package com.Proyect.Ecommerce.security;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.Proyect.Ecommerce.exception.BadRequestException;

/**
 * HU-09 · Single-use links to choose a new password, kept in Redis as {@code pwreset:{hash}} → userId
 * for 30 minutes. One link a minute per account; asking sooner sends nothing and says nothing, so the
 * answer never tells whether the email has an account.
 */
@Service
public class PasswordResetService {

    public static final Duration TTL = Duration.ofMinutes(30);
    static final Duration RESEND_AFTER = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;

    public PasswordResetService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    static String key(String token) {
        return "pwreset:" + SecureTokens.sha256(token);
    }

    static String cooldownKey(Long userId) {
        return "pwreset:sent:" + userId;
    }

    /** The token for a new link, or empty when this account got one less than a minute ago. */
    public Optional<String> newLink(Long userId) {
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(cooldownKey(userId), "1", RESEND_AFTER))) {
            return Optional.empty();
        }
        String token = SecureTokens.newToken();
        redis.opsForValue().set(key(token), userId.toString(), TTL);
        return Optional.of(token);
    }

    /** The user the link was for. Each link works once (GETDEL). */
    public Long confirm(String token) {
        String userId = token == null ? null : redis.opsForValue().getAndDelete(key(token));
        if (userId == null) {
            throw new BadRequestException("El enlace venció, pedí uno nuevo");
        }
        return Long.valueOf(userId);
    }
}
