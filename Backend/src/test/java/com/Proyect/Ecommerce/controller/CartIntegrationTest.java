package com.Proyect.Ecommerce.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

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

/** HU-13 · Cantidades del carrito con una sola regla de stock, against real Postgres and Redis. */
@IntegrationTest
@AutoConfigureMockMvc
class CartIntegrationTest {

    private static final long LUCIA = 7L;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private StringRedisTemplate redis;

    private Category category;

    @BeforeEach
    void emptyStore() {
        jdbc.execute("TRUNCATE order_items, orders, wishlist, product_images, products, categories RESTART IDENTITY CASCADE");
        ProductSearchIndex.productsChanged();
        var cartKeys = redis.keys("cart:*");
        if (cartKeys != null && !cartKeys.isEmpty()) {
            redis.delete(cartKeys);
        }
        Category bazar = new Category();
        bazar.setName("Bazar");
        category = categories.save(bazar);
    }

    /** Escenario: Subir la cantidad dentro del stock. */
    @Test
    void raisesTheQuantityWithinStock() throws Exception {
        Product taza = product("Taza", "1000.00", 5);
        mvc.perform(post("/api/cart/{id}", taza.getId()).with(asLucia())).andExpect(status().isOk());

        mvc.perform(put("/api/cart/{id}", taza.getId()).param("qty", "3").with(asLucia()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/cart").with(asLucia()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].productName").value("Taza"))
                .andExpect(jsonPath("$[0].quantity").value(3))
                .andExpect(jsonPath("$[0].stock").value(5))
                .andExpect(jsonPath("$[0].available").value(true));
    }

    /** Escenario: Pedir más que el stock. */
    @Test
    void askingForMoreThanTheStockIs409AndKeepsTheQuantity() throws Exception {
        Product taza = product("Taza", "1000.00", 5);
        mvc.perform(post("/api/cart/{id}", taza.getId()).param("qty", "2").with(asLucia()));

        mvc.perform(put("/api/cart/{id}", taza.getId()).param("qty", "6").with(asLucia()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Solo quedan 5."));

        mvc.perform(get("/api/cart").with(asLucia()))
                .andExpect(jsonPath("$[0].quantity").value(2));
    }

    @Test
    void addingPastTheStockIs409TooAndDoesNotTrim() throws Exception {
        Product taza = product("Taza", "1000.00", 5);
        mvc.perform(post("/api/cart/{id}", taza.getId()).param("qty", "4").with(asLucia()));

        mvc.perform(post("/api/cart/{id}", taza.getId()).param("qty", "2").with(asLucia()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Solo quedan 5."));

        mvc.perform(get("/api/cart").with(asLucia()))
                .andExpect(jsonPath("$[0].quantity").value(4));
    }

    /** Escenario: Un producto se desactiva mientras está en el carrito. */
    @Test
    void aDeactivatedProductStaysInTheCartAsNotAvailable() throws Exception {
        Product gorra = product("Gorra roja", "5000.00", 4);
        mvc.perform(post("/api/cart/{id}", gorra.getId()).with(asLucia()));
        gorra.setActive(false);
        products.save(gorra);

        // Asked twice: reading the cart must not drop it.
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/cart").with(asLucia()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].productName").value("Gorra roja"))
                    .andExpect(jsonPath("$[0].available").value(false));
        }
    }

    @Test
    void aProductThatRanOutStaysAsNotAvailable() throws Exception {
        Product taza = product("Taza", "1000.00", 2);
        mvc.perform(post("/api/cart/{id}", taza.getId()).param("qty", "2").with(asLucia()));
        setStock(taza, 0);

        mvc.perform(get("/api/cart").with(asLucia()))
                .andExpect(jsonPath("$[0].quantity").value(2))
                .andExpect(jsonPath("$[0].available").value(false));
    }

    @Test
    void whenStockDropsBelowTheCartTheQuantityIsNotTrimmed() throws Exception {
        Product taza = product("Taza", "1000.00", 5);
        mvc.perform(post("/api/cart/{id}", taza.getId()).param("qty", "4").with(asLucia()));
        setStock(taza, 2);

        mvc.perform(get("/api/cart").with(asLucia()))
                .andExpect(jsonPath("$[0].quantity").value(4))
                .andExpect(jsonPath("$[0].stock").value(2))
                .andExpect(jsonPath("$[0].available").value(true));
    }

    @Test
    void anInactiveProductCannotBeAddedOrChanged() throws Exception {
        Product gorra = product("Gorra roja", "5000.00", 4);
        mvc.perform(post("/api/cart/{id}", gorra.getId()).with(asLucia()));
        gorra.setActive(false);
        products.save(gorra);

        mvc.perform(put("/api/cart/{id}", gorra.getId()).param("qty", "2").with(asLucia()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El producto no se encuentra disponible."));
        mvc.perform(post("/api/cart/{id}", gorra.getId()).with(asLucia()))
                .andExpect(status().isConflict());
    }

    // ── helpers ──────────────────────────────────────────────

    private static RequestPostProcessor asLucia() {
        return authentication(new UsernamePasswordAuthenticationToken(
                "lucia@example.com", LUCIA, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Product product(String name, String price, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setCategory(category);
        return products.save(product);
    }

    private void setStock(Product product, int stock) {
        jdbc.update("UPDATE products SET stock = ? WHERE id = ?", stock, product.getId());
    }
}
