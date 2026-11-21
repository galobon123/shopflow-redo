package com.Proyect.Ecommerce.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

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

/** HU-33 · Verificación de dispositivo nuevo, against real Postgres and Redis. */
@IntegrationTest
@AutoConfigureMockMvc
class NewDeviceLoginIntegrationTest {

    private static final String ANA = "ana@mail.com";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private RecordingMailSender mail;

    @BeforeEach
    void anaHasAnAccount() throws Exception {
        jdbc.execute("TRUNCATE order_items, orders, address, wishlist, users RESTART IDENTITY CASCADE");
        for (String pattern : Set.of("login:*", "device:*", "verify:*")) {
            var keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
        mail.clear();
        register("Ana", ANA);
        mail.clear();
    }

    private String register(String name, String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\", \"email\": \"%s\", \"password\": \"secreta123\"}".formatted(name, email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.deviceToken");
    }

    private ResultActions login(String deviceToken) throws Exception {
        String device = deviceToken == null ? "" : ", \"deviceToken\": \"%s\"".formatted(deviceToken);
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"ana@mail.com\", \"password\": \"secreta123\"%s}".formatted(device)));
    }

    /** Signs in from a new browser and returns the challenge id. */
    private String loginFromANewBrowser() throws Exception {
        String body = login(null).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.challengeId");
    }

    private ResultActions sendCode(String challengeId, String code) throws Exception {
        return mvc.perform(post("/api/auth/login/code").contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\": \"%s\", \"code\": \"%s\"}".formatted(challengeId, code)));
    }

    private String codeInEmail() {
        return mail.find(ANA, "código es: (\\d{6})");
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    void aNewBrowserGetsACodeByEmailInsteadOfASession() throws Exception {
        login(null)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.challengeId").isString())
                .andExpect(jsonPath("$.sentTo").value("a***@mail.com"))
                .andExpect(jsonPath("$.token").doesNotExist());

        assertEquals(1, mail.to(ANA).size());
        assertTrue(mail.lastTo(ANA).getSubject().startsWith(codeInEmail()), "the code is in the subject too");
    }

    @Test
    void theRightCodeSignsInAndTheBrowserIsTrustedFromThenOn() throws Exception {
        String challenge = loginFromANewBrowser();

        String body = sendCode(challenge, codeInEmail())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.deviceToken").isString())
                .andReturn().getResponse().getContentAsString();
        String device = JsonPath.read(body, "$.deviceToken");
        mail.clear();

        login(device).andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
        assertTrue(mail.to(ANA).isEmpty(), "no code for a trusted browser");
    }

    @Test
    void theCodeWorksOnlyOnce() throws Exception {
        String challenge = loginFromANewBrowser();
        String code = codeInEmail();
        sendCode(challenge, code).andExpect(status().isOk());

        sendCode(challenge, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El código venció. Iniciá sesión de nuevo."));
    }

    @Test
    void fiveWrongCodesBurnTheChallenge() throws Exception {
        String challenge = loginFromANewBrowser();
        String code = codeInEmail();

        sendCode(challenge, wrong(code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El código no es correcto. Te quedan 4 intentos."));
        for (int i = 0; i < 3; i++) {
            sendCode(challenge, wrong(code)).andExpect(status().isBadRequest());
        }
        sendCode(challenge, wrong(code))
                .andExpect(jsonPath("$.detail").value("Demasiados códigos equivocados. Iniciá sesión de nuevo."));

        // Even the right one no longer works: a guesser gets 5 tries, not a million.
        sendCode(challenge, code).andExpect(status().isBadRequest());
    }

    @Test
    void anotherAccountsBrowserIsANewBrowser() throws Exception {
        String bobsBrowser = register("Bob", "bob@mail.com");

        login(bobsBrowser).andExpect(status().isAccepted());
    }

    @Test
    void anotherCodeOnlyAfterAMinute() throws Exception {
        String challenge = loginFromANewBrowser();

        mvc.perform(post("/api/auth/login/code/resend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\": \"%s\"}".formatted(challenge)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        assertEquals(1, mail.to(ANA).size());
    }

    @Test
    void theCodeAlsoConfirmsTheEmail() throws Exception {
        assertEquals(false, jdbc.queryForObject("SELECT email_verified FROM users", Boolean.class));

        sendCode(loginFromANewBrowser(), codeInEmail()).andExpect(jsonPath("$.emailVerified").value(true));

        assertEquals(true, jdbc.queryForObject("SELECT email_verified FROM users", Boolean.class));
    }

    @Test
    void withTheEmailServerDownThePersonIsTold() throws Exception {
        mail.goDown();

        login(null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("No pudimos enviarte el código. Probá de nuevo en unos minutos."));
    }
}
