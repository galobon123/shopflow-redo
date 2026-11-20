package com.Proyect.Ecommerce.security;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * HU-33 · Browsers where the person already proved they own the email.
 * The browser keeps a random token (localStorage, it survives logout); Redis keeps its hash → userId
 * for 90 days, renewed each time it's used. Signing in with the password from a browser without one
 * asks for a code sent by email.
 */
@Service
public class TrustedDeviceService {

    static final Duration TTL = Duration.ofDays(90);

    private final StringRedisTemplate redis;

    public TrustedDeviceService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    static String key(String deviceToken) {
        return "device:" + SecureTokens.sha256(deviceToken);
    }

    /** Creates a new device token for this user; the caller hands it to the browser. */
    public String trust(Long userId) {
        String token = SecureTokens.newToken();
        redis.opsForValue().set(key(token), userId.toString(), TTL);
        return token;
    }

    /** True when the token belongs to this user; also renews it for another 90 days. */
    public boolean isTrusted(String deviceToken, Long userId) {
        if (deviceToken == null || deviceToken.isBlank()) {
            return false;
        }
        String key = key(deviceToken);
        if (!userId.toString().equals(redis.opsForValue().get(key))) {
            return false;
        }
        redis.expire(key, TTL);
        return true;
    }
}
