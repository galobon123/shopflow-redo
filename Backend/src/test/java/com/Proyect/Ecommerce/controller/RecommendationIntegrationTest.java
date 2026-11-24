package com.Proyect.Ecommerce.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.stubbing.Answer;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.Proyect.Ecommerce.IntegrationTest;
import com.Proyect.Ecommerce.model.Category;
import com.Proyect.Ecommerce.model.OrderStatus;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.model.Role;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.repository.CategoryRepository;
import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.repository.UserRepository;
import com.Proyect.Ecommerce.search.ProductSearchIndex;
import com.Proyect.Ecommerce.service.GraphBackfill;
import com.Proyect.Ecommerce.service.GraphSync;
import com.Proyect.Ecommerce.service.OrderService;
import com.Proyect.Ecommerce.service.ProductChanged;
import com.Proyect.Ecommerce.service.RecommendationService;

/**
 * HU-15 · Recomendaciones por compras y wishlist, against real Postgres, Redis and Neo4j.
 * "Neo4j caído" is the Neo4j gateway (RecommendationService) failing, as in WishlistIntegrationTest.
 */
@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RecommendationIntegrationTest {

    private static final String PENDING = "neo4j:sync:pending";
    private static final String FAILED = "neo4j:sync:failed";
    private static final String NEO4J_DOWN = "recs:neo4j-down";
    private static final Duration SYNC = Duration.ofSeconds(10);

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private UserRepository users;
    @Autowired private StringRedisTemplate redis;
    @Autowired private Driver neo4j;
    @Autowired private GraphSync sync;
    @Autowired private GraphBackfill backfill;
    @Autowired private OrderService orders;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private RecommendationService recommendations;

    private Category bazar;
    private Category cocina;
    private User ana;

    @BeforeEach
    void emptyStore() {
        jdbc.execute("TRUNCATE reviews, order_items, orders, address, wishlist, product_images, products, categories, users RESTART IDENTITY CASCADE");
        ProductSearchIndex.productsChanged();
        try (var session = neo4j.session()) {
            session.run("MATCH (n) DETACH DELETE n").consume();
        }
        redis.delete(List.of(PENDING, FAILED));
        var cached = redis.keys("recs:*");
        if (cached != null && !cached.isEmpty()) redis.delete(cached);
        bazar = category("Bazar");
        cocina = category("Cocina");
        ana = customer("Ana");
    }

    /** Escenario: Recomendaciones por compras de otros. */
    @Test
    void theProductPageShowsWhatOtherBuyersOfItAlsoBought() throws Exception {
        Product taza = product("Taza");
        Product plato = product("Plato");
        Product mate = product("Mate");
        buys(ana, taza);
        for (String name : List.of("Bruno", "Carla", "Dario")) {
            buys(customer(name), taza, plato);
        }
        buys(customer("Emilia"), taza, mate);
        await().atMost(SYNC).until(() -> purchases() == 9);

        mvc.perform(get("/api/products/{id}/also-bought", taza.getId())) // a guest sees it too
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Plato", "Mate")));
    }

    @Test
    void alsoBoughtLeavesOutInactiveAndSoldOutProducts() throws Exception {
        Product taza = product("Taza");
        Product plato = product("Plato");
        Product vaso = product("Vaso");
        Product jarra = product("Jarra");
        buys(customer("Bruno"), taza, plato, vaso, jarra);
        await().atMost(SYNC).until(() -> purchases() == 4);
        jdbc.update("UPDATE products SET active = false WHERE id = ?", vaso.getId());
        jdbc.update("UPDATE products SET stock = 0 WHERE id = ?", jarra.getId());

        mvc.perform(get("/api/products/{id}/also-bought", taza.getId()))
                .andExpect(jsonPath("$[*].name", contains("Plato")));
    }

    /** Escenario: Cliente sin historial. Best sellers count units in orders paid or later only. */
    @Test
    void aNewCustomerGetsBestSellers() throws Exception {
        Product taza = product("Taza");
        Product plato = product("Plato");
        Product mate = product("Mate");
        Product vaso = product("Vaso");
        Product jarra = product("Jarra");
        Product termo = product("Termo");
        User bruno = customer("Bruno");
        order(bruno, OrderStatus.ENTREGADA, 3, plato);
        order(bruno, OrderStatus.PAGADA, 2, plato, taza);
        order(bruno, OrderStatus.PENDIENTE_PAGO, 10, mate);
        order(bruno, OrderStatus.CANCELADA, 10, mate);
        order(bruno, OrderStatus.ENVIADA, 20, vaso, jarra);
        jdbc.update("UPDATE products SET active = false WHERE id = ?", vaso.getId());
        jdbc.update("UPDATE products SET stock = 0 WHERE id = ?", jarra.getId());

        mvc.perform(get("/api/recommendations").with(as(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections", hasSize(1)))
                .andExpect(jsonPath("$.sections[0].type").value("BEST_SELLERS"))
                // Plato 5, Taza 2, then the unsold ones newest first.
                .andExpect(jsonPath("$.sections[0].products[*].name", contains("Plato", "Taza", "Termo", "Mate")));
    }

    /** Escenario: Neo4j caído. */
    @Test
    void withNeo4jDownAndNoCacheItAnswersBestSellersAndLogsTheError(CapturedOutput output) throws Exception {
        Product taza = product("Taza");
        Product plato = product("Plato");
        buys(ana, taza);
        buys(customer("Bruno"), taza, plato);
        await().atMost(SYNC).until(() -> purchases() == 3);
        doThrow(new TransientDataAccessResourceException("Neo4j no responde"))
                .when(recommendations).boughtByBuyersLikeYou(anyLong(), anyInt());

        mvc.perform(get("/api/recommendations").with(as(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections", hasSize(1)))
                .andExpect(jsonPath("$.sections[0].type").value("BEST_SELLERS"));

        assertThat(output).contains("WARN").contains("Neo4j failed for the recommendations of user " + ana.getId())
                .contains("Neo4j no responde");
        assertThat(output).doesNotContain("at com.Proyect.Ecommerce.service.RecommendedProducts"); // one line, no trace
        // Not 10 minutes of best sellers: the personalized ones come back a minute after Neo4j does.
        assertThat(redis.hasKey("recs:" + ana.getId())).isFalse();
        assertThat(redis.getExpire(NEO4J_DOWN, TimeUnit.SECONDS)).isBetween(1L, 60L);

        reset(recommendations);
        redis.delete(NEO4J_DOWN); // that minute went by
        mvc.perform(get("/api/recommendations").with(as(ana)))
                .andExpect(jsonPath("$.sections[0].type").value("FOR_YOU"))
                .andExpect(jsonPath("$.sections[0].products[*].name", contains("Plato")));
    }

    /** While Neo4j is marked down, product pages and customers don't wait for it: one failure, then no calls. */
    @Test
    void onceNeo4jFailedNobodyWaitsForItThatMinute(CapturedOutput output) throws Exception {
        Product taza = product("Taza");
        doThrow(new TransientDataAccessResourceException("Neo4j no responde"))
                .when(recommendations).alsoBought(anyLong(), anyInt());

        for (int page = 0; page < 3; page++) {
            mvc.perform(get("/api/products/{id}/also-bought", taza.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
        mvc.perform(get("/api/recommendations").with(as(ana)))
                .andExpect(jsonPath("$.sections[0].type").value("BEST_SELLERS"));

        verify(recommendations, times(1)).alsoBought(anyLong(), anyInt());
        verify(recommendations, never()).boughtByBuyersLikeYou(anyLong(), anyInt());
        assertThat(output).contains("Neo4j failed for 'also bought' of product " + taza.getId());
        assertThat(redis.getExpire(NEO4J_DOWN, TimeUnit.SECONDS)).isBetween(1L, 60L);
    }

    /** Neo4j is asked with no Postgres transaction open, so a slow Neo4j holds no Postgres connection. */
    @Test
    void neo4jIsAskedWithoutAPostgresTransaction() throws Exception {
        Product taza = product("Taza");
        List<Boolean> inTransaction = new ArrayList<>();
        Answer<Object> recordingTransaction = call -> {
            inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
            return call.callRealMethod();
        };
        doAnswer(recordingTransaction).when(recommendations).alsoBought(anyLong(), anyInt());
        doAnswer(recordingTransaction).when(recommendations).boughtByBuyersLikeYou(anyLong(), anyInt());
        doAnswer(recordingTransaction).when(recommendations).likeYourWishlist(anyLong(), anyInt());

        mvc.perform(get("/api/products/{id}/also-bought", taza.getId())).andExpect(status().isOk());
        mvc.perform(get("/api/recommendations").with(as(ana))).andExpect(status().isOk());

        assertThat(inTransaction).containsExactly(false, false, false);
    }

    /** "Recomendados para vos" from purchases and "Por tus favoritos" from its categories, minus what's bought. */
    @Test
    void aCustomerWithHistoryGetsPersonalizedBlocksCachedForTenMinutes() throws Exception {
        Product taza = product("Taza");
        Product plato = product("Plato");
        Product mate = product("Mate");
        product("Termo");
        product("Sartén", cocina);
        order(ana, OrderStatus.ENTREGADA, 1, taza);
        order(customer("Bruno"), OrderStatus.PAGADA, 1, taza, plato, mate);
        jdbc.update("INSERT INTO wishlist (user_id, product_id, created_at) VALUES (?, ?, now())", ana.getId(), mate.getId());
        backfill.backfill();

        mvc.perform(get("/api/recommendations").with(as(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections", hasSize(2)))
                .andExpect(jsonPath("$.sections[0].type").value("FOR_YOU"))
                .andExpect(jsonPath("$.sections[0].products[*].name", contains("Plato"))) // Mate is saved already
                .andExpect(jsonPath("$.sections[1].type").value("WISHLIST"))
                // Bazar, without Mate (saved), Taza (bought) or Plato (already above).
                .andExpect(jsonPath("$.sections[1].products[*].name", contains("Termo")));

        assertThat(redis.getExpire("recs:" + ana.getId(), TimeUnit.SECONDS)).isBetween(590L, 600L);
    }

    /** Paying creates PURCHASED {qty, at} and forgets the cached recommendations once it's in Neo4j. */
    @Test
    void payingAnOrderPutsItInNeo4jAndForgetsTheCache() throws Exception {
        Product taza = product("Taza");
        mvc.perform(get("/api/recommendations").with(as(ana)));
        assertThat(redis.hasKey("recs:" + ana.getId())).isTrue();

        long order = order(ana, OrderStatus.PENDIENTE_PAGO, 2, taza);
        orders.markPaid(order);

        await().atMost(SYNC).until(() -> purchases() == 1);
        try (var session = neo4j.session()) {
            var purchase = session.run("MATCH (:User {id: $user})-[r:PURCHASED]->(:Product {id: $product}) RETURN r",
                    Map.of("user", ana.getId(), "product", taza.getId())).single().get("r").asRelationship();
            assertThat(purchase.get("qty").asLong()).isEqualTo(2);
            assertThat(purchase.get("orderId").asLong()).isEqualTo(order);
            assertThat(purchase.get("at").isNull()).isFalse();
        }
        await().atMost(SYNC).until(() -> !redis.hasKey("recs:" + ana.getId()));
    }

    @Test
    void withNeo4jDownAPaidOrderWaitsInTheQueueUntilItComesBack() throws Exception {
        Product taza = product("Taza");
        doThrow(new TransientDataAccessResourceException("Neo4j no responde")).when(recommendations).syncPurchases(any());
        long order = order(ana, OrderStatus.PENDIENTE_PAGO, 1, taza);

        orders.markPaid(order);

        await().atMost(SYNC).until(() -> pending().size() == 1);
        assertThat(pending()).containsExactly("order:" + order + ":0");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order)).isEqualTo("PAGADA");

        reset(recommendations); // Neo4j is back
        sync.retryPending();

        assertThat(purchases()).isEqualTo(1);
        assertThat(pending()).isEmpty();
    }

    /** HU-18 will cancel paid orders: the sync follows Postgres and takes its purchases out. */
    @Test
    void anOrderThatIsNoLongerPaidLeavesTheGraph() throws Exception {
        Product taza = product("Taza");
        long order = order(ana, OrderStatus.PENDIENTE_PAGO, 1, taza);
        orders.markPaid(order);
        await().atMost(SYNC).until(() -> purchases() == 1);

        jdbc.update("UPDATE orders SET status = 'CANCELADA' WHERE id = ?", order);
        redis.opsForList().rightPush(PENDING, "order:" + order + ":0");
        sync.retryPending();

        assertThat(purchases()).isZero();
    }

    /** OPS: backfill. Only orders paid or later, and running it again changes nothing. */
    @Test
    void theBackfillLoadsProductsWishlistsAndPaidOrdersAndCanRunTwice() throws Exception {
        List<Product> five = List.of(product("Taza"), product("Plato"), product("Mate"), product("Vaso"), product("Termo"));
        List<OrderStatus> statuses = List.of(OrderStatus.PAGADA, OrderStatus.ENVIADA, OrderStatus.ENTREGADA,
                OrderStatus.PENDIENTE_PAGO, OrderStatus.CANCELADA);
        for (int i = 0; i < 5; i++) {
            order(ana, statuses.get(i), 1, five.get(i));
        }
        jdbc.update("INSERT INTO wishlist (user_id, product_id, created_at) VALUES (?, ?, now())", ana.getId(), five.get(4).getId());

        backfill.backfill();
        backfill.backfill();

        assertThat(purchases()).isEqualTo(3);
        assertThat(count("MATCH (:User)-[r:WISHLISTED]->(:Product) RETURN count(r) AS n")).isEqualTo(1);
        assertThat(count("MATCH (p:Product) RETURN count(p) AS n")).isEqualTo(5);
        assertThat(count("MATCH (:Product)-[r:BELONGS_TO]->(:Category) RETURN count(r) AS n")).isEqualTo(5);
    }

    /** A product created by the admin gets its node; an edit (HU-21) moves its name, price and category. */
    @Test
    void productChangesReachTheirNode() throws Exception {
        mvc.perform(post("/api/products").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Taza\",\"price\":1000,\"stock\":5,\"categoryId\":" + bazar.getId() + "}"))
                .andExpect(status().isCreated());
        await().atMost(SYNC).until(() -> count("MATCH (p:Product {name: 'Taza', price: 1000.0})-[:BELONGS_TO]->(:Category {name: 'Bazar'}) RETURN count(p) AS n") == 1);

        Long id = products.findAll().getFirst().getId();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Product taza = products.findById(id).orElseThrow();
            taza.setName("Taza negra");
            taza.setPrice(new BigDecimal("1500.00"));
            taza.setCategory(cocina);
            events.publishEvent(new ProductChanged(id));
        });

        await().atMost(SYNC).until(() -> count("MATCH (p:Product {name: 'Taza negra', price: 1500.0})-[:BELONGS_TO]->(:Category {name: 'Cocina'}) RETURN count(p) AS n") == 1);
        assertThat(count("MATCH (:Product)-[r:BELONGS_TO]->(:Category) RETURN count(r) AS n")).isEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────

    /** An order paid through OrderService, so its PURCHASED go to Neo4j after the commit like a real one. */
    private void buys(User user, Product... items) {
        orders.markPaid(order(user, OrderStatus.PENDIENTE_PAGO, 1, items));
    }

    private long order(User user, OrderStatus status, int quantity, Product... items) {
        Long address = jdbc.queryForObject("""
                INSERT INTO address (first_name, last_name, address, city_id, zip_code, created_by)
                VALUES ('Test', 'Test', 'San Martín 123', (SELECT min(id) FROM city WHERE name = 'Rosario'), '2000', ?)
                RETURNING id""", Long.class, user.getId());
        Long order = jdbc.queryForObject("""
                INSERT INTO orders (created_at, user_id, total, address_id, status) VALUES (now(), ?, 0, ?, ?)
                RETURNING id""", Long.class, user.getId(), address, status.name());
        for (Product item : items) {
            jdbc.update("INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, 1000)",
                    order, item.getId(), quantity);
        }
        return order;
    }

    private List<String> pending() {
        return redis.opsForList().range(PENDING, 0, -1);
    }

    private long purchases() {
        return count("MATCH (:User)-[r:PURCHASED]->(:Product) RETURN count(r) AS n");
    }

    private long count(String cypher) {
        try (var session = neo4j.session()) {
            return session.run(cypher).single().get("n").asLong();
        }
    }

    /** Signed in the way JwtAuthFilter leaves it: the user id travels as the credentials. */
    private static RequestPostProcessor as(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                user.getEmail(), user.getId(), List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private static RequestPostProcessor admin() {
        return authentication(new UsernamePasswordAuthenticationToken(
                "admin@example.com", 0L, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private Category category(String name) {
        Category category = new Category();
        category.setName(name);
        return categories.save(category);
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
        return product(name, bazar);
    }

    private Product product(String name, Category category) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal("1000.00"));
        product.setStock(5);
        product.setCategory(category);
        return products.save(product);
    }
}
