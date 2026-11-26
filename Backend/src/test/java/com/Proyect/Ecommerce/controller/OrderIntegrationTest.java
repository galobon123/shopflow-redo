package com.Proyect.Ecommerce.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.Proyect.Ecommerce.IntegrationTest;
import com.Proyect.Ecommerce.model.Category;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.model.Role;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.repository.CategoryRepository;
import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

/** HU-17 · POST /api/orders: one order per purchase attempt (Idempotency-Key) and one checkout at a time. */
@IntegrationTest
@AutoConfigureMockMvc
class OrderIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    /** Real Redis; one test makes it fail right after the order is committed. */
    @MockitoSpyBean private StringRedisTemplate redis;
    @Autowired private UserRepository users;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;

    private User lucia;
    private long address;
    private Product taza;

    @BeforeEach
    void emptyStore() {
        jdbc.execute("TRUNCATE reviews, order_items, orders, address, wishlist, product_images, products, categories, users RESTART IDENTITY CASCADE");
        for (String pattern : List.of("cart:*", "order:*")) {
            var keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        }
        lucia = customer("Lucia");
        address = addressOf(lucia);
        taza = product("Taza", "1000.00", 5);
    }

    @Test
    void theFirstTimeCreatesTheOrderWaitingForItsPayment() throws Exception {
        putInCart(lucia, taza, 2);

        mvc.perform(buy(lucia, UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.total").value(2000.0))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        assertThat(stockOf(taza)).isEqualTo(3);
        assertThat(redis.hasKey(cartKey(lucia))).isFalse();
        assertThat(redis.hasKey(lockKey(lucia))).as("the lock is let go").isFalse();
    }

    /** The total is the backend's: whatever the browser sends is ignored. */
    @Test
    void aTotalSentByTheBrowserIsIgnored() throws Exception {
        putInCart(lucia, taza, 2);

        mvc.perform(post("/api/orders").with(as(lucia))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\": %d, \"total\": 1, \"unitPrice\": 1}".formatted(address)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(2000.0))
                .andExpect(jsonPath("$.items[0].unitPrice").value(1000.0));
    }

    @Test
    void withoutTheKeyOrWithOneThatIsNotAUuidNothingIsBought() throws Exception {
        putInCart(lucia, taza, 1);

        mvc.perform(post("/api/orders").with(as(lucia))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\": %d}".formatted(address)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud inválida"))
                .andExpect(jsonPath("$.detail").value("Falta el encabezado Idempotency-Key."));
        mvc.perform(buy(lucia, "compra-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El encabezado Idempotency-Key tiene que ser un UUID."));

        assertThat(stockOf(taza)).isEqualTo(5);
        assertThat(orderCount()).isZero();
    }

    /** Escenario: Confirmo dos veces la misma compra (one after the other: the browser retries). */
    @Test
    void theSamePurchaseSentTwiceGivesTheSameOrderAndTakesTheStockOnce() throws Exception {
        putInCart(lucia, taza, 1);
        String key = UUID.randomUUID().toString();

        long first = idOf(mvc.perform(buy(lucia, key)).andExpect(status().isCreated()).andReturn().getResponse());
        long again = idOf(mvc.perform(buy(lucia, key)).andExpect(status().isOk()).andReturn().getResponse());

        assertThat(again).isEqualTo(first);
        assertThat(stockOf(taza)).isEqualTo(4);
        assertThat(orderCount()).isEqualTo(1);
        assertThat(redis.getExpire(idemKey(lucia, key))).as("remembered for a day").isGreaterThan(23 * 3600);
    }

    /**
     * Escenario: Confirmo dos veces la misma compra (double click: both at once). Only one creates the order;
     * the other either finds it done (200) or still running (409 "Tu compra se está procesando"), and trying
     * again then gives the same order.
     */
    @Test
    void theSamePurchaseSentTwiceAtOnceMakesOneOrder() throws Exception {
        putInCart(lucia, taza, 1);
        String key = UUID.randomUUID().toString();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<MockHttpServletResponse>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(buy(lucia, key)).andReturn().getResponse();
                }));
            }
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<MockHttpServletResponse> result : results) {
                MockHttpServletResponse response = result.get();
                statuses.add(response.getStatus());
                if (response.getStatus() == 409) {
                    assertThat((String) JsonPath.read(response.getContentAsString(), "$.detail"))
                            .isEqualTo("Tu compra se está procesando.");
                }
            }
            assertThat(statuses).containsOnlyOnce(201);
            assertThat(statuses).containsAnyOf(200, 409);
        } finally {
            pool.shutdownNow();
        }

        long created = jdbc.queryForObject("SELECT id FROM orders", Long.class);
        long retried = idOf(mvc.perform(buy(lucia, key)).andExpect(status().isOk()).andReturn().getResponse());
        assertThat(retried).isEqualTo(created);
        assertThat(stockOf(taza)).isEqualTo(4);
        assertThat(orderCount()).isEqualTo(1);
    }

    /** A key already used names that purchase: with another cart it still answers the original order. */
    @Test
    void aKeyUsedAgainWithAnotherCartGivesTheOriginalOrder() throws Exception {
        putInCart(lucia, taza, 1);
        String key = UUID.randomUUID().toString();
        long first = idOf(mvc.perform(buy(lucia, key)).andExpect(status().isCreated()).andReturn().getResponse());

        putInCart(lucia, taza, 3);
        mvc.perform(buy(lucia, key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first))
                .andExpect(jsonPath("$.items[0].quantity").value(1));

        assertThat(stockOf(taza)).isEqualTo(4);
        assertThat(redis.opsForHash().get(cartKey(lucia), taza.getId().toString())).isEqualTo("3");
    }

    /** Another customer's key is not this one's: the same value buys separately. */
    @Test
    void theKeyBelongsToTheCustomer() throws Exception {
        User martin = customer("Martin");
        long martinsAddress = addressOf(martin);
        putInCart(lucia, taza, 1);
        putInCart(martin, taza, 1);
        String key = UUID.randomUUID().toString();

        mvc.perform(buy(lucia, key)).andExpect(status().isCreated());
        mvc.perform(post("/api/orders").with(as(martin))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\": %d}".formatted(martinsAddress)))
                .andExpect(status().isCreated());

        assertThat(orderCount()).isEqualTo(2);
    }

    /** A failed attempt frees its key: once the cart is fixed, the same purchase goes through. */
    @Test
    void afterRunningOutOfStockTheSameKeyCanBuyOnceTheCartIsFixed() throws Exception {
        putInCart(lucia, taza, 6);
        String key = UUID.randomUUID().toString();

        mvc.perform(buy(lucia, key))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Sin stock de Taza."));
        assertThat(redis.hasKey(idemKey(lucia, key))).isFalse();
        assertThat(redis.hasKey(lockKey(lucia))).isFalse();

        putInCart(lucia, taza, 5);
        mvc.perform(buy(lucia, key)).andExpect(status().isCreated());
        assertThat(stockOf(taza)).isZero();
    }

    /** Escenario: Falla al guardar la orden (through the API): nothing changes and the attempt can be retried. */
    @Test
    void whenTheDatabaseRefusesTheOrderNothingChangesAndTheKeyIsFreed() throws Exception {
        putInCart(lucia, taza, 2);
        String key = UUID.randomUUID().toString();

        jdbc.execute("CREATE OR REPLACE FUNCTION refuse_order() RETURNS trigger LANGUAGE plpgsql AS "
                + "$$ BEGIN RAISE EXCEPTION 'orders are refused in this test'; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER refuse_order AFTER INSERT ON orders "
                + "DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION refuse_order()");
        try {
            mvc.perform(buy(lucia, key)).andExpect(status().isInternalServerError());
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS refuse_order ON orders");
            jdbc.execute("DROP FUNCTION IF EXISTS refuse_order()");
        }

        assertThat(stockOf(taza)).isEqualTo(5);
        assertThat(redis.opsForHash().get(cartKey(lucia), taza.getId().toString())).isEqualTo("2");
        assertThat(redis.hasKey(idemKey(lucia, key))).isFalse();
        assertThat(redis.hasKey(lockKey(lucia))).isFalse();

        mvc.perform(buy(lucia, key)).andExpect(status().isCreated());
        assertThat(stockOf(taza)).isEqualTo(3);
    }

    /** Two tabs, two keys: while one checkout runs, the other is told to wait and buys nothing. */
    @Test
    void whileAnotherCheckoutRunsTheCustomerIsToldToWait() throws Exception {
        putInCart(lucia, taza, 1);
        redis.opsForValue().set(lockKey(lucia), "la-otra-pestana");
        String key = UUID.randomUUID().toString();

        mvc.perform(buy(lucia, key))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Tu compra se está procesando."))
                .andExpect(jsonPath("$.reason").value("order-in-progress"));

        assertThat(redis.opsForValue().get(lockKey(lucia))).as("only its owner lets the lock go")
                .isEqualTo("la-otra-pestana");
        assertThat(redis.hasKey(idemKey(lucia, key))).as("this attempt can be retried").isFalse();
        assertThat(stockOf(taza)).isEqualTo(5);

        redis.delete(lockKey(lucia));
        mvc.perform(buy(lucia, key)).andExpect(status().isCreated());
    }

    /** A purchase still running answers 409 to a repeat, which doesn't buy again. */
    @Test
    void aRepeatWhileTheFirstIsStillRunningIsToldToWait() throws Exception {
        putInCart(lucia, taza, 1);
        String key = UUID.randomUUID().toString();
        redis.opsForValue().set(idemKey(lucia, key), "processing:another-request");

        mvc.perform(buy(lucia, key))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Tu compra se está procesando."));

        assertThat(redis.opsForValue().get(idemKey(lucia, key))).isEqualTo("processing:another-request");
        assertThat(stockOf(taza)).isEqualTo(5);
    }

    /**
     * Redis goes down right after the order is committed: the key can't be marked done nor the lock let go.
     * The purchase is made, so the customer gets it (201), not an error that invites buying again.
     */
    @Test
    void aRedisFailureAfterTheOrderIsSavedStillAnswersTheOrder() throws Exception {
        putInCart(lucia, taza, 1);
        String key = UUID.randomUUID().toString();
        RedisConnectionFailureException down = new RedisConnectionFailureException("Redis is down in this test");
        ValueOperations<String, String> values = spy(redis.opsForValue());
        doThrow(down).when(values).set(startsWith("order:idem:"), startsWith("done:"), any(Duration.class));
        doReturn(values).when(redis).opsForValue();
        doThrow(down).when(redis).execute(any(RedisScript.class), eq(List.of(lockKey(lucia))), any());

        long id = idOf(mvc.perform(buy(lucia, key))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDIENTE_PAGO"))
                .andReturn().getResponse());

        assertThat(id).isEqualTo(jdbc.queryForObject("SELECT id FROM orders", Long.class));
        assertThat(stockOf(taza)).isEqualTo(4);
        assertThat(redis.getExpire(lockKey(lucia))).as("the lock expires on its own").isBetween(1L, 30L);
    }

    // ── helpers ──────────────────────────────────────────────

    private MockHttpServletRequestBuilder buy(User user, String key) {
        return post("/api/orders").with(as(user))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\": %d}".formatted(address));
    }

    private static long idOf(MockHttpServletResponse response) throws Exception {
        return ((Number) JsonPath.read(response.getContentAsString(), "$.id")).longValue();
    }

    private static String idemKey(User user, String key) {
        return "order:idem:" + user.getId() + ":" + key;
    }

    private static String lockKey(User user) {
        return "order:lock:" + user.getId();
    }

    private static String cartKey(User user) {
        return "cart:" + user.getId();
    }

    private void putInCart(User user, Product product, int quantity) {
        redis.opsForHash().put(cartKey(user), product.getId().toString(), String.valueOf(quantity));
    }

    private int stockOf(Product product) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, product.getId());
    }

    private int orderCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
    }

    private long addressOf(User owner) {
        Integer rosario = jdbc.queryForObject("SELECT id FROM city WHERE name = 'Rosario'", Integer.class);
        return jdbc.queryForObject("INSERT INTO address (first_name, last_name, address, city_id, zip_code, created_by) "
                + "VALUES (?, 'Test', 'San Martín 123', ?, '2000', ?) RETURNING id", Long.class,
                owner.getName(), rosario, owner.getId());
    }

    private Product product(String name, String price, int stock) {
        Category category = new Category();
        category.setName("Bazar " + name);
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setCategory(categories.save(category));
        return products.save(product);
    }

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
}
