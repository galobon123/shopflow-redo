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

/** HU-07 · Inicio de sesión con límite de intentos, against real Postgres and Redis. */
@IntegrationTest
@AutoConfigureMockMvc
class LoginIntegrationTest {

    private static final String IP = "203.0.113.7";
    private static final String COUNTER = "login:fail:" + IP + ":ana@mail.com";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;

    @BeforeEach
    void anaHasAnAccount() throws Exception {
        jdbc.execute("TRUNCATE order_items, orders, address, wishlist, users RESTART IDENTITY CASCADE");
        var counters = redis.keys("login:fail:*");
        if (counters != null && !counters.isEmpty()) {
            redis.delete(counters);
        }
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ana\", \"email\": \"ana@mail.com\", \"password\": \"secreta123\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
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
        assertTrue(ttl > 0 && ttl <= 900, "the counter lasts 15 minutes, ttl=" + ttl);
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
                .andExpect(jsonPath("$.detail").value("Demasiados intentos. Probá de nuevo en 15 minutos."));

        long retryAfter = redis.getExpire(COUNTER);
        assertTrue(retryAfter > 840 && retryAfter <= 900, "retry after ~15 minutes, was " + retryAfter);
    }

    @Test
    void theLimitIsPerIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("ana@mail.com", "equivocada", IP).andExpect(status().isUnauthorized());
        }

        login("ana@mail.com", "secreta123", "198.51.100.20").andExpect(status().isOk());
    }
}
