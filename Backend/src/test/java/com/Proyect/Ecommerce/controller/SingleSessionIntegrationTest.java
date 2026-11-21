package com.Proyect.Ecommerce.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import com.Proyect.Ecommerce.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

/** One open session per account: signing in from another browser closes the previous one at once. */
@IntegrationTest
@AutoConfigureMockMvc
class SingleSessionIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private String firstBrowser;
    private String deviceToken;

    @BeforeEach
    void anaIsSignedIn() throws Exception {
        jdbc.execute("TRUNCATE order_items, orders, address, wishlist, users RESTART IDENTITY CASCADE");
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ana\", \"email\": \"ana@mail.com\", \"password\": \"secreta123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        firstBrowser = JsonPath.read(registered, "$.token");
        deviceToken = JsonPath.read(registered, "$.deviceToken");
    }

    /** Signs in again; the device token skips the new-device code (HU-33), which has its own tests. */
    private String signInElsewhere() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ana@mail.com\", \"password\": \"secreta123\", \"deviceToken\": \"%s\"}"
                                .formatted(deviceToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    @Test
    void signingInElsewhereClosesTheFirstSession() throws Exception {
        String secondBrowser = signInElsewhere();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + firstBrowser))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("session-replaced"))
                .andExpect(jsonPath("$.detail").value("Abriste tu cuenta en otro navegador."));
        mvc.perform(get("/api/products").header("Authorization", "Bearer " + firstBrowser))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + secondBrowser))
                .andExpect(status().isOk());
    }

    @Test
    void theOpenStreamHearsItRightAway() throws Exception {
        MockHttpServletResponse stream = mvc.perform(get("/api/session/events")
                        .header("Authorization", "Bearer " + firstBrowser))
                .andExpect(request().asyncStarted())
                .andReturn().getResponse();

        signInElsewhere();

        long deadline = System.currentTimeMillis() + 5_000;
        while (!stream.getContentAsString().contains("event:replaced") && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(stream.getContentAsString().contains("event:replaced"), stream.getContentAsString());
    }

    @Test
    void aFreshTokenForTheSameBrowserKeepsTheSession() throws Exception {
        // Already verified, so asking for another link only refreshes the token (no one-per-minute limit).
        jdbc.update("UPDATE users SET email_verified = TRUE");
        String body = mvc.perform(post("/api/auth/verify-email/resend")
                        .header("Authorization", "Bearer " + firstBrowser))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshed = JsonPath.read(body, "$.token");

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + firstBrowser))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + refreshed))
                .andExpect(status().isOk());
    }
}
