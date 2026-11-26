package com.Proyect.Ecommerce.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
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
import com.Proyect.Ecommerce.model.ProductImage;
import com.Proyect.Ecommerce.repository.CategoryRepository;
import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.search.ProductSearchIndex;

/** HU-11 · Detalle de producto, against real Postgres. */
@IntegrationTest
@AutoConfigureMockMvc
class ProductDetailIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;

    private Category remeras;

    @BeforeEach
    void catalog() {
        jdbc.execute("TRUNCATE order_items, orders, wishlist, product_images, products, categories RESTART IDENTITY CASCADE");
        ProductSearchIndex.productsChanged();
        Category category = new Category();
        category.setName("Remeras");
        remeras = categories.save(category);
    }

    @Test
    void showsAnActiveProductWithItsPhotosInOrder() throws Exception {
        Product remera = product("Remera lino", true);
        // Saved out of order on purpose: the gallery follows position, not insertion.
        remera.getImages().add(new ProductImage("https://cdn.example/remera-3.jpg", 2));
        remera.getImages().add(new ProductImage("https://cdn.example/remera-1.jpg", 0));
        remera.getImages().add(new ProductImage("https://cdn.example/remera-2.jpg", 1));
        products.save(remera);

        mvc.perform(get("/api/products/{id}", remera.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Remera lino"))
                .andExpect(jsonPath("$.description").value("Lino lavado, corte recto."))
                .andExpect(jsonPath("$.price").value(48900))
                .andExpect(jsonPath("$.stock").value(3))
                .andExpect(jsonPath("$.images", contains(
                        "https://cdn.example/remera-1.jpg",
                        "https://cdn.example/remera-2.jpg",
                        "https://cdn.example/remera-3.jpg")))
                .andExpect(jsonPath("$.imageUrl").value("https://cdn.example/remera-1.jpg"))
                .andExpect(jsonPath("$.averageRating").value(nullValue()))
                .andExpect(jsonPath("$.reviewCount").value(0));
    }

    @Test
    void theCatalogCardShowsTheCover() throws Exception {
        Product remera = product("Remera lino", true);
        remera.getImages().add(new ProductImage("https://cdn.example/remera-2.jpg", 1));
        remera.getImages().add(new ProductImage("https://cdn.example/remera-1.jpg", 0));
        products.save(remera);

        mvc.perform(get("/api/products"))
                .andExpect(jsonPath("$.items[0].imageUrl").value("https://cdn.example/remera-1.jpg"));
    }

    @Test
    void aMissingOrInactiveProductIsNotFound() throws Exception {
        Product gorra = product("Gorra roja", false);

        for (Long id : new Long[] {9999L, gorra.getId()}) {
            mvc.perform(get("/api/products/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Este producto no existe o ya no está disponible."));
        }
        mvc.perform(get("/api/products/{id}", gorra.getId()).with(user("ana@mail.com").roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void theAdminStillSeesAnInactiveProduct() throws Exception {
        Product gorra = product("Gorra roja", false);

        mvc.perform(get("/api/products/{id}", gorra.getId()).with(user("admin@shopflow.local").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Gorra roja"))
                .andExpect(jsonPath("$.active").value(false));
    }

    private Product product(String name, boolean active) {
        Product product = new Product();
        product.setName(name);
        product.setDescription("Lino lavado, corte recto.");
        product.setPrice(new BigDecimal("48900"));
        product.setStock(3);
        product.setCategory(remeras);
        product.setActive(active);
        return products.save(product);
    }
}
