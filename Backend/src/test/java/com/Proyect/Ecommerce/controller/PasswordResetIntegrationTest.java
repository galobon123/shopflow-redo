package com.Proyect.Ecommerce.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
import com.Proyect.Ecommerce.security.SecureTokens;
import com.jayway.jsonpath.JsonPath;

/** HU-09 · Recuperar contraseña, against real Postgres and Redis. */
@IntegrationTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {

    private static final String ANA = "ana@mail.com";
    private static final String EXPIRED = "El enlace venció, pedí uno nuevo";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private RecordingMailSender mail;

    private String registeredJwt;
    private String deviceToken;

    @BeforeEach
    void anaHasAnAccount() throws Exception {
        jdbc.execute("TRUNCATE order_items, orders, address, wishlist, users RESTART IDENTITY CASCADE");
        for (String pattern : Set.of("login:*", "device:*", "verify:*", "pwreset:*")) {
            var keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ana\", \"email\": \"ana@mail.com\", \"password\": \"secreta123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        registeredJwt = JsonPath.read(body, "$.token");
        deviceToken = JsonPath.read(body, "$.deviceToken");
        mail.clear();
    }

    private ResultActions forgot(String email) throws Exception {
        return mvc.perform(post("/api/auth/forgot").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(email)));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\", \"password\": \"%s\"}".formatted(token, password)));
    }

    private ResultActions login(String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"ana@mail.com\", \"password\": \"%s\", \"deviceToken\": \"%s\"}"
                        .formatted(password, deviceToken)));
    }

    private String linkToken() {
        return mail.find(ANA, "/reset-password\\?token=([\\w-]+)");
    }

    @Test
    void theLinkSetsANewPasswordOnce() throws Exception {
        forgot(ANA).andExpect(status().isNoContent());
        assertEquals("Restablecé tu contraseña de ShopFlow", mail.lastTo(ANA).getSubject());
        String token = linkToken();

        reset(token, "nuevaClave99")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.deviceToken").isString());

        login("nuevaClave99").andExpect(status().isOk());
        login("secreta123").andExpect(status().isUnauthorized());
        reset(token, "otraClave99")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(EXPIRED));
    }

    @Test
    void theLinkLastsThirtyMinutes() throws Exception {
        forgot(ANA);

        Long ttl = redis.getExpire("pwreset:" + SecureTokens.sha256(linkToken()), TimeUnit.SECONDS);
        assertTrue(ttl > 29 * 60 && ttl <= 30 * 60, "TTL " + ttl);
    }

    @Test
    void anExpiredLinkSaysToAskForANewOne() throws Exception {
        forgot(ANA);
        String token = linkToken();
        // What Redis does once the 30 minutes pass.
        redis.delete("pwreset:" + SecureTokens.sha256(token));

        reset(token, "nuevaClave99")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(EXPIRED));
        login("secreta123").andExpect(status().isOk());
    }

    @Test
    void anUnknownEmailGetsTheSameAnswerAndNoEmail() throws Exception {
        forgot("nadie@mail.com").andExpect(status().isNoContent());

        assertTrue(mail.to("nadie@mail.com").isEmpty());
    }

    @Test
    void changingThePasswordClosesTheOpenSessions() throws Exception {
        forgot(ANA);
        String jwt = JsonPath.read(reset(linkToken(), "nuevaClave99").andReturn().getResponse().getContentAsString(),
                "$.token");

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + registeredJwt))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    void aGoogleAccountWithoutPasswordCanSetOne() throws Exception {
        jdbc.update("UPDATE users SET password = NULL, google_sub = 'google-ana'");
        forgot(ANA);

        reset(linkToken(), "nuevaClave99").andExpect(status().isOk());
        login("nuevaClave99").andExpect(status().isOk());
    }

    @Test
    void anotherLinkWaitsAMinuteWithoutSayingSo() throws Exception {
        forgot(ANA).andExpect(status().isNoContent());
        mail.lastTo(ANA);
        forgot(ANA).andExpect(status().isNoContent());

        assertEquals(1, mail.to(ANA).size());
    }

    @Test
    void theNewPasswordFollowsTheRegistrationRules() throws Exception {
        forgot(ANA);

        reset(linkToken(), "corta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Tiene que tener al menos 8 caracteres."));
    }

    /** 40 characters but 80 bytes: BCrypt's limit is in bytes. The link must survive the error. */
    @Test
    void aPasswordOverSeventyTwoBytesIsAFieldErrorAndKeepsTheLink() throws Exception {
        forgot(ANA);
        String token = linkToken();

        reset(token, "ñ".repeat(40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Es demasiado larga. Probá con una más corta."));

        reset(token, "nuevaClave99").andExpect(status().isOk());
    }

    /** The answer doesn't wait for the SMTP server, so its time doesn't tell whether the account exists. */
    @Test
    void theAnswerDoesNotWaitForTheEmail() throws Exception {
        mail.slowDown(Duration.ofSeconds(3));

        long start = System.nanoTime();
        forgot(ANA).andExpect(status().isNoContent());
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertTrue(millis < 1_000, "Took " + millis + " ms");
        assertEquals("Restablecé tu contraseña de ShopFlow", mail.lastTo(ANA).getSubject());
    }

    @Test
    void askingWorksWithTheEmailServerDown() throws Exception {
        mail.goDown();

        forgot(ANA).andExpect(status().isNoContent());
    }
}
