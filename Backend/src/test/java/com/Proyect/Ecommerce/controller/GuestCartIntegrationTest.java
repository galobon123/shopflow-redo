package com.Proyect.Ecommerce.controller;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.Proyect.Ecommerce.IntegrationTest;
import com.Proyect.Ecommerce.model.Category;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.repository.CategoryRepository;
import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.search.ProductSearchIndex;

/** HU-12 · Carrito de invitado y unión al iniciar sesión, against real Postgres and Redis. */
@IntegrationTest
@AutoConfigureMockMvc
class GuestCartIntegrationTest {

    private static final long LUCIA = 7L;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private StringRedisTemplate redis;

    private Category category;
    private String cartId;

    @BeforeEach
    void emptyStore() {
        jdbc.execute("TRUNCATE order_items, orders, wishlist, product_images, products, categories RESTART IDENTITY CASCADE");
        ProductSearchIndex.productsChanged();
        var cartKeys = redis.keys("cart:*");
        if (cartKeys != null && !cartKeys.isEmpty()) {
            redis.delete(cartKeys);
        }
        Category remeras = new Category();
        remeras.setName("Remeras");
        category = categories.save(remeras);
        cartId = UUID.randomUUID().toString();
    }

    @Test
    void aGuestFillsACartThatLivesThreeDays() throws Exception {
        Product remera = product("Remera azul", 5);

        mvc.perform(post("/api/cart/{id}", remera.getId()).param("qty", "2").header("X-Cart-Id", cartId))
                .andExpect(status().isOk());

        mvc.perform(get("/api/cart").header("X-Cart-Id", cartId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName").value("Remera azul"))
                .andExpect(jsonPath("$[0].quantity").value(2));
        Long ttl = redis.getExpire("cart:guest:" + cartId, TimeUnit.SECONDS);
        assertTrue(ttl > Duration.ofDays(3).minusMinutes(1).toSeconds() && ttl <= Duration.ofDays(3).toSeconds(), "ttl " + ttl);
    }

    @Test
    void theStockRuleIsTheSameForAGuest() throws Exception {
        Product remera = product("Remera azul", 5);
        mvc.perform(post("/api/cart/{id}", remera.getId()).header("X-Cart-Id", cartId));

        mvc.perform(put("/api/cart/{id}", remera.getId()).param("qty", "6").header("X-Cart-Id", cartId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Solo quedan 5."));
    }

    @Test
    void withoutSessionOrCartIdItIs401() throws Exception {
        mvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Tenés que iniciar sesión para continuar."));
    }

    /** An expired session must say so (401), not quietly become a guest cart. */
    @Test
    void aTokenThatDoesNotAuthenticateIs401EvenWithACartId() throws Exception {
        Product remera = product("Remera azul", 5);

        mvc.perform(get("/api/cart").header("Authorization", "Bearer expired.or.invalid").header("X-Cart-Id", cartId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Tenés que iniciar sesión para continuar."));
        mvc.perform(post("/api/cart/{id}", remera.getId()).header("Authorization", "Bearer expired.or.invalid").header("X-Cart-Id", cartId))
                .andExpect(status().isUnauthorized());
        assertFalse(redis.hasKey("cart:guest:" + cartId), "nothing went to a guest cart");
    }

    @Test
    void aCartIdThatIsNotAUuidIs400() throws Exception {
        mvc.perform(get("/api/cart").header("X-Cart-Id", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El identificador del carrito no es válido."));
    }

    @Test
    void signedInTheCartIdIsIgnoredAndTheAccountCartIsUsed() throws Exception {
        Product remera = product("Remera azul", 5);
        mvc.perform(post("/api/cart/{id}", remera.getId()).header("X-Cart-Id", cartId));

        mvc.perform(get("/api/cart").header("X-Cart-Id", cartId).with(asLucia()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    /** Escenario: El carrito del invitado pasa a la cuenta. */
    @Test
    void theGuestCartMovesToTheAccount() throws Exception {
        Product remera = product("Remera azul", 5);
        mvc.perform(post("/api/cart/{id}", remera.getId()).param("qty", "2").header("X-Cart-Id", cartId));
        mvc.perform(post("/api/cart/{id}", remera.getId()).with(asLucia()));

        mvc.perform(post("/api/cart/merge").header("X-Cart-Id", cartId).with(asLucia()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.notices", empty()));

        assertFalse(redis.hasKey("cart:guest:" + cartId), "the guest cart is deleted");
        mvc.perform(get("/api/cart").with(asLucia()))
                .andExpect(jsonPath("$[0].quantity").value(3));
    }

    /** Escenario: La unión supera el stock. */
    @Test
    void theMergeIsTrimmedToTheStockAndSaysSo() throws Exception {
        Product remera = product("Remera azul", 2);
        mvc.perform(post("/api/cart/{id}", remera.getId()).param("qty", "2").header("X-Cart-Id", cartId));
        mvc.perform(post("/api/cart/{id}", remera.getId()).with(asLucia()));

        mvc.perform(post("/api/cart/merge").header("X-Cart-Id", cartId).with(asLucia()))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.notices[0]").value("Ajustamos la cantidad de Remera azul al stock disponible"));
    }

    @Test
    void mergingTwiceOrWithoutAGuestCartChangesNothing() throws Exception {
        Product remera = product("Remera azul", 5);
        mvc.perform(post("/api/cart/{id}", remera.getId()).param("qty", "2").header("X-Cart-Id", cartId));

        mvc.perform(post("/api/cart/merge").header("X-Cart-Id", cartId).with(asLucia()));
        mvc.perform(post("/api/cart/merge").header("X-Cart-Id", cartId).with(asLucia()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
        mvc.perform(post("/api/cart/merge").with(asLucia()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void anUnavailableGuestProductComesAlongMarkedAsSuch() throws Exception {
        Product gorra = product("Gorra roja", 4);
        mvc.perform(post("/api/cart/{id}", gorra.getId()).header("X-Cart-Id", cartId));
        gorra.setActive(false);
        products.save(gorra);

        mvc.perform(post("/api/cart/merge").header("X-Cart-Id", cartId).with(asLucia()))
                .andExpect(jsonPath("$.items[0].productName").value("Gorra roja"))
                .andExpect(jsonPath("$.items[0].available").value(false))
                .andExpect(jsonPath("$.notices", empty()));
    }

    @Test
    void mergingNeedsASession() throws Exception {
        mvc.perform(post("/api/cart/merge").header("X-Cart-Id", cartId))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ──────────────────────────────────────────────

    private static RequestPostProcessor asLucia() {
        return authentication(new UsernamePasswordAuthenticationToken(
                "lucia@example.com", LUCIA, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Product product(String name, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal("15000"));
        product.setStock(stock);
        product.setCategory(category);
        return products.save(product);
    }
}
