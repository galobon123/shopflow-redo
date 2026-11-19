package com.Proyect.Ecommerce.security;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.Proyect.Ecommerce.exception.TooManyRequestsException;

/**
 * HU-07 · Brute-force guard for the login, kept in Redis.
 * Failed logins per IP and email are counted for 15 minutes; from the 5th one the login answers 429,
 * even with the right password, until the window ends. A successful login clears the count.
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    public LoginAttemptService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    static String key(String ip, String email) {
        return "login:fail:" + ip + ":" + email;
    }

    /** Throws 429 when this IP already failed {@link #MAX_FAILURES} times for this email. */
    public void checkAllowed(String ip, String email) {
        String count = redis.opsForValue().get(key(ip, email));
        if (count == null || Integer.parseInt(count) < MAX_FAILURES) {
            return;
        }
        Long ttl = redis.getExpire(key(ip, email), TimeUnit.SECONDS);
        long retryAfter = ttl != null && ttl > 0 ? ttl : WINDOW.toSeconds();
        long minutes = (retryAfter + 59) / 60;
        throw new TooManyRequestsException(
                "Demasiados intentos. Probá de nuevo en " + minutes + (minutes == 1 ? " minuto." : " minutos."),
                retryAfter);
    }

    public void recordFailure(String ip, String email) {
        String key = key(ip, email);
        Long failures = redis.opsForValue().increment(key);
        // The window starts with the first failure; later ones don't extend it.
        // The TTL check also repairs a key left without expiry if the process died between the two calls.
        if (failures != null && (failures == 1 || redis.getExpire(key) == -1)) {
            redis.expire(key, WINDOW);
        }
    }

    public void reset(String ip, String email) {
        redis.delete(key(ip, email));
    }
}
