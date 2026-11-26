package com.Proyect.Ecommerce.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.Proyect.Ecommerce.FakeMercadoPago;
import com.Proyect.Ecommerce.IntegrationTest;
import com.Proyect.Ecommerce.RecordingMailSender;
import com.Proyect.Ecommerce.dto.order.OrderRequestDTO;
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
import com.Proyect.Ecommerce.service.MailQueue;
import com.Proyect.Ecommerce.service.OrderExpiry;
import com.Proyect.Ecommerce.service.OrderService;
import com.jayway.jsonpath.JsonPath;

/**
 * HU-18 · Paying with Mercado Pago, end to end against real Postgres, Redis and Neo4j, with Mercado Pago's API
 * played by {@link FakeMercadoPago}: the preference for an order, and the webhook that pays (or not) the order.
 */
@IntegrationTest
@AutoConfigureMockMvc
class PaymentIntegrationTest {

    private static final String SECRET = "test-webhook-secret";
    private static final String LUCIA = "lucia@example.com";
    private static final Duration SYNC = Duration.ofSeconds(10);

    @Autowired private MockMvc mvc;
    @Autowired private FakeMercadoPago mercadoPago;
    @Autowired private OrderService orders;
    @Autowired private OrderExpiry expiry;
    @Autowired private MailQueue mailQueue;
    @Autowired private RecordingMailSender mail;
    @Autowired private UserRepository users;
    @Autowired private AddressRepository addresses;
    @Autowired private CityRepository cities;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Driver neo4j;

    private User lucia;
    private Product taza;
    private long orderId;

    @BeforeEach
    void anOrderWaitingForItsPayment() {
        jdbc.execute("TRUNCATE reviews, order_items, orders, address, wishlist, product_images, products, categories, users RESTART IDENTITY CASCADE");
        for (String pattern : List.of("cart:*", "order:*", "mp:event:*", "mail:*", "neo4j:*", "recs:*")) {
            var keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        }
        try (var session = neo4j.session()) {
            session.run("MATCH (n) DETACH DELETE n").consume();
        }
        mercadoPago.clear();
        mail.clear();

        Category category = new Category();
        category.setName("Bazar");
        category = categories.save(category);
        taza = new Product();
        taza.setName("Taza");
        taza.setPrice(new BigDecimal("1000.00"));
        taza.setStock(5);
        taza.setCategory(category);
        taza = products.save(taza);

        lucia = customer("Lucía", LUCIA);
        redis.opsForHash().put("cart:" + lucia.getId(), taza.getId().toString(), "2");
        OrderRequestDTO request = new OrderRequestDTO();
        request.setAddressId(addressOf(lucia).getId());
        orderId = orders.createOrder(lucia.getId(), request).getId();
    }

    // ── The preference ───────────────────────────────────────

