package com.Proyect.Ecommerce.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.Proyect.Ecommerce.IntegrationTest;
import com.Proyect.Ecommerce.security.GoogleTokenVerifier;
import com.Proyect.Ecommerce.security.GoogleTokenVerifier.GoogleIdentity;

/**
 * HU-32 · Ingreso con Google: what happens to accounts. Checking Google's signature is
 * GoogleTokenVerifierTest's job; here the verifier is replaced by one that trusts "credencial-de-ana".
 */
@IntegrationTest
@AutoConfigureMockMvc
class GoogleLoginIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private GoogleTokenVerifier google;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE order_items, orders, address, wishlist, users RESTART IDENTITY CASCADE");
        when(google.verify("credencial-de-ana")).thenReturn(new GoogleIdentity("google-ana", "ana@gmail.com", "Ana Gómez"));
        when(google.clientId()).thenReturn("shopflow.apps.googleusercontent.com");
    }

    private ResultActions signInWithGoogle() throws Exception {
        return mvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\": \"credencial-de-ana\"}"));
    }

    private Map<String, Object> anasRow() {
        return jdbc.queryForMap("SELECT id, name, password, email_verified, google_sub FROM users WHERE email = 'ana@gmail.com'");
    }

    @Test
    void theFirstTimeCreatesAVerifiedAccountWithoutPassword() throws Exception {
        signInWithGoogle()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@gmail.com"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.deviceToken").isString());

        Map<String, Object> ana = anasRow();
        assertEquals("Ana Gómez", ana.get("name"));
        assertNull(ana.get("password"));
        assertEquals("google-ana", ana.get("google_sub"));
    }

    @Test
    void theSecondTimeIsTheSameAccount() throws Exception {
        signInWithGoogle();
        signInWithGoogle().andExpect(status().isOk());

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
    }

    @Test
    void anAccountWithTheSameEmailIsJoinedAndKeepsItsPassword() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ana\", \"email\": \"ana@gmail.com\", \"password\": \"secreta123\"}"))
                .andExpect(status().isOk());
        Map<String, Object> before = anasRow();

        signInWithGoogle().andExpect(status().isOk());

        Map<String, Object> after = anasRow();
        assertEquals(before.get("id"), after.get("id"));
        assertEquals(before.get("password"), after.get("password"));
        assertEquals("Ana", after.get("name"), "the name she chose stays");
        assertEquals(true, after.get("email_verified"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
    }

    @Test
    void anAccountMadeWithGoogleHasNoPasswordToGuess() throws Exception {
        signInWithGoogle();

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ana@gmail.com\", \"password\": \"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ana@gmail.com\", \"password\": \"cualquiera\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theLoginScreenGetsTheClientId() throws Exception {
        mvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleClientId").value("shopflow.apps.googleusercontent.com"));
    }
}
