package com.Proyect.Ecommerce.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.Proyect.Ecommerce.IntegrationTest;
import com.Proyect.Ecommerce.RecordingMailSender;
import com.jayway.jsonpath.JsonPath;

/**
 * HU-07 · Inicio de sesión con límite de intentos, against real Postgres and Redis.
 * Signs in from the browser Ana registered with, which is trusted: the new-device code (HU-33)
 * has its own tests in NewDeviceLoginIntegrationTest.
 */
@IntegrationTest
@AutoConfigureMockMvc
class LoginIntegrationTest {

    private static final String IP = "203.0.113.7";
    private static final String COUNTER = "login:fail:" + IP + ":ana@mail.com";
    private static final String LOCK = "login:lock:" + IP + ":ana@mail.com";
    private static final String CHROME_ON_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private RecordingMailSender mail;

    private String anasBrowser;

    @BeforeEach
    void anaHasAnAccount() throws Exception {
        jdbc.execute("TRUNCATE order_items, orders, address, wishlist, users RESTART IDENTITY CASCADE");
        for (String pattern : new String[] {"login:fail:*", "login:lock:*"}) {
            var keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ana\", \"email\": \"ana@mail.com\", \"password\": \"secreta123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        anasBrowser = JsonPath.read(registered, "$.deviceToken");
        mail.clear();
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .header("User-Agent", CHROME_ON_WINDOWS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\", \"deviceToken\": \"%s\"}"
                        .formatted(email, password, anasBrowser)));
    }

    private int failures() {
        String count = redis.opsForValue().get(COUNTER);
        return count == null ? 0 : Integer.parseInt(count);
    }

    @Test
    void rightPasswordGivesATokenAndClearsTheCounter() throws Exception {
        login("ana@mail.com", "equivocada", IP).andExpect(status().isUnauthorized());
        assertEquals(1, failures());

        login("ana@mail.com", "secreta123", IP)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());

        assertFalse(redis.hasKey(COUNTER));
    }

    @Test
    void wrongPasswordIs401AndCountsOneFailure() throws Exception {
        login("ana@mail.com", "equivocada", IP)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos."));

        assertEquals(1, failures());
        long ttl = redis.getExpire(COUNTER);
        assertTrue(ttl > 3540 && ttl <= 3600, "failures are remembered for an hour, ttl=" + ttl);
    }

    @Test
    void unknownEmailLooksExactlyLikeAWrongPassword() throws Exception {
        login("nadie@mail.com", "equivocada", IP)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos."));
    }

    @Test
    void afterFiveFailuresEvenTheRightPasswordWaits() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("ana@mail.com", "equivocada", IP).andExpect(status().isUnauthorized());
        }

        login("ana@mail.com", "secreta123", IP)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").value("Demasiados intentos. Probá de nuevo en 1 minuto."));

        long retryAfter = redis.getExpire(LOCK);
        assertTrue(retryAfter > 0 && retryAfter <= 60, "retry after ~1 minute, was " + retryAfter);
        assertEquals(5, failures(), "tries while waiting don't count");
        assertTrue(mail.to("ana@mail.com").isEmpty());
    }

    /** Fails 5 times, then skips the 1-minute wait as if it had passed. */
    private void failFiveTimes() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("ana@mail.com", "equivocada", IP).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void theFifteenthFailureLocksForFifteenMinutesAndAsksAnaIfItWasHer() throws Exception {
        failFiveTimes();
        redis.delete(LOCK);
        failFiveTimes();
        redis.delete(LOCK);
        failFiveTimes();

        login("ana@mail.com", "secreta123", IP)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Demasiados intentos. Probá de nuevo en 15 minutos."));
        long retryAfter = redis.getExpire(LOCK);
        assertTrue(retryAfter > 840 && retryAfter <= 900, "retry after ~15 minutes, was " + retryAfter);

        String alert = mail.lastTo("ana@mail.com").getText();
        assertTrue(alert.contains("¿Fuiste vos?"), alert);
        assertTrue(alert.contains("desde Chrome en Windows"), alert);
        assertTrue(alert.contains(" a las ") && alert.contains("(hora de Argentina)"), alert);
        assertEquals(1, mail.to("ana@mail.com").size());
    }

    @Test
    void theLimitIsPerIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("ana@mail.com", "equivocada", IP).andExpect(status().isUnauthorized());
        }

        login("ana@mail.com", "secreta123", "198.51.100.20").andExpect(status().isOk());
    }
}
