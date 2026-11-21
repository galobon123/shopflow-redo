package com.Proyect.Ecommerce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.Proyect.Ecommerce.exception.ServiceUnavailableException;

/**
 * Account emails (HU-09, HU-31, HU-33). Plain text for now; HU-20 adds templates and a retry queue.
 * In development they land in Mailpit (http://localhost:8025) instead of a real inbox.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String publicUrl;

    public MailService(JavaMailSender mailSender,
                       @Value("${app.mail.from}") String from,
                       @Value("${app.public-url}") String publicUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
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
     */
    @Async
    public void sendPasswordResetLink(String to, String name, String token) {
        String link = publicUrl + "/reset-password?token=" + token;
        try {
            send(to, "Restablecé tu contraseña de ShopFlow", """
                    Hola %s,

                    Para elegir una contraseña nueva, abrí este enlace:
                    %s

                    Vence en 30 minutos y sirve una sola vez. Si no lo pediste, ignorá este mensaje:
                    tu contraseña no cambia.
                    """.formatted(name, link));
        } catch (MailException e) {
            log.warn("Password reset email to {} not sent: {}", to, e.getMessage());
        }
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

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
