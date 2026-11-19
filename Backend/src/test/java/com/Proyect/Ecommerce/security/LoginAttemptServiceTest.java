package com.Proyect.Ecommerce.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    LoginAttemptServiceTest() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void fourFailuresStillAllowAnotherTry() {
        when(values.get(key)).thenReturn("4");
        assertDoesNotThrow(() -> attempts.checkAllowed("1.2.3.4", "ana@mail.com"));
    }

    @Test
    void roundsTheWaitUpToWholeMinutes() {
        when(values.get(key)).thenReturn("5");
        when(redis.getExpire(key, TimeUnit.SECONDS)).thenReturn(61L);

        TooManyRequestsException error = assertThrows(TooManyRequestsException.class,
                () -> attempts.checkAllowed("1.2.3.4", "ana@mail.com"));

        assertEquals("Demasiados intentos. Probá de nuevo en 2 minutos.", error.getMessage());
        assertEquals(61, error.getRetryAfterSeconds());
    }

    @Test
    void laterFailuresDoNotExtendTheWindow() {
        when(values.increment(key)).thenReturn(3L);
        when(redis.getExpire(key)).thenReturn(500L);

        attempts.recordFailure("1.2.3.4", "ana@mail.com");

        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void repairsACounterLeftWithoutExpiry() {
        when(values.increment(key)).thenReturn(3L);
        when(redis.getExpire(key)).thenReturn(-1L);

        attempts.recordFailure("1.2.3.4", "ana@mail.com");

        verify(redis).expire(key, LoginAttemptService.WINDOW);
    }
}
