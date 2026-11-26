package com.Proyect.Ecommerce.security;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.Proyect.Ecommerce.exception.BadRequestException;
import com.Proyect.Ecommerce.exception.TooManyRequestsException;

/**
 * HU-33 · The step between "right password from a new device" and the session: a 6-digit code by email.
 *
 * Redis keeps {@code login:challenge:{id}} → userId, code, attempts, sentAt for 10 minutes.
 * The id goes to the browser; the code only to the inbox. Five wrong codes burn the challenge,
 * so guessing 1 in a million five times is the best an attacker with the password gets.
 * Emails per account are capped too, so a leaked password can't be used to flood the inbox.
 */
@Service
public class LoginChallengeService {

    static final Duration TTL = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 5;
    static final Duration RESEND_AFTER = Duration.ofSeconds(60);
    static final int MAX_SENDS = 5;
    static final Duration SENDS_WINDOW = Duration.ofMinutes(15);

    public record Challenge(String id, Long userId, String code) {
    }

    private final StringRedisTemplate redis;

    public LoginChallengeService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    static String key(String challengeId) {
        return "login:challenge:" + challengeId;
    }

    static String sendsKey(Long userId) {
        return "login:code:sends:" + userId;
    }

    public Challenge start(Long userId) {
        countSend(userId);
        Challenge challenge = new Challenge(UUID.randomUUID().toString(), userId, SecureTokens.newCode());
        redis.opsForHash().putAll(key(challenge.id()), Map.of(
                "userId", userId.toString(),
                "code", challenge.code(),
                "attempts", "0",
                "sentAt", String.valueOf(System.currentTimeMillis())));
        redis.expire(key(challenge.id()), TTL);
        return challenge;
    }

    /** A new code for the same challenge, at most once a minute; the old code stops working. */
    public Challenge resend(String challengeId) {
        String key = key(challengeId);
        Map<Object, Object> data = redis.opsForHash().entries(key);
        if (data.isEmpty()) {
            throw expired();
        }
        long waited = System.currentTimeMillis() - Long.parseLong((String) data.get("sentAt"));
        if (waited < RESEND_AFTER.toMillis()) {
            long seconds = (RESEND_AFTER.toMillis() - waited + 999) / 1000;
            throw new TooManyRequestsException("Esperá " + seconds + " segundos para pedir otro código.", seconds);
        }
        Long userId = Long.valueOf((String) data.get("userId"));
        countSend(userId);
        String code = SecureTokens.newCode();
        redis.opsForHash().putAll(key, Map.of(
                "code", code,
                "attempts", "0",
                "sentAt", String.valueOf(System.currentTimeMillis())));
        redis.expire(key, TTL);
        return new Challenge(challengeId, userId, code);
    }

    /** Returns the user the code was for. The challenge works once: a right code deletes it. */
    public Long confirm(String challengeId, String code) {
        String key = key(challengeId);
        String expected = (String) redis.opsForHash().get(key, "code");
        if (expected == null) {
            throw expired();
        }
        // Count first, atomically, so parallel guesses can't get more than MAX_ATTEMPTS tries.
        long attempts = redis.opsForHash().increment(key, "attempts", 1);
        if (attempts > MAX_ATTEMPTS) {
            redis.delete(key);
            throw new BadRequestException("Demasiados códigos equivocados. Iniciá sesión de nuevo.");
        }
        if (!SecureTokens.sameSecret(expected, code)) {
            int left = MAX_ATTEMPTS - (int) attempts;
            throw new BadRequestException(left == 0
                    ? "Demasiados códigos equivocados. Iniciá sesión de nuevo."
                    : "El código no es correcto. Te quedan " + left + (left == 1 ? " intento." : " intentos."));
        }
        String userId = (String) redis.opsForHash().get(key, "userId");
        // Only whoever deletes the key gets in: two requests with the right code can't both succeed.
        if (userId == null || !Boolean.TRUE.equals(redis.delete(key))) {
            throw expired();
        }
        return Long.valueOf(userId);
    }

    private void countSend(Long userId) {
        String key = sendsKey(userId);
        Long sends = redis.opsForValue().increment(key);
        if (sends != null && (sends == 1 || redis.getExpire(key) == -1)) {
            redis.expire(key, SENDS_WINDOW);
        }
        if (sends != null && sends > MAX_SENDS) {
            Long ttl = redis.getExpire(key);
            long retryAfter = ttl != null && ttl > 0 ? ttl : SENDS_WINDOW.toSeconds();
            long minutes = (retryAfter + 59) / 60;
            throw new TooManyRequestsException("Ya te mandamos varios códigos. Probá de nuevo en "
                    + minutes + (minutes == 1 ? " minuto." : " minutos."), retryAfter);
        }
    }

    private static BadRequestException expired() {
        return new BadRequestException("El código venció. Iniciá sesión de nuevo.");
    }
}
