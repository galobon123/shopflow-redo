package com.Proyect.Ecommerce.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.Proyect.Ecommerce.exception.ServiceUnavailableException;
import com.Proyect.Ecommerce.security.PasswordResetService;

/**
 * Account emails (HU-07, HU-09, HU-31, HU-33, HU-20). In development they land in Mailpit (http://localhost:8025)
 * instead of a real inbox.
 * <p>
 * HU-20 · The welcome and the password reset go through {@link MailQueue}, with HTML and retries. The verification
 * link, the sign-in code and the login alert stay plain text and are sent once, right away: the code must fail the
 * sign-in when it can't be sent and is dead after 10 minutes anyway, and the person asks for another link or code
 * from the screen they are on. That also keeps the verification token and the code out of Redis, which only
 * keeps their hashes.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    /** "el sábado 21 de noviembre a las 17:51": the shop sells in Argentina. */
    private static final DateTimeFormatter WHEN = DateTimeFormatter
            .ofPattern("'el' EEEE d 'de' MMMM 'a las' HH:mm", Locale.forLanguageTag("es-AR"))
            .withZone(ZoneId.of("America/Argentina/Buenos_Aires"));

    private final JavaMailSender mailSender;
    private final MailQueue queue;
    private final MailTemplates templates;
    private final String from;
    private final String publicUrl;

    public MailService(JavaMailSender mailSender,
                       MailQueue queue,
                       MailTemplates templates,
                       @Value("${app.mail.from}") String from,
                       @Value("${app.public-url}") String publicUrl) {
        this.mailSender = mailSender;
        this.queue = queue;
        this.templates = templates;
        this.from = from;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    /**
     * HU-20 · When the email is confirmed (link, sign-in code, password reset or Google), not on registering: the
     * address may still be a typo or someone else's. Queued, it can wait; never fails the caller.
     */
    public void sendWelcome(String to, String name) {
        queue.enqueue(templates.welcome(to, name));
    }

    /**
     * Never fails the caller: the account already exists and the person can ask for the link again
     * from the banner.
     */
    public void sendVerificationLink(String to, String name, String token) {
        String link = publicUrl + "/verify-email?token=" + token;
        try {
            send(to, "Confirmá tu email en ShopFlow", """
                    Hola %s,

                    Para confirmar tu email y poder comprar, abrí este enlace:
                    %s

                    Vence en 24 horas. Si no creaste una cuenta en ShopFlow, ignorá este mensaje.
                    """.formatted(name, link));
        } catch (MailException e) {
            log.warn("Verification email to {} not sent: {}", to, e.getMessage());
        }
    }

    /**
     * HU-09 · Sent off the request thread and never fails the caller: waiting for the SMTP server, or
     * a 503, would tell that the email has an account. The person can ask again after a minute.
     * HU-20 · The first send is right away; if it fails, the queue retries it while the link still works. Only then
     * the token waits in Redis in the clear (inside the email), and never past its 30 minutes.
     */
    @Async
    public void sendPasswordResetLink(String to, String name, String token) {
        queue.sendOrQueue(templates.passwordReset(to, name, token, Instant.now().plus(PasswordResetService.TTL)));
    }

    /** Fails with 503: without the email the person can't finish signing in, so they must know. */
    public void sendSignInCode(String to, String name, String code) {
        try {
            send(to, code + " es tu código para entrar a ShopFlow", """
                    Hola %s,

                    Alguien (seguramente vos) quiere entrar a tu cuenta desde un dispositivo nuevo.
                    Tu código es: %s

                    Vence en 10 minutos. Si no fuiste vos, cambiá tu contraseña: quien lo intentó ya la conoce.
                    """.formatted(name, code));
        } catch (MailException e) {
            log.error("Sign-in code email to {} not sent", to, e);
            throw new ServiceUnavailableException("No pudimos enviarte el código. Probá de nuevo en unos minutos.");
        }
    }

    /**
     * HU-07 · After the login locks for 15 minutes. Off the request thread and never fails the caller:
     * the answer must look the same whether the email has an account or not.
     */
    @Async
    public void sendLoginLockedAlert(String to, String name, Instant when, String device) {
        try {
            send(to, "¿Fuiste vos? Bloqueamos el ingreso a tu cuenta de ShopFlow", """
                    Hola %s,

                    Alguien escribió mal la contraseña de tu cuenta muchas veces seguidas,
                    %s (hora de Argentina), desde %s.

                    ¿Fuiste vos?

                    Si fuiste vos, no tenés que hacer nada: en 15 minutos vas a poder volver a intentarlo.
                    Si te olvidaste la contraseña, podés elegir una nueva acá:
                    %s

                    Si no fuiste vos, tu cuenta sigue protegida: por las dudas, cambiá tu contraseña
                    con el mismo enlace.
                    """.formatted(name, WHEN.format(when), device, publicUrl + "/forgot-password"));
        } catch (MailException e) {
            log.warn("Login locked alert to {} not sent: {}", to, e.getMessage());
        }
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
