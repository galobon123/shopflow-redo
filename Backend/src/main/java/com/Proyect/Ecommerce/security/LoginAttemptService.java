package com.Proyect.Ecommerce.security;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.Proyect.Ecommerce.exception.TooManyRequestsException;

/**
 * HU-07 · Brute-force guard for the login, kept in Redis, per IP and email.
 * Every 5 failures the login waits 1 minute; the 15th failure locks it for 15 minutes and the owner gets
 * an email asking if it was them. While locked the login answers 429, even with the right password, and
 * those tries don't count. Failures are forgotten an hour after the last one, or on a successful login.
 */
@Service
public class LoginAttemptService {

    static final int FAILURES_PER_PAUSE = 5;
    static final int FAILURES_PER_LOCK = 15;
    static final Duration PAUSE = Duration.ofMinutes(1);
    static final Duration LOCK = Duration.ofMinutes(15);
    static final Duration MEMORY = Duration.ofHours(1);

    private final StringRedisTemplate redis;

    public LoginAttemptService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    static String key(String ip, String email) {
        return "login:fail:" + ip + ":" + email;
    }

    static String lockKey(String ip, String email) {
        return "login:lock:" + ip + ":" + email;
    }

    /** Throws 429 while this IP has to wait for this email. */
    public void checkAllowed(String ip, String email) {
        Long ttl = redis.getExpire(lockKey(ip, email), TimeUnit.SECONDS);
        if (ttl == null || ttl <= 0) {
            return;
        }
        long minutes = (ttl + 59) / 60;
        throw new TooManyRequestsException(
                "Demasiados intentos. Probá de nuevo en " + minutes + (minutes == 1 ? " minuto." : " minutos."),
                ttl);
    }

    /** @return true when this failure locked the login for {@link #LOCK}: time to warn the owner. */
    public boolean recordFailure(String ip, String email) {
        String key = key(ip, email);
        Long failures = redis.opsForValue().increment(key);
        redis.expire(key, MEMORY);
        if (failures == null || failures % FAILURES_PER_PAUSE != 0) {
            return false;
        }
        if (failures < FAILURES_PER_LOCK) {
            redis.opsForValue().set(lockKey(ip, email), "1", PAUSE);
            return false;
        }
        // After the long lock the count starts over: 5 more failures pause again.
        redis.opsForValue().set(lockKey(ip, email), "1", LOCK);
        redis.delete(key);
        return true;
    }

    public void reset(String ip, String email) {
        redis.delete(key(ip, email));
    }
}
