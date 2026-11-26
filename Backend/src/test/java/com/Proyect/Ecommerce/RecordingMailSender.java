package com.Proyect.Ecommerce;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;

/** Integration tests' inbox: keeps what the app sends instead of talking to an SMTP server. */
public class RecordingMailSender extends JavaMailSenderImpl {

    /** HU-20 · An email as received; the HTML ones (MailQueue) also keep their HTML part. */
    public static class Received extends SimpleMailMessage {
        private String html;

        public String getHtml() {
            return html;
        }
    }

    private final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();
    private final AtomicInteger refused = new AtomicInteger();
    private volatile boolean down;
    private volatile Duration delay = Duration.ZERO;

    @Override
    public void send(SimpleMailMessage... messages) {
        arrive();
        sent.addAll(List.of(messages));
    }

    @Override
    public void send(MimeMessage... messages) {
        arrive();
        for (MimeMessage message : messages) {
            sent.add(received(message));
        }
    }

    private void arrive() {
        if (down) {
            refused.incrementAndGet();
            throw new MailSendException("SMTP down (test)");
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Written out and read back, as an SMTP server would get it. */
    private Received received(MimeMessage message) {
        try {
            message.saveChanges();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            message.writeTo(bytes);
            MimeMessage copy = new MimeMessage(getSession(), new ByteArrayInputStream(bytes.toByteArray()));
            Received received = new Received();
            received.setFrom(copy.getFrom()[0].toString());
            received.setTo(Arrays.stream(copy.getAllRecipients()).map(Address::toString).toArray(String[]::new));
            received.setSubject(copy.getSubject());
            read(copy, received);
            return received;
        } catch (MessagingException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void read(Part part, Received received) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            received.setText((String) part.getContent());
        } else if (part.isMimeType("text/html")) {
            received.html = (String) part.getContent();
        } else if (part.isMimeType("multipart/*")) {
            Multipart parts = (Multipart) part.getContent();
            for (int i = 0; i < parts.getCount(); i++) {
                read(parts.getBodyPart(i), received);
            }
        }
    }

    public void clear() {
        sent.clear();
        refused.set(0);
        down = false;
        delay = Duration.ZERO;
    }

    /** Makes every send take this long, like a slow SMTP server. */
    public void slowDown(Duration delay) {
        this.delay = delay;
    }

    /** Makes every send fail, like an SMTP server that doesn't answer. */
    public void goDown() {
        down = true;
    }

    /** HU-20 · The SMTP server answers again. */
    public void comeBack() {
        down = false;
    }

    /** HU-20 · Sends that failed because the server was down. */
    public int refused() {
        return refused.get();
    }

    public List<SimpleMailMessage> to(String address) {
        return sent.stream().filter(m -> m.getTo() != null && List.of(m.getTo()).contains(address)).toList();
    }

    /** HU-20 · The emails to {@code address} with this subject. */
    public List<Received> to(String address, String subject) {
        return to(address).stream()
                .filter(m -> m instanceof Received && subject.equals(m.getSubject()))
                .map(Received.class::cast)
                .toList();
    }

    /** Waits up to 10 seconds for one: some emails are sent off the request thread (HU-09). */
    public SimpleMailMessage lastTo(String address) {
        long deadline = System.currentTimeMillis() + 10_000;
        List<SimpleMailMessage> messages = to(address);
        while (messages.isEmpty() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            messages = to(address);
        }
        if (messages.isEmpty()) {
            throw new AssertionError("No email to " + address);
        }
        return messages.getLast();
    }

    /** The first match of {@code regex}'s group 1 in the last email to {@code address}. */
    public String find(String address, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(lastTo(address).getText());
        if (!matcher.find()) {
            throw new AssertionError("Not found " + regex + " in: " + lastTo(address).getText());
        }
        return matcher.group(1);
    }
}
