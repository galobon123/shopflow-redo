package com.Proyect.Ecommerce.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.Proyect.Ecommerce.IntegrationTest;
import com.Proyect.Ecommerce.model.Category;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.model.Role;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.repository.CategoryRepository;
import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.repository.UserRepository;
import com.Proyect.Ecommerce.search.ProductSearchIndex;
import com.Proyect.Ecommerce.service.RecommendationService;
import com.Proyect.Ecommerce.service.WishlistGraphSync;

/**
 * HU-14 · Wishlist que no depende de Neo4j, against real Postgres, Redis and Neo4j.
 * "Neo4j caído" is the Neo4j gateway (RecommendationService) failing like an unreachable server.
 */
@IntegrationTest
@AutoConfigureMockMvc
class WishlistIntegrationTest {

    private static final String PENDING = "neo4j:sync:pending";
    private static final String FAILED = "neo4j:sync:failed";
    private static final Duration SYNC = Duration.ofSeconds(10);

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private UserRepository users;
    @Autowired private StringRedisTemplate redis;
    @Autowired private Driver neo4j;
    @Autowired private WishlistGraphSync sync;
    @MockitoSpyBean private RecommendationService recommendations;

    private Category bazar;
    private User ana;

    @BeforeEach
    void emptyStore() {
        jdbc.execute("TRUNCATE reviews, order_items, orders, address, wishlist, product_images, products, categories, users RESTART IDENTITY CASCADE");
        ProductSearchIndex.productsChanged();
        try (var session = neo4j.session()) {
            session.run("MATCH (n) DETACH DELETE n").consume();
        }
        redis.delete(List.of(PENDING, FAILED));
        Category category = new Category();
        category.setName("Bazar");
        bazar = categories.save(category);
        ana = customer("Ana");
    }

