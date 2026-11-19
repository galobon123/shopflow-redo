package com.Proyect.Ecommerce.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.Proyect.Ecommerce.IntegrationTest;
import com.Proyect.Ecommerce.model.Category;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.repository.CategoryRepository;
import com.Proyect.Ecommerce.repository.ProductRepository;

/** HU-10 · Listado de productos con filtros, búsqueda y paginación, against real Postgres. */
@IntegrationTest
@AutoConfigureMockMvc
class ProductCatalogIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;

    private Category remeras;
    private Category gorras;

    @BeforeEach
    void catalog() {
        jdbc.execute("TRUNCATE order_items, orders, wishlist, products, categories RESTART IDENTITY CASCADE");
        remeras = category("Remeras");
        gorras = category("Gorras");
    }

    @Test
    void browsesACategoryTwelveAtATime() throws Exception {
        for (int i = 1; i <= 30; i++) {
            product("Remera " + i, "1000", remeras, true);
        }
        product("Gorra azul", "800", gorras, true);

        mvc.perform(get("/api/products").param("category", "Remeras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(12)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalItems").value(30))
                .andExpect(jsonPath("$.totalPages").value(3));

        mvc.perform(get("/api/products").param("category", "remeras").param("page", "2"))
                .andExpect(jsonPath("$.items", hasSize(6)))
                .andExpect(jsonPath("$.items[*].categoryName", not(hasItem("Gorras"))));
    }

    @Test
    void aSearchWithNoResultsIsAnEmptyPage() throws Exception {
        product("Remera lino", "1000", remeras, true);

        mvc.perform(get("/api/products").param("q", "zzzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void searchesNameAndDescriptionIgnoringCase() throws Exception {
        product("Remera lino", "1000", remeras, true);
        Product gorra = product("Gorra", "800", gorras, true);
        gorra.setDescription("Visera de LINO lavado");
        products.save(gorra);
        product("Remera algodón", "900", remeras, true);

        mvc.perform(get("/api/products").param("q", "Lino").param("sort", "name"))
                .andExpect(jsonPath("$.items[*].name", contains("Gorra", "Remera lino")));
    }

    @Test
    void percentAndUnderscoreAreSearchedLiterally() throws Exception {
        product("Descuento 50% off", "1000", remeras, true);
        product("Remera 500", "1000", remeras, true);

        mvc.perform(get("/api/products").param("q", "50%"))
                .andExpect(jsonPath("$.items[*].name", contains("Descuento 50% off")));
    }

    @Test
    void aDeactivatedProductIsHiddenFromCustomersButNotFromTheAdmin() throws Exception {
        product("Remera lino", "1000", remeras, true);
        product("Gorra roja", "800", gorras, false);

        mvc.perform(get("/api/products"))
                .andExpect(jsonPath("$.items[*].name", contains("Remera lino")));

        mvc.perform(get("/api/admin/products").param("sort", "name").with(user("admin@shopflow.local").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", contains("Gorra roja", "Remera lino")));
    }

    @Test
    void sortsByPrice() throws Exception {
        product("Cara", "3000", remeras, true);
        product("Barata", "1000", remeras, true);
        product("Media", "2000", remeras, true);

        mvc.perform(get("/api/products").param("sort", "price_asc"))
                .andExpect(jsonPath("$.items[*].name", contains("Barata", "Media", "Cara")));
        mvc.perform(get("/api/products").param("sort", "price_desc"))
                .andExpect(jsonPath("$.items[*].name", contains("Cara", "Media", "Barata")));
    }

    @Test
    void rejectsAnUnknownSortAndCapsThePageSize() throws Exception {
        mvc.perform(get("/api/products").param("sort", "popular"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Orden no válido: popular. Usá newest, price_asc, price_desc o name."));

        mvc.perform(get("/api/products").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(48));
    }

    @Test
    void theRemovedEndpointsAreGone() throws Exception {
        mvc.perform(get("/api/products/priceRange/1/2")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/products/name/Remera")).andExpect(status().is4xxClientError());
    }

    private Category category(String name) {
        Category category = new Category();
        category.setName(name);
        return categories.save(category);
    }

    private Product product(String name, String price, Category category, boolean active) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStock(5);
        product.setCategory(category);
        product.setActive(active);
        return products.save(product);
    }
}
