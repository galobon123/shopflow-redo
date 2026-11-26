package com.Proyect.Ecommerce.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * HU-20 · Emails that must arrive even with the SMTP server down for a while: welcome, password reset and the
 * order ones ({@link MailTemplates}). Each waits in the Redis sorted set {@value #QUEUE}, scored by the time it may
 * be sent, and a job sends the due ones every few seconds. A failed send is retried after 1, 5 and 15 minutes;
 * after the third retry it goes to the list {@value #FAILED} with an ERROR log.
 * <p>
 * The queue keeps the email already rendered, not its type and data: it says what was true when it happened (the
 * price paid, the product's name at that moment) however long it waits, and a send never fails for want of data.
 * <p>
 * An email being sent sits in {@value #SENDING}, scored by when it was taken. Taking it is one Lua script, so two
 * workers (or two instances) never take the same one, and it leaves only once sent or rescheduled: on shutdown
 * the one being sent goes back to the queue, and after a crash a later run takes it back once {@link #LEASE} passed.
 */
@Component
public class MailQueue {

    /** What an email says, rendered. {@code expiresAt}: it carries a link that stops working then (else null). */
    public record Mail(String to, String subject, String text, String html, Instant expiresAt) {
    }

    /** What the queue holds. {@code failures}: sends that failed so far. The id keeps two equal emails apart. */
    record Job(String id, int failures, Mail mail) {
    }

    private static final Logger log = LoggerFactory.getLogger(MailQueue.class);

    public static final String QUEUE = "mail:queue";
    public static final String SENDING = "mail:sending";
    public static final String FAILED = "mail:failed";
    /** Wait before each retry: 3 retries after the first send, so 4 sends at most. */
    static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));
    /** An email taken longer ago than this belongs to a worker that died: SMTP timeouts are seconds. */
    static final Duration LEASE = Duration.ofMinutes(10);

    /** The first due email moves to SENDING; nil when none is due. */
    private static final RedisScript<String> TAKE = RedisScript.of("""
            local due = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, 1)
            if #due == 0 then return false end
            redis.call('ZREM', KEYS[1], due[1])
            redis.call('ZADD', KEYS[2], ARGV[1], due[1])
            return due[1]
            """, String.class);
    /** Back to the queue, as ARGV[2] at time ARGV[3], only if still in SENDING (else someone already moved it). */
    private static final RedisScript<Long> RESCHEDULE = RedisScript.of("""
            if redis.call('ZREM', KEYS[1], ARGV[1]) == 0 then return 0 end
            redis.call('ZADD', KEYS[2], ARGV[3], ARGV[2])
            return 1
            """, Long.class);
    /** To the failed list, as ARGV[2], only if still in SENDING. */
    private static final RedisScript<Long> GIVE_UP = RedisScript.of("""
            if redis.call('ZREM', KEYS[1], ARGV[1]) == 0 then return 0 end
            redis.call('RPUSH', KEYS[2], ARGV[2])
            return 1
            """, Long.class);
    /** Emails taken before ARGV[1] go back to the queue, due at ARGV[2]. */
    private static final RedisScript<Long> TAKE_BACK = RedisScript.of("""
            local stale = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            for _, job in ipairs(stale) do
                redis.call('ZREM', KEYS[1], job)
                redis.call('ZADD', KEYS[2], ARGV[2], job)
            end
            return #stale
            """, Long.class);

    private final StringRedisTemplate redis;
    private final JavaMailSender mailSender;
    private final JsonMapper json;
    private final String from;
    /** How long shutdown waits for the email being sent before putting it back in the queue. */
    private final Duration shutdownWait;
    /** Held by a run: one at a time, whether the scheduler, a test or shutdown asks. */
    private final ReentrantLock running = new ReentrantLock();
    private volatile boolean stopping;
    /** The entry this worker took and hasn't finished. */
    private volatile String inFlight;

    @Autowired
    public MailQueue(StringRedisTemplate redis, JavaMailSender mailSender, JsonMapper json,
                     @Value("${app.mail.from}") String from) {
        this(redis, mailSender, json, from, Duration.ofSeconds(20));
    }

    MailQueue(StringRedisTemplate redis, JavaMailSender mailSender, JsonMapper json, String from, Duration shutdownWait) {
        this.redis = redis;
        this.mailSender = mailSender;
        this.json = json;
        this.from = from;
        this.shutdownWait = shutdownWait;
    }

    /**
     * Sent by the next run of the worker. Never throws: if Redis fails the email is lost with a WARN, and whatever
     * the caller was doing (an order already paid, an account just verified) stays done.
     */
    public void enqueue(Mail mail) {
        push(new Job(UUID.randomUUID().toString(), 0, mail), Instant.now());
    }

    /**
     * For emails the person is waiting for: the first send is right now, on the caller's thread; only if it fails
     * the email goes to the queue, for its first retry. Never throws.
     */
    public void sendOrQueue(Mail mail) {
        try {
            send(mail);
        } catch (MailException e) {
            log.warn("Email to {} not sent, queued for retry: {}", mail.to(), e.getMessage());
            push(new Job(UUID.randomUUID().toString(), 1, mail), Instant.now().plus(BACKOFF.getFirst()));
        }
    }

    private void push(Job job, Instant notBefore) {
        try {
            redis.opsForZSet().add(QUEUE, json.writeValueAsString(job), notBefore.toEpochMilli());
        } catch (RuntimeException e) {
            log.warn("Email \"{}\" to {} could not be queued, it won't be sent: {}",
                    job.mail().subject(), job.mail().to(), e.getMessage());
        }
    }

    /** The worker: every few seconds ({@code app.mail.worker-cron}). */
    @Scheduled(cron = "${app.mail.worker-cron}")
    public void deliverDue() {
        deliverDue(Instant.now());
    }

    /**
     * Sends what is due at {@code now}, one email at a time, until none is left or a send fails (with the SMTP down,
     * trying the rest now would only wait for each timeout: they keep their own times). Tests pass a later time to
     * skip the waits.
     */
    public void deliverDue(Instant now) {
        if (!running.tryLock()) return;
        try {
            redis.execute(TAKE_BACK, List.of(SENDING, QUEUE), millis(now.minus(LEASE)), millis(now));
            while (!stopping) {
                String entry = redis.execute(TAKE, List.of(QUEUE, SENDING), millis(now));
                if (entry == null) return;
                inFlight = entry;
                boolean sent = deliver(entry, now);
                inFlight = null;
                if (!sent) return;
            }
        } finally {
            running.unlock();
        }
    }

    /** False when the send failed. */
    private boolean deliver(String entry, Instant now) {
        Job job;
        try {
            job = json.readValue(entry, Job.class);
        } catch (JacksonException e) {
            log.error("Unreadable queued email moved to {}", FAILED, e);
            redis.execute(GIVE_UP, List.of(SENDING, FAILED), entry, entry);
            return true;
        }
        Mail mail = job.mail();
        if (mail.expiresAt() != null && now.isAfter(mail.expiresAt())) {
            log.warn("Email \"{}\" to {} dropped: its link already expired", mail.subject(), mail.to());
            redis.opsForZSet().remove(SENDING, entry);
            return true;
        }
        try {
            send(mail);
            redis.opsForZSet().remove(SENDING, entry);
            return true;
        } catch (MailException e) {
            int failures = job.failures() + 1;
            if (failures > BACKOFF.size()) {
                log.error("Email \"{}\" to {} not sent after {} attempts, moved to {}",
                        mail.subject(), mail.to(), failures, FAILED, e);
                redis.execute(GIVE_UP, List.of(SENDING, FAILED), entry, json.writeValueAsString(failed(job, failures)));
            } else {
                Duration wait = BACKOFF.get(failures - 1);
                log.warn("Email \"{}\" to {} not sent, retry in {} min: {}",
                        mail.subject(), mail.to(), wait.toMinutes(), e.getMessage());
                redis.execute(RESCHEDULE, List.of(SENDING, QUEUE), entry,
                        json.writeValueAsString(new Job(job.id(), failures, mail)), millis(now.plus(wait)));
            }
            return false;
        }
    }

    /** What the failed list keeps. Without the body when it carries a link: it would still work for a while. */
    private static Job failed(Job job, int failures) {
        Mail mail = job.mail();
        return new Job(job.id(), failures, mail.expiresAt() == null ? mail
                : new Mail(mail.to(), mail.subject(), null, null, mail.expiresAt()));
    }

    /**
     * On shutdown no run starts again, and the email being sent gets {@link #shutdownWait} to finish. If it doesn't,
     * it goes back to the queue (it may arrive twice if the SMTP takes it after all; never zero times).
     */
    @PreDestroy
    void stop() throws InterruptedException {
        stopping = true;
        if (running.tryLock(shutdownWait.toMillis(), TimeUnit.MILLISECONDS)) {
            running.unlock();
            return;
        }
        String entry = inFlight;
        if (entry != null) {
            redis.execute(RESCHEDULE, List.of(SENDING, QUEUE), entry, entry, millis(Instant.now()));
            log.warn("Shutting down while an email was being sent: it went back to {}", QUEUE);
        }
    }

    private void send(Mail mail) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(mail.to());
            helper.setSubject(mail.subject());
            helper.setText(mail.text(), mail.html());
        } catch (MessagingException e) {
            throw new MailPreparationException(e);
        }
        mailSender.send(message);
    }

    private static String millis(Instant time) {
        return String.valueOf(time.toEpochMilli());
    }
}
