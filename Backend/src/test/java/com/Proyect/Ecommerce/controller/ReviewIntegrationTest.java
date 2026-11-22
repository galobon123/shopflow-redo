package com.Proyect.Ecommerce.controller;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.Proyect.Ecommerce.IntegrationTest;
import com.Proyect.Ecommerce.model.Address;
import com.Proyect.Ecommerce.model.Category;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.model.Role;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.repository.AddressRepository;
import com.Proyect.Ecommerce.repository.CategoryRepository;
import com.Proyect.Ecommerce.repository.CityRepository;
import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.repository.UserRepository;
import com.Proyect.Ecommerce.search.ProductSearchIndex;

/** HU-30 · Reseñas de productos comprados, against real Postgres. */
@IntegrationTest
@AutoConfigureMockMvc
class ReviewIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private UserRepository users;
    @Autowired private AddressRepository addresses;
    @Autowired private CityRepository cities;

    private Product remera;

    @BeforeEach
    void catalog() {
        jdbc.execute("TRUNCATE reviews, order_items, orders, address, wishlist, product_images, products, categories, users RESTART IDENTITY CASCADE");
        ProductSearchIndex.productsChanged();
        Category category = new Category();
        category.setName("Remeras");
        category = categories.save(category);
        Product product = new Product();
        product.setName("Remera lino");
        product.setPrice(new BigDecimal("48900"));
        product.setStock(3);
        product.setCategory(category);
        remera = products.save(product);
    }

    @Test
    void aReviewOfADeliveredProductShowsOnItsPage() throws Exception {
        User ana = customer("Ana Gómez");
        order(ana, "ENTREGADA", remera);

        mvc.perform(review(remera, 4, "Muy buena tela").with(as(ana)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.comment").value("Muy buena tela"))
                .andExpect(jsonPath("$.authorName").value("Ana"))
                // An instant, so the browser shows the day of its own time zone (the server runs in UTC).
                .andExpect(jsonPath("$.createdAt").value(endsWith("Z")));

        mvc.perform(get("/api/products/{id}/reviews", remera.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.items", hasSize(1)))
                .andExpect(jsonPath("$.reviews.items[0].comment").value("Muy buena tela"))
                .andExpect(jsonPath("$.summary.average").value(4.0))
                .andExpect(jsonPath("$.summary.total").value(1))
                .andExpect(jsonPath("$.summary.counts.4").value(1));
        mvc.perform(get("/api/products/{id}", remera.getId()))
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.reviewCount").value(1));
    }

    @Test
    void withoutADeliveredOrderItIs403() throws Exception {
        User ana = customer("Ana");
        order(ana, "ENVIADA", remera); // on its way: not received yet

        mvc.perform(review(remera, 4, "Muy buena tela").with(as(ana)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Solo podés reseñar productos que recibiste."));
        mvc.perform(get("/api/products/{id}/reviews", remera.getId()))
                .andExpect(jsonPath("$.summary.total").value(0));
    }

    @Test
    void anotherCustomersDeliveredOrderDoesNotCount() throws Exception {
        order(customer("Bruno"), "ENTREGADA", remera);

        mvc.perform(review(remera, 5, null).with(as(customer("Ana"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSecondReviewOfTheSameProductIs409() throws Exception {
        User ana = customer("Ana");
        order(ana, "ENTREGADA", remera);
        mvc.perform(review(remera, 4, null).with(as(ana))).andExpect(status().isCreated());

        mvc.perform(review(remera, 2, "Cambié de idea").with(as(ana)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya reseñaste este producto."));
    }

    @Test
    void guestsCantReview() throws Exception {
        mvc.perform(review(remera, 4, null)).andExpect(status().isUnauthorized());
    }

    @Test
    void ratingMustBeOneToFiveAndTheCommentShort() throws Exception {
        User ana = customer("Ana");
        order(ana, "ENTREGADA", remera);

        for (int rating : new int[] {0, 6}) {
            mvc.perform(review(remera, rating, null).with(as(ana)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.rating").value("Elegí de 1 a 5 estrellas."));
        }
        mvc.perform(post("/api/products/{id}/reviews", remera.getId()).with(as(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.errors.rating").value("Elegí de 1 a 5 estrellas."));
        mvc.perform(review(remera, 4, "a".repeat(1001)).with(as(ana)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.comment").value("El comentario puede tener hasta 1000 caracteres."));
    }

    @Test
    void filtersByStars() throws Exception {
        for (int i = 0; i < 3; i++) {
            reviewed(customer("Cinco" + i), 5);
        }
        for (int i = 0; i < 2; i++) {
            reviewed(customer("Uno" + i), 1);
        }

        mvc.perform(get("/api/products/{id}/reviews", remera.getId()).param("rating", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.items", hasSize(2)))
                .andExpect(jsonPath("$.reviews.items[*].rating", everyItem(is(1))))
                .andExpect(jsonPath("$.reviews.totalItems").value(2))
                // The summary is always of every review, whatever the filter.
                .andExpect(jsonPath("$.summary.total").value(5))
                .andExpect(jsonPath("$.summary.average").value(3.4))
                .andExpect(jsonPath("$.summary.counts.5").value(3))
                .andExpect(jsonPath("$.summary.counts.1").value(2))
                .andExpect(jsonPath("$.summary.counts.3").value(0));
        mvc.perform(get("/api/products/{id}/reviews", remera.getId()).param("rating", "6"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reviewsComeNewestFirstInPages() throws Exception {
        for (int i = 0; i < 12; i++) {
            reviewed(customer("Cliente" + i), 4);
        }

        mvc.perform(get("/api/products/{id}/reviews", remera.getId()))
                .andExpect(jsonPath("$.reviews.items", hasSize(10)))
                .andExpect(jsonPath("$.reviews.items[0].authorName").value("Cliente11"))
                .andExpect(jsonPath("$.reviews.totalPages").value(2));
        mvc.perform(get("/api/products/{id}/reviews", remera.getId()).param("page", "1"))
                .andExpect(jsonPath("$.reviews.items", hasSize(2)));
    }

    @Test
    void theAdminHidesAReviewAndItStopsCounting() throws Exception {
        reviewed(customer("Ana"), 5);
        Long bad = reviewed(customer("Bruno"), 1);

        mvc.perform(put("/api/admin/reviews/{id}/hide", bad).with(user("lucia@example.com").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/reviews/{id}/hide", bad).with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/products/{id}/reviews", remera.getId()))
                .andExpect(jsonPath("$.reviews.items", hasSize(1)))
                .andExpect(jsonPath("$.summary.total").value(1))
                .andExpect(jsonPath("$.summary.average").value(5.0))
                .andExpect(jsonPath("$.summary.counts.1").value(0));
        mvc.perform(get("/api/products/{id}", remera.getId()))
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.reviewCount").value(1));

        mvc.perform(put("/api/admin/reviews/{id}/show", bad).with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/products/{id}/reviews", remera.getId()))
                .andExpect(jsonPath("$.summary.total").value(2));
        mvc.perform(put("/api/admin/reviews/{id}/hide", 9999).with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void tellsTheCustomerWhetherTheyCanReview() throws Exception {
        User ana = customer("Ana");
        mvc.perform(get("/api/products/{id}/reviews", remera.getId()))
                .andExpect(jsonPath("$.canReview").value(false)); // guest
        mvc.perform(get("/api/products/{id}/reviews", remera.getId()).with(as(ana)))
                .andExpect(jsonPath("$.canReview").value(false)); // never received it

        order(ana, "ENTREGADA", remera);
        mvc.perform(get("/api/products/{id}/reviews", remera.getId()).with(as(ana)))
                .andExpect(jsonPath("$.canReview").value(true));

        mvc.perform(review(remera, 4, null).with(as(ana))).andExpect(status().isCreated());
        mvc.perform(get("/api/products/{id}/reviews", remera.getId()).with(as(ana)))
                .andExpect(jsonPath("$.canReview").value(false)); // already did
    }

    @Test
    void reviewsOfAMissingProductAre404() throws Exception {
        mvc.perform(get("/api/products/{id}/reviews", 9999)).andExpect(status().isNotFound());
        mvc.perform(post("/api/products/{id}/reviews", 9999).with(as(customer("Bruno")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4}"))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    /** Signed in the way JwtAuthFilter leaves it: the user id travels as the credentials. */
    private static RequestPostProcessor as(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                user.getEmail(), user.getId(), List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private static MockHttpServletRequestBuilder review(Product product, int rating, String comment) {
        String body = comment == null
                ? "{\"rating\":" + rating + "}"
                : "{\"rating\":" + rating + ",\"comment\":\"" + comment + "\"}";
        return post("/api/products/{id}/reviews", product.getId())
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** A delivered order of the product and the review it allowed; returns the review id. */
    private Long reviewed(User user, int rating) throws Exception {
        order(user, "ENTREGADA", remera);
        mvc.perform(review(remera, rating, null).with(as(user))).andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM reviews WHERE user_id = ?", Long.class, user.getId());
    }

    private User customer(String name) {
        User user = new User();
        user.setName(name);
        user.setEmail("cliente" + users.count() + "@example.com");
        user.setPassword("not-used");
        user.setRole(Role.USER);
        user.setEmailVerified(true);
        return users.save(user);
    }

    private void order(User owner, String status, Product product) {
        Long orderId = jdbc.queryForObject(
                "INSERT INTO orders (created_at, user_id, total, address_id, status) VALUES (now(), ?, 48900, ?, ?) RETURNING id",
                Long.class, owner.getId(), address(owner).getId(), status);
        jdbc.update("INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, 1, 48900)",
                orderId, product.getId());
    }

    private Address address(User owner) {
        Integer rosario = jdbc.queryForObject("SELECT id FROM city WHERE name = 'Rosario'", Integer.class);
        Address address = new Address();
        address.setFirstName(owner.getName());
        address.setLastName("Test");
        address.setAddress("San Martín 123");
        address.setZipCode("2000");
        address.setCity(cities.findById(rosario).orElseThrow());
        address.setCreatedBy(owner);
        return addresses.save(address);
    }
}
