package com.Proyect.Ecommerce;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Integration tests' inbox: keeps what the app sends instead of talking to an SMTP server. */
public class RecordingMailSender extends JavaMailSenderImpl {

    private final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();
    private volatile boolean down;
    private volatile Duration delay = Duration.ZERO;

    @Override
    public void send(SimpleMailMessage... messages) {
        if (down) {
            throw new MailSendException("SMTP down (test)");
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        sent.addAll(List.of(messages));
    }

    public void clear() {
        sent.clear();
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

    public List<SimpleMailMessage> to(String address) {
        return sent.stream().filter(m -> m.getTo() != null && List.of(m.getTo()).contains(address)).toList();
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
