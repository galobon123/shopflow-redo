package com.Proyect.Ecommerce.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import com.Proyect.Ecommerce.service.MailQueue;
import com.jayway.jsonpath.JsonPath;

/** HU-31 · Verificación del email, against real Postgres and Redis. */
@IntegrationTest
@AutoConfigureMockMvc
class EmailVerificationIntegrationTest {

    private static final String ANA = "ana@mail.com";
    private static final String WELCOME = "Te damos la bienvenida a ShopFlow";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private RecordingMailSender mail;
    @Autowired private MailQueue mailQueue;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE order_items, orders, address, wishlist, users RESTART IDENTITY CASCADE");
        for (String pattern : Set.of("login:*", "device:*", "verify:*", "mail:*")) {
            var keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
        mail.clear();
    }

    private ResultActions register() throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Ana\", \"email\": \"ana@mail.com\", \"password\": \"secreta123\"}"));
    }

    private ResultActions openLink(String token) throws Exception {
        return mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\"}".formatted(token)));
    }

    private ResultActions resend(String jwt) throws Exception {
        var request = post("/api/auth/verify-email/resend");
        return mvc.perform(jwt == null ? request : request.header("Authorization", "Bearer " + jwt));
    }

    private String linkToken() {
        return mail.find(ANA, "/verify-email\\?token=([\\w-]+)");
    }

    private boolean verifiedInDatabase() {
        return jdbc.queryForObject("SELECT email_verified FROM users", Boolean.class);
    }

    @Test
    void registeringSendsTheLinkAndTheAccountStartsUnverified() throws Exception {
        register().andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(false));

        assertEquals("Confirmá tu email en ShopFlow", mail.lastTo(ANA).getSubject());
        assertTrue(mail.lastTo(ANA).getText().contains("http://localhost:4200/verify-email?token="));
        assertEquals(false, verifiedInDatabase());
    }

    @Test
    void theLinkConfirmsTheEmailAndSignsIn() throws Exception {
        register();

        openLink(linkToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.deviceToken").isString());

        assertEquals(true, verifiedInDatabase());
    }

    @Test
    void theLinkWorksOnce() throws Exception {
        register();
        String token = linkToken();
        openLink(token).andExpect(status().isOk());

        openLink(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El enlace venció o ya se usó. Pedí uno nuevo desde tu cuenta."));
    }

    @Test
    void aMadeUpLinkDoesNothing() throws Exception {
        register();

        openLink("inventado").andExpect(status().isBadRequest());
        assertEquals(false, verifiedInDatabase());
    }

    @Test
    void anotherLinkNeedsASessionAndWaitsAMinute() throws Exception {
        String jwt = JsonPath.read(register().andReturn().getResponse().getContentAsString(), "$.token");

        resend(null).andExpect(status().isUnauthorized());
        resend(jwt).andExpect(status().isTooManyRequests());
        assertEquals(1, mail.to(ANA).size());
    }

    @Test
    void askingAgainWhenAlreadyVerifiedSendsNothingAndRefreshesTheSession() throws Exception {
        register();
        // The link signs in where it was opened, which closes the session that registered.
        String jwt = JsonPath.read(openLink(linkToken()).andReturn().getResponse().getContentAsString(), "$.token");
        mail.clear();

        resend(jwt).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
        assertTrue(mail.to(ANA).isEmpty());
    }

    /** HU-20 · Not on registering: the address may still be a typo or someone else's. */
    @Test
    void theWelcomeArrivesWhenTheEmailIsConfirmed() throws Exception {
        register();
        mailQueue.deliverDue(Instant.now());
        assertTrue(mail.to(ANA, WELCOME).isEmpty(), "not on registering");

        openLink(linkToken()).andExpect(status().isOk());
        mailQueue.deliverDue(Instant.now());

        var welcome = mail.to(ANA, WELCOME);
        assertEquals(1, welcome.size());
        assertTrue(welcome.getFirst().getText().contains("Hola Ana,"), welcome.getFirst().getText());
        assertTrue(welcome.getFirst().getHtml().contains("Hola Ana,"), welcome.getFirst().getHtml());
    }

    @Test
    void registeringWorksWithTheEmailServerDown() throws Exception {
        mail.goDown();

        register().andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
    }
}