    /** Escenario: Agregar a la wishlist. */
    @Test
    void addingShowsItInTheWishlistAndInNeo4j() throws Exception {
        Product taza = product("Taza");

        mvc.perform(post("/api/wishlist/{id}", taza.getId()).with(as(ana))).andExpect(status().isOk());

        mvc.perform(get("/api/wishlist").with(as(ana)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].productName").value("Taza"));
        await().atMost(SYNC).until(() -> wishlisted(ana, taza));
    }

    @Test
    void removingTakesTheRelationOutOfNeo4j() throws Exception {
        Product taza = product("Taza");
        mvc.perform(post("/api/wishlist/{id}", taza.getId()).with(as(ana)));
        await().atMost(SYNC).until(() -> wishlisted(ana, taza));

        mvc.perform(delete("/api/wishlist/{id}", taza.getId()).with(as(ana))).andExpect(status().isOk());

        await().atMost(SYNC).until(() -> !wishlisted(ana, taza));
    }

    /** Escenario: Neo4j caído. */
    @Test
    void withNeo4jDownItSavesQueuesAndTheJobSyncsWhenItComesBack() throws Exception {
        Product taza = product("Taza");
        neo4jDown();

        mvc.perform(post("/api/wishlist/{id}", taza.getId()).with(as(ana))).andExpect(status().isOk());

        mvc.perform(get("/api/wishlist").with(as(ana)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].productName").value("Taza"));
        await().atMost(SYNC).until(() -> pending().size() == 1);
        assertThat(pending()).containsExactly(ana.getId() + ":" + taza.getId() + ":0");
        assertThat(wishlisted(ana, taza)).isFalse();

        reset(recommendations); // Neo4j is back
        sync.retryPending();

        assertThat(wishlisted(ana, taza)).isTrue();
        assertThat(pending()).isEmpty();
    }

    @Test
    void whileNeo4jIsStillDownTheJobLeavesTheQueueAsItIs() throws Exception {
        Product taza = product("Taza");
        neo4jDown();
        mvc.perform(post("/api/wishlist/{id}", taza.getId()).with(as(ana)));
        await().atMost(SYNC).until(() -> pending().size() == 1);

        for (int run = 0; run < 10; run++) {
            sync.retryPending();
        }

        assertThat(pending()).containsExactly(ana.getId() + ":" + taza.getId() + ":0");
        assertThat(redis.opsForList().size(FAILED)).isZero();
    }

    @Test
    void whenNeo4jGoesDownHalfwayTheJobStopsWithoutSpendingAttempts() throws Exception {
        Product taza = product("Taza");
        Product mate = product("Mate");
        for (Product product : List.of(taza, mate)) {
            jdbc.update("INSERT INTO wishlist (user_id, product_id, created_at) VALUES (?, ?, now())", ana.getId(), product.getId());
            redis.opsForList().rightPush(PENDING, ana.getId() + ":" + product.getId() + ":0");
        }
        neo4jDown();
        doReturn(true).doReturn(false).when(recommendations).isAvailable(); // up when the run starts

        sync.retryPending();

        assertThat(pending()).containsExactlyInAnyOrder(
                ana.getId() + ":" + taza.getId() + ":0", ana.getId() + ":" + mate.getId() + ":0");
        assertThat(redis.opsForList().size(FAILED)).isZero();
    }

    /** Added, then removed, then added and removed again while Neo4j was down: it ends as Postgres says. */
    @Test
    void retriesEndAsTheWishlistIsNotInTheOrderTheyQueued() throws Exception {
        Product taza = product("Taza");
        Product mate = product("Mate");
        mvc.perform(post("/api/wishlist/{id}", taza.getId()).with(as(ana)));
        await().atMost(SYNC).until(() -> wishlisted(ana, taza));

        neo4jDown();
        mvc.perform(delete("/api/wishlist/{id}", taza.getId()).with(as(ana)));
        mvc.perform(post("/api/wishlist/{id}", taza.getId()).with(as(ana)));
        mvc.perform(delete("/api/wishlist/{id}", taza.getId()).with(as(ana)));
        mvc.perform(post("/api/wishlist/{id}", mate.getId()).with(as(ana)));
        mvc.perform(delete("/api/wishlist/{id}", mate.getId()).with(as(ana)));
        mvc.perform(post("/api/wishlist/{id}", mate.getId()).with(as(ana)));
        await().atMost(SYNC).until(() -> pending().size() == 6);

        reset(recommendations);
        // The newest first, as if the queue had come out backwards.
        List<String> queued = pending();
        redis.delete(PENDING);
        queued.reversed().forEach(entry -> redis.opsForList().rightPush(PENDING, entry));
        sync.retryPending();

        assertThat(wishlisted(ana, taza)).isFalse();
        assertThat(wishlisted(ana, mate)).isTrue();
        assertThat(pending()).isEmpty();
    }

    /** One that always fails, with Neo4j up, doesn't hold the rest and leaves the queue after a few tries. */
    @Test
    void anEntryThatAlwaysFailsDoesNotBlockTheQueue() throws Exception {
        Product taza = product("Taza");
        Product mate = product("Mate");
        doThrow(new IllegalStateException("Datos que Neo4j no acepta"))
                .when(recommendations).recommendationAdd(argThat(dto -> dto.productId().equals(taza.getId())));
        mvc.perform(post("/api/wishlist/{id}", taza.getId()).with(as(ana)));
        await().atMost(SYNC).until(() -> pending().size() == 1);
        redis.opsForList().rightPush(PENDING, "no-es-un-par");
        redis.opsForList().rightPush(PENDING, ana.getId() + ":" + mate.getId() + ":0");
        jdbc.update("INSERT INTO wishlist (user_id, product_id, created_at) VALUES (?, ?, now())", ana.getId(), mate.getId());

        sync.retryPending();

        assertThat(wishlisted(ana, mate)).isTrue();
        assertThat(pending()).containsExactly(ana.getId() + ":" + taza.getId() + ":1");
        assertThat(redis.opsForList().range(FAILED, 0, -1)).containsExactly("no-es-un-par");

        for (int run = 0; run < 10; run++) {
            sync.retryPending();
        }
        assertThat(pending()).isEmpty();
        assertThat(redis.opsForList().range(FAILED, 0, -1))
                .containsExactly("no-es-un-par", ana.getId() + ":" + taza.getId() + ":5");
    }

    @Test
    void clearingEmptiesTheWholeWishlistAndNeo4jButNotOtherCustomers() throws Exception {
        User bruno = customer("Bruno");
        List<Product> four = List.of(product("Taza"), product("Mate"), product("Bombilla"), product("Termo"));
        for (Product product : four) {
            mvc.perform(post("/api/wishlist/{id}", product.getId()).with(as(ana)));
        }
        mvc.perform(post("/api/wishlist/{id}", four.get(0).getId()).with(as(bruno)));
        await().atMost(SYNC).until(() -> four.stream().allMatch(p -> wishlisted(ana, p)) && wishlisted(bruno, four.get(0)));

        mvc.perform(delete("/api/wishlist").with(as(ana))).andExpect(status().isNoContent());

        mvc.perform(get("/api/wishlist").with(as(ana))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/wishlist").with(as(bruno))).andExpect(jsonPath("$", hasSize(1)));
        await().atMost(SYNC).until(() -> four.stream().noneMatch(p -> wishlisted(ana, p)));
        assertThat(wishlisted(bruno, four.get(0))).isTrue();
    }

    // ── helpers ──────────────────────────────────────────────

    /** Every write to Neo4j fails, and so does asking whether it answers. */
    private void neo4jDown() {
        var unreachable = new TransientDataAccessResourceException("Neo4j no responde");
        doThrow(unreachable).when(recommendations).recommendationAdd(any());
        doThrow(unreachable).when(recommendations).syncWishlistRemove(anyLong(), anyLong());
        doReturn(false).when(recommendations).isAvailable();
    }

    private List<String> pending() {
        return redis.opsForList().range(PENDING, 0, -1);
    }

    private boolean wishlisted(User user, Product product) {
        try (var session = neo4j.session()) {
            return session.run("MATCH (:User {id: $user})-[:WISHLISTED]->(:Product {id: $product}) RETURN count(*) AS n",
                    Map.of("user", user.getId(), "product", product.getId())).single().get("n").asLong() > 0;
        }
    }

    /** Signed in the way JwtAuthFilter leaves it: the user id travels as the credentials. */
    private static RequestPostProcessor as(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                user.getEmail(), user.getId(), List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private User customer(String name) {
        User user = new User();
        user.setName(name);
        user.setEmail(name.toLowerCase() + "@example.com");
        user.setPassword("not-used");
        user.setRole(Role.USER);
        user.setEmailVerified(true);
        return users.save(user);
    }

    private Product product(String name) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal("1000.00"));
        product.setStock(5);
        product.setCategory(bazar);
        return products.save(product);
    }
}
