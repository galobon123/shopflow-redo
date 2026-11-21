package com.Proyect.Ecommerce.security;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.Proyect.Ecommerce.exception.BadRequestException;
import com.Proyect.Ecommerce.exception.TooManyRequestsException;

/**
 * HU-31 · Single-use links to confirm the email, kept in Redis as {@code verify:email:{hash}} → userId
 * for 24 hours. Asking for another link is limited to once a minute per account.
 */
@Service
public class EmailVerificationService {

    static final Duration TTL = Duration.ofHours(24);
    static final Duration RESEND_AFTER = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;

    public EmailVerificationService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    static String key(String token) {
        return "verify:email:" + SecureTokens.sha256(token);
    }

    static String cooldownKey(Long userId) {
        return "verify:email:sent:" + userId;
    }

    /** The token for the first link, sent on registration. Starts the minute before another can be asked. */
    public String newLink(Long userId) {
        redis.opsForValue().set(cooldownKey(userId), "1", RESEND_AFTER);
        return storeToken(userId);
    }

    /** Another link, asked from the banner. Throws 429 if this account got one less than a minute ago. */
    public String anotherLink(Long userId) {
        String cooldown = cooldownKey(userId);
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(cooldown, "1", RESEND_AFTER))) {
            Long ttl = redis.getExpire(cooldown, TimeUnit.SECONDS);
            long seconds = ttl != null && ttl > 0 ? ttl : RESEND_AFTER.toSeconds();
            throw new TooManyRequestsException("Esperá " + seconds + " segundos para pedir otro email.", seconds);
        }
        return storeToken(userId);
    }

    private String storeToken(Long userId) {
        String token = SecureTokens.newToken();
        redis.opsForValue().set(key(token), userId.toString(), TTL);
        return token;
    }

    /** The user the link was for. Each link works once (GETDEL). */
    public Long confirm(String token) {
        String userId = token == null ? null : redis.opsForValue().getAndDelete(key(token));
        if (userId == null) {
            throw new BadRequestException("El enlace venció o ya se usó. Pedí uno nuevo desde tu cuenta.");
        }
        return Long.valueOf(userId);
    }
}