    /** The order's items at the price kept in it, its id as external_reference, the way back and its deadline. */
    @Test
    void payingCreatesAPreferenceWithTheOrderAndSendsToItsSandboxCheckout() throws Exception {
        mvc.perform(post("/api/orders/{id}/payment", orderId).with(as(lucia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value(FakeMercadoPago.sandboxUrl(1)));

        List<FakeMercadoPago.Request> sent = mercadoPago.requests("POST", "/checkout/preferences");
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().authorization()).isEqualTo("Bearer TEST-access-token");
        String body = sent.getFirst().body();
        assertThat((Object) JsonPath.read(body, "$.items[0].id")).isEqualTo(taza.getId().toString());
        assertThat((Object) JsonPath.read(body, "$.items[0].title")).isEqualTo("Taza");
        assertThat((Object) JsonPath.read(body, "$.items[0].quantity")).isEqualTo(2);
        assertThat(new BigDecimal(JsonPath.read(body, "$.items[0].unit_price").toString())).isEqualByComparingTo("1000");
        assertThat((Object) JsonPath.read(body, "$.items[0].currency_id")).isEqualTo("ARS");
        assertThat((Object) JsonPath.read(body, "$.external_reference")).isEqualTo(String.valueOf(orderId));
        String result = "http://localhost:4200/checkout/result/" + orderId;
        assertThat((Object) JsonPath.read(body, "$.back_urls.success")).isEqualTo(result + "/approved");
        assertThat((Object) JsonPath.read(body, "$.back_urls.pending")).isEqualTo(result + "/pending");
        assertThat((Object) JsonPath.read(body, "$.back_urls.failure")).isEqualTo(result + "/rejected");
        // The store is on localhost here: Mercado Pago would refuse auto_return (PaymentPreferenceTest has the rest).
        assertThat(JsonPath.<Map<String, Object>>read(body, "$")).doesNotContainKey("auto_return");
        // Signed Webhooks only, not the old IPN as well.
        assertThat((Object) JsonPath.read(body, "$.notification_url"))
                .isEqualTo("https://api.shopflow.test/api/payments/webhook?source_news=webhooks");
        // Approved or rejected at once, never days in a manual review.
        assertThat((Object) JsonPath.read(body, "$.binary_mode")).isEqualTo(true);
        assertThat((Object) JsonPath.read(body, "$.expires")).isEqualTo(true);
        assertThat(JsonPath.<List<String>>read(body, "$.payment_methods.excluded_payment_types[*].id")).containsExactly("ticket", "atm");

        // Mercado Pago stops taking payments when the order expires: the same instant, in Argentina's time.
        String deadline = JsonPath.read(body, "$.expiration_date_to");
        assertThat(deadline).endsWith("-03:00");
        Instant expiresAt = jdbc.queryForObject("SELECT expires_at FROM orders WHERE id = ?", LocalDateTime.class, orderId)
                .atZone(ZoneId.systemDefault()).toInstant();
        assertThat(Duration.between(OffsetDateTime.parse(deadline).toInstant(), expiresAt).abs().toMillis()).isLessThan(1);
    }

    /** One preference per order: trying again goes to the same checkout. */
    @Test
    void tryingAgainReusesThePreference() throws Exception {
        mvc.perform(post("/api/orders/{id}/payment", orderId).with(as(lucia))).andExpect(status().isOk());
        mvc.perform(post("/api/orders/{id}/payment", orderId).with(as(lucia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value(FakeMercadoPago.sandboxUrl(1)));

        assertThat(mercadoPago.requests("POST", "/checkout/preferences")).hasSize(1);
    }

    @Test
    void someoneElsesOrderLooksMissing() throws Exception {
        User martin = customer("Martín", "martin@example.com");

        mvc.perform(post("/api/orders/{id}/payment", orderId).with(as(martin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pedido no encontrado."));
        assertThat(mercadoPago.requests("POST", "/checkout/preferences")).isEmpty();
    }

    @Test
    void onlyAnOrderStillWaitingForItsPaymentAndOnTimeCanBePaid() throws Exception {
        jdbc.update("UPDATE orders SET expires_at = ? WHERE id = ?", LocalDateTime.now().minusSeconds(1), orderId);
        mvc.perform(post("/api/orders/{id}/payment", orderId).with(as(lucia)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Se venció el plazo para pagar este pedido."));

        jdbc.update("UPDATE orders SET status = 'PAGADA', expires_at = ? WHERE id = ?", LocalDateTime.now().plusMinutes(5), orderId);
        mvc.perform(post("/api/orders/{id}/payment", orderId).with(as(lucia)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este pedido ya no está esperando el pago."));

        assertThat(mercadoPago.requests("POST", "/checkout/preferences")).isEmpty();
    }

    @Test
    void withMercadoPagoDownTheOrderStaysAndTheNextTryWorks() throws Exception {
        mercadoPago.goDown();
        mvc.perform(post("/api/orders/{id}/payment", orderId).with(as(lucia)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Servicio no disponible"))
                .andExpect(jsonPath("$.detail").value("No pudimos conectar con Mercado Pago. Probá de nuevo en unos minutos."));
        assertThat(statusOfOrder()).isEqualTo("PENDIENTE_PAGO");

        mercadoPago.comeBack();
        mvc.perform(post("/api/orders/{id}/payment", orderId).with(as(lucia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value(FakeMercadoPago.sandboxUrl(1)));
    }

    // ── The webhook ──────────────────────────────────────────

    /** Escenario: Pago aprobado. PAGADA, the confirmation email and PURCHASED in Neo4j. */
    @Test
    void anApprovedPaymentPaysTheOrderSendsTheEmailAndReachesNeo4j() throws Exception {
        mercadoPago.payment(9001, "approved", orderId, "2000.00", "ARS");

        notifyPayment("123", 9001).andExpect(status().isOk());

        assertThat(statusOfOrder()).isEqualTo("PAGADA");
        assertThat(jdbc.queryForObject("SELECT mp_payment_id FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("9001");
        assertThat(mercadoPago.requests("GET", "/v1/payments/9001").getFirst().authorization())
                .isEqualTo("Bearer TEST-access-token");

        mailQueue.deliverDue(Instant.now());
        assertThat(mail.to(LUCIA, "Recibimos el pago de tu pedido #" + orderId)).hasSize(1);

        await().atMost(SYNC).until(() -> purchases() == 1);
        try (var session = neo4j.session()) {
            long qty = session.run("MATCH (:User {id: $user})-[r:PURCHASED]->(:Product {id: $product}) RETURN r.qty AS qty",
                    Map.of("user", lucia.getId(), "product", taza.getId())).single().get("qty").asLong();
            assertThat(qty).isEqualTo(2);
        }
    }

    /** Escenario: Webhook duplicado. The same notice again answers 200 and changes nothing, nor asks Mercado Pago. */
    @Test
    void theSameNoticeTwiceIsHandledOnce() throws Exception {
        mercadoPago.payment(9001, "approved", orderId, "2000.00", "ARS");
        notifyPayment("123", 9001).andExpect(status().isOk());
        assertThat(redis.getExpire("mp:event:123")).as("remembered for a day").isGreaterThan(23 * 3600);

        mercadoPago.payment(9001, "refunded", orderId, "2000.00", "ARS"); // would cancel it if handled again
        notifyPayment("123", 9001).andExpect(status().isOk());

        assertThat(statusOfOrder()).isEqualTo("PAGADA");
        assertThat(mercadoPago.requests("GET", "/v1/payments/")).hasSize(1);
        mailQueue.deliverDue(Instant.now());
        assertThat(mail.to(LUCIA, "Recibimos el pago de tu pedido #" + orderId)).hasSize(1);
    }

    /** Escenario: Webhook falso. 401, the order doesn't change and Mercado Pago isn't even asked. */
    @Test
    void aNoticeWithABadOrMissingSignatureIsRefused() throws Exception {
        mercadoPago.payment(9001, "approved", orderId, "2000.00", "ARS");

        mvc.perform(webhook("123", "9001", "ts=1742505638683,v1=" + "0".repeat(64), "req-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Firma inválida"));
        mvc.perform(webhook("123", "9001", null, "req-1"))
                .andExpect(status().isUnauthorized());
        // Signed for another payment: the id it covers isn't this one.
        mvc.perform(webhook("123", "9001", signature("9002", "req-1", "1742505638683"), "req-1"))
                .andExpect(status().isUnauthorized());

        assertThat(statusOfOrder()).isEqualTo("PENDIENTE_PAGO");
        assertThat(mercadoPago.requests("GET", "/v1/payments/")).isEmpty();
        assertThat(redis.hasKey("mp:event:123")).as("a later real notice 123 is still handled").isFalse();
    }

    /** Escenario: Pago rechazado. The order keeps waiting, so the customer can pay again until it expires. */
    @Test
    void aRejectedPaymentLeavesTheOrderWaitingAndAnotherAttemptPaysIt() throws Exception {
        mercadoPago.payment(9001, "rejected", orderId, "2000.00", "ARS");
        notifyPayment("123", 9001).andExpect(status().isOk());
        assertThat(statusOfOrder()).isEqualTo("PENDIENTE_PAGO");

        mercadoPago.payment(9002, "approved", orderId, "2000.00", "ARS");
        notifyPayment("124", 9002).andExpect(status().isOk());
        assertThat(statusOfOrder()).isEqualTo("PAGADA");
    }

    @Test
    void aPendingPaymentChangesNothing() throws Exception {
        mercadoPago.payment(9001, "in_process", orderId, "2000.00", "ARS");

        notifyPayment("123", 9001).andExpect(status().isOk());

        assertThat(statusOfOrder()).isEqualTo("PENDIENTE_PAGO");
        assertThat(issue()).isNull();
    }

    @Test
    void noticesThatAreNotAboutPaymentsAreIgnored() throws Exception {
        String requestId = UUID.randomUUID().toString();
        mvc.perform(post("/api/payments/webhook").param("data.id", "555").param("type", "merchant_order")
                        .header("x-signature", signature("555", requestId, "1742505638683"))
                        .header("x-request-id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": 77, \"type\": \"merchant_order\", \"data\": {\"id\": \"555\"}}"))
                .andExpect(status().isOk());

        assertThat(mercadoPago.requests("GET", "/")).isEmpty();
    }

    /** With Mercado Pago down the notice fails with 503 and is let go, so Mercado Pago's retry is handled. */
    @Test
    void whenThePaymentCannotBeReadMercadoPagoIsAskedToRetry() throws Exception {
        mercadoPago.payment(9001, "approved", orderId, "2000.00", "ARS");
        mercadoPago.goDown();

        notifyPayment("123", 9001).andExpect(status().isServiceUnavailable());
        assertThat(statusOfOrder()).isEqualTo("PENDIENTE_PAGO");
        assertThat(redis.hasKey("mp:event:123")).isFalse();

        mercadoPago.comeBack();
        notifyPayment("123", 9001).andExpect(status().isOk());
        assertThat(statusOfOrder()).isEqualTo("PAGADA");
    }

    /**
     * A payment Mercado Pago doesn't have (404: another account's, or the panel's "Simular notificación") won't
     * appear on a retry: 200, the notice stays handled and the order doesn't change.
     */
    @Test
    void aPaymentMercadoPagoDoesNotHaveIsIgnoredWithout503() throws Exception {
        notifyPayment("123", 424242).andExpect(status().isOk());

        assertThat(mercadoPago.requests("GET", "/v1/payments/424242")).hasSize(1);
        assertThat(statusOfOrder()).isEqualTo("PENDIENTE_PAGO");
        assertThat(redis.hasKey("mp:event:123")).as("not asked for again").isTrue();
    }

    /**
     * A 401 (wrong, expired or sandbox token) is ours to fix, and the notice may be a real approved payment: 503 and
     * the key let go, so Mercado Pago sends it again and it pays the order once the token is right.
     */
    @Test
    void aRefusedTokenAsksMercadoPagoToRetry() throws Exception {
        mercadoPago.payment(9001, "approved", orderId, "2000.00", "ARS");
        mercadoPago.refuseWith(401);

        notifyPayment("123", 9001).andExpect(status().isServiceUnavailable());
        assertThat(statusOfOrder()).isEqualTo("PENDIENTE_PAGO");
        assertThat(redis.hasKey("mp:event:123")).isFalse();

        mercadoPago.comeBack();
        notifyPayment("123", 9001).andExpect(status().isOk());
        assertThat(statusOfOrder()).isEqualTo("PAGADA");
    }

    /** The body isn't trusted: an approved payment for less than the total doesn't pay the order. */
    @Test
    void aPaymentForAnotherAmountOrCurrencyDoesNotPayTheOrder() throws Exception {
        mercadoPago.payment(9001, "approved", orderId, "1.00", "ARS");
        notifyPayment("123", 9001).andExpect(status().isOk());
        assertThat(statusOfOrder()).isEqualTo("PENDIENTE_PAGO");
        assertThat(issue()).contains("9001");

        mercadoPago.payment(9002, "approved", orderId, "2000.00", "USD");
        notifyPayment("124", 9002).andExpect(status().isOk());
        assertThat(statusOfOrder()).isEqualTo("PENDIENTE_PAGO");
    }

    @Test
    void aPaymentThatNamesNoOrderIsIgnored() throws Exception {
        mercadoPago.payment(9001, "approved", 999_999L, "2000.00", "ARS");
        mercadoPago.payment(9002, "approved", null, "2000.00", "ARS");

        notifyPayment("123", 9001).andExpect(status().isOk());
        notifyPayment("124", 9002).andExpect(status().isOk());

        assertThat(statusOfOrder()).isEqualTo("PENDIENTE_PAGO");
    }

    /**
     * The payment is approved just after the order expired and was cancelled: it isn't revived in silence (its stock
     * may be sold already). It stays CANCELADA, marked for someone to give the money back.
     */
    @Test
    void anApprovedPaymentForAnOrderAlreadyCancelledIsMarkedForReview() throws Exception {
        expiry.cancelExpired(LocalDateTime.now().plusMinutes(31));
        assertThat(statusOfOrder()).isEqualTo("CANCELADA");

        mercadoPago.payment(9001, "approved", orderId, "2000.00", "ARS");
        notifyPayment("123", 9001).andExpect(status().isOk());

        assertThat(statusOfOrder()).isEqualTo("CANCELADA");
        assertThat(stockOfTaza()).isEqualTo(5);
        assertThat(issue()).contains("9001").contains("cancelado");
        mailQueue.deliverDue(Instant.now());
        assertThat(mail.to(LUCIA)).isEmpty();
    }

    /** A second approved payment of a paid order (two tabs) is marked: someone gives it back. */
    @Test
    void aSecondApprovedPaymentIsMarkedForReview() throws Exception {
        mercadoPago.payment(9001, "approved", orderId, "2000.00", "ARS");
        mercadoPago.payment(9002, "approved", orderId, "2000.00", "ARS");

        notifyPayment("123", 9001).andExpect(status().isOk());
        notifyPayment("124", 9002).andExpect(status().isOk());

        assertThat(statusOfOrder()).isEqualTo("PAGADA");
        assertThat(issue()).contains("9002");
    }

    /** A refund (or chargeback) of the payment that paid it cancels the order and gives its stock back. */
    @Test
    void aRefundOfThePaymentCancelsThePaidOrderAndGivesTheStockBack() throws Exception {
        mercadoPago.payment(9001, "approved", orderId, "2000.00", "ARS");
        notifyPayment("123", 9001).andExpect(status().isOk());
        await().atMost(SYNC).until(() -> purchases() == 1);
        assertThat(stockOfTaza()).isEqualTo(3);

        // A refund of some other payment of the order changes nothing.
        mercadoPago.payment(9002, "refunded", orderId, "2000.00", "ARS");
        notifyPayment("124", 9002).andExpect(status().isOk());
        assertThat(statusOfOrder()).isEqualTo("PAGADA");

        mercadoPago.payment(9001, "refunded", orderId, "2000.00", "ARS");
        notifyPayment("125", 9001).andExpect(status().isOk());

        assertThat(statusOfOrder()).isEqualTo("CANCELADA");
        assertThat(stockOfTaza()).isEqualTo(5);
        await().atMost(SYNC).until(() -> purchases() == 0);
    }

    // ── helpers ──────────────────────────────────────────────

    /** Mercado Pago's notice that payment {@code paymentId} changed, signed as it signs them. */
    private ResultActions notifyPayment(String eventId, long paymentId) throws Exception {
        String requestId = UUID.randomUUID().toString();
        String ts = String.valueOf(System.currentTimeMillis());
        return mvc.perform(webhook(eventId, String.valueOf(paymentId), signature(String.valueOf(paymentId), requestId, ts), requestId));
    }

    private static MockHttpServletRequestBuilder webhook(
            String eventId, String paymentId, String signature, String requestId) {
        // As Mercado Pago calls notification_url: its own source_news, then data.id and type.
        var request = post("/api/payments/webhook").param("source_news", "webhooks")
                .param("data.id", paymentId).param("type", "payment")
                .header("x-request-id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id": %s, "live_mode": false, "type": "payment", "action": "payment.updated",
                         "api_version": "v1", "data": {"id": "%s"}}
                        """.formatted(eventId, paymentId));
        return signature == null ? request : request.header("x-signature", signature);
    }

    /** Mercado Pago's x-signature: HMAC-SHA256 of the manifest with the webhook's secret. */
    private static String signature(String dataId, String requestId, String ts) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        return "ts=" + ts + ",v1=" + HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }

    private String statusOfOrder() {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private String issue() {
        return jdbc.queryForObject("SELECT payment_issue FROM orders WHERE id = ?", String.class, orderId);
    }

    private int stockOfTaza() {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, taza.getId());
    }

    private long purchases() {
        try (var session = neo4j.session()) {
            return session.run("MATCH (:User)-[r:PURCHASED]->(:Product) RETURN count(r) AS n").single().get("n").asLong();
        }
    }

    private User customer(String name, String email) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("not-used");
        user.setRole(Role.USER);
        user.setEmailVerified(true);
        return users.save(user);
    }

    private Address addressOf(User owner) {
        Address address = new Address();
        address.setFirstName(owner.getName());
        address.setLastName("Pérez");
        address.setAddress("San Martín 123");
        address.setZipCode("2000");
        address.setCity(cities.findById(jdbc.queryForObject("SELECT id FROM city WHERE name = 'Rosario'", Integer.class))
                .orElseThrow());
        address.setCreatedBy(owner);
        return addresses.save(address);
    }

    /** Signed in the way JwtAuthFilter leaves it: the user id travels as the credentials. */
    private static RequestPostProcessor as(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                user.getEmail(), user.getId(), List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
