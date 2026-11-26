package com.Proyect.Ecommerce.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.Proyect.Ecommerce.exception.TooManyRequestsException;

/** HU-07 · Edge cases of the counter that are awkward to reach with a real Redis. */
class LoginAttemptServiceTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final LoginAttemptService attempts = new LoginAttemptService(redis);
    private final String key = LoginAttemptService.key("1.2.3.4", "ana@mail.com");
    private final String lock = LoginAttemptService.lockKey("1.2.3.4", "ana@mail.com");

    LoginAttemptServiceTest() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void withoutALockAnotherTryIsAllowed() {
        when(redis.getExpire(lock, TimeUnit.SECONDS)).thenReturn(-2L);
        assertDoesNotThrow(() -> attempts.checkAllowed("1.2.3.4", "ana@mail.com"));
    }

    @Test
    void roundsTheWaitUpToWholeMinutes() {
        when(redis.getExpire(lock, TimeUnit.SECONDS)).thenReturn(61L);

        TooManyRequestsException error = assertThrows(TooManyRequestsException.class,
                () -> attempts.checkAllowed("1.2.3.4", "ana@mail.com"));

        assertEquals("Demasiados intentos. Probá de nuevo en 2 minutos.", error.getMessage());
        assertEquals(61, error.getRetryAfterSeconds());
    }

    @Test
    void failuresBetweenPausesOnlyCount() {
        when(values.increment(key)).thenReturn(3L);

        assertFalse(attempts.recordFailure("1.2.3.4", "ana@mail.com"));

        verify(redis).expire(key, LoginAttemptService.MEMORY);
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void theTenthFailurePausesForAMinute() {
        when(values.increment(key)).thenReturn(10L);

        assertFalse(attempts.recordFailure("1.2.3.4", "ana@mail.com"));

        verify(values).set(lock, "1", LoginAttemptService.PAUSE);
    }

    @Test
    void theFifteenthFailureLocksForFifteenMinutesAndStartsOver() {
        when(values.increment(key)).thenReturn(15L);

        assertTrue(attempts.recordFailure("1.2.3.4", "ana@mail.com"));

        verify(values).set(lock, "1", LoginAttemptService.LOCK);
        verify(redis).delete(key);
    }
}
