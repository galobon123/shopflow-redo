package com.Proyect.Ecommerce.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.Proyect.Ecommerce.IntegrationTest;

/** HU-06 · Registro de cliente, through HTTP and against the real database. */
@IntegrationTest
@AutoConfigureMockMvc
class RegistrationIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cleanUsers() {
        jdbc.execute("TRUNCATE order_items, orders, address, wishlist, users RESTART IDENTITY CASCADE");
    }

    private ResultActions register(String name, String email, String password) throws Exception {
        String body = """
                {"name": "%s", "email": "%s", "password": "%s"}
                """.formatted(name, email, password);
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int users() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
    }

    @Test
    void registersWithANormalizedEmailAndSignsIn() throws Exception {
        register("Ana", " Ana@Mail.com ", "diez-chars")
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.email").value("ana@mail.com"))
                .andExpect(jsonPath("$.token").isString());

        assertEquals("ana@mail.com", jdbc.queryForObject("SELECT email FROM users", String.class));
    }

    @Test
    void theSameEmailWithOtherCapitalsIsAlreadyTaken() throws Exception {
        register("Ana", "ana@mail.com", "diez-chars").andExpect(status().is2xxSuccessful());

        register("Otra Ana", "ANA@mail.com", "diez-chars")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una cuenta con ese email."));
        assertEquals(1, users());
    }

    @Test
    void shortPasswordIsAFieldError() throws Exception {
        register("Ana", "ana@mail.com", "corta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("La contraseña debe tener entre 8 y 72 caracteres"));
        assertEquals(0, users());
    }

    /** HU-09 review · BCrypt's limit is 72 bytes: "ñ" takes two. */
    @Test
    void passwordLengthCountsBytes() throws Exception {
        register("Ana", "ana@mail.com", "ñ".repeat(40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("La contraseña debe tener entre 8 y 72 caracteres"));
        assertEquals(0, users());

        register("Ana", "ana@mail.com", "ñ".repeat(36)).andExpect(status().is2xxSuccessful());
    }

    @Test
    void signsInWithAnyCapitalization() throws Exception {
        register("Ana", "ana@mail.com", "diez-chars").andExpect(status().is2xxSuccessful());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \" Ana@Mail.COM\", \"password\": \"diez-chars\"}"))
                // Right credentials; from a browser that isn't trusted yet, so a code goes by email (HU-33).
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.challengeId").isString());
    }

    /** Two registrations at once with the same email: one account, one 409, never a 500. */
    @Test
    void simultaneousRegistrationsCreateOneAccount() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<String>> responses = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                responses.add(pool.submit(() -> {
                    start.await();
                    var response = register("Ana", "ana@mail.com", "diez-chars").andReturn().getResponse();
                    return response.getStatus() + " " + response.getContentAsString();
                }));
            }
            start.countDown();

            List<String> results = new ArrayList<>();
            for (Future<String> response : responses) {
                results.add(response.get());
            }
            results.sort(null);
            assertTrue(results.get(0).startsWith("200 "), "one succeeds: " + results);
            assertTrue(results.get(1).startsWith("409 "), "the other gets 409: " + results);
            assertTrue(results.get(1).contains("Ya existe una cuenta con ese email."), "with the real reason: " + results);
            assertEquals(1, users());
        } finally {
            pool.shutdownNow();
        }
    }
}
