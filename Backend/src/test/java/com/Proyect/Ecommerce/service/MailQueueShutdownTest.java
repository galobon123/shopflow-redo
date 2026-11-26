package com.Proyect.Ecommerce.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.Proyect.Ecommerce.IntegrationTest;
import com.Proyect.Ecommerce.RecordingMailSender;
import com.Proyect.Ecommerce.service.MailQueue.Mail;

import jakarta.mail.internet.MimeMessage;
import tools.jackson.databind.json.JsonMapper;

/**
 * HU-20 · The worker doesn't lose emails when it stops: one being sent on shutdown goes back to the queue, and
 * one taken by a worker that died is taken back later. Against real Redis.
 */
@IntegrationTest
class MailQueueShutdownTest {

    private static final Mail HELLO = new Mail("ana@mail.com", "Hola", "Hola Ana", "<p>Hola Ana</p>", null);

    @Autowired private StringRedisTemplate redis;
    @Autowired private JsonMapper json;
    @Autowired private RecordingMailSender inbox;

    /** An SMTP server that never answers, until the sending thread is interrupted. */
    private final JavaMailSender hungSmtp = mock(JavaMailSender.class);
    private final CountDownLatch sending = new CountDownLatch(1);
    private Thread worker;

    @BeforeEach
    void emptyQueue() {
        redis.delete(List.of(MailQueue.QUEUE, MailQueue.SENDING, MailQueue.FAILED));
        inbox.clear();
        when(hungSmtp.createMimeMessage()).thenAnswer(call -> new JavaMailSenderImpl().createMimeMessage());
        doAnswer(call -> {
            sending.countDown();
            new CountDownLatch(1).await();
            return null;
        }).when(hungSmtp).send(any(MimeMessage.class));
    }

    @AfterEach
    void stopWorker() {
        if (worker != null) worker.interrupt();
        inbox.clear();
    }

    /** A worker of its own, stuck sending HELLO. */
    private MailQueue stuckSending() throws InterruptedException {
        MailQueue queue = new MailQueue(redis, hungSmtp, json, "ShopFlow <no-reply@shopflow.local>", Duration.ofMillis(200));
        queue.enqueue(HELLO);
        worker = new Thread(() -> queue.deliverDue(Instant.now()));
        worker.start();
        assertTrue(sending.await(10, TimeUnit.SECONDS));
        return queue;
    }

    private MailQueue healthy() {
        return new MailQueue(redis, inbox, json, "ShopFlow <no-reply@shopflow.local>", Duration.ofSeconds(1));
    }

    @Test
    void onShutdownTheEmailBeingSentGoesBackToTheQueue() throws Exception {
        MailQueue stopping = stuckSending();

        stopping.stop();

        assertEquals(1, redis.opsForZSet().size(MailQueue.QUEUE));
        assertEquals(0, redis.opsForZSet().size(MailQueue.SENDING));
        healthy().deliverDue(Instant.now());
        assertEquals(1, inbox.to("ana@mail.com", "Hola").size());
    }

    @Test
    void anEmailTakenByAWorkerThatDiedIsSentAfterTheLease() throws Exception {
        stuckSending(); // and never stopped, as if the process was killed

        healthy().deliverDue(Instant.now());
        assertTrue(inbox.to("ana@mail.com").isEmpty(), "still someone else's");

        healthy().deliverDue(Instant.now().plus(MailQueue.LEASE).plusSeconds(1));
        assertEquals(1, inbox.to("ana@mail.com", "Hola").size());
    }

    /** Whatever was done before queuing (an order paid, an account verified) stays done. */
    @Test
    void withRedisDownQueuingDoesNotFail() {
        StringRedisTemplate redisDown = mock(StringRedisTemplate.class);
        when(redisDown.opsForZSet()).thenThrow(new RedisConnectionFailureException("Redis down (test)"));
        MailQueue queue = new MailQueue(redisDown, inbox, json, "ShopFlow <no-reply@shopflow.local>", Duration.ofSeconds(1));

        assertDoesNotThrow(() -> queue.enqueue(HELLO));
        inbox.goDown();
        assertDoesNotThrow(() -> queue.sendOrQueue(HELLO));
    }
}
