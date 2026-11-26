package com.Proyect.Ecommerce.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.Proyect.Ecommerce.IntegrationTest;
import com.Proyect.Ecommerce.RecordingMailSender;
import com.Proyect.Ecommerce.dto.order.OrderRequestDTO;
import com.Proyect.Ecommerce.exception.ConflictException;
import com.Proyect.Ecommerce.model.Address;
import com.Proyect.Ecommerce.model.Category;
import com.Proyect.Ecommerce.model.OrderStatus;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.model.Role;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.repository.AddressRepository;
import com.Proyect.Ecommerce.repository.CategoryRepository;
import com.Proyect.Ecommerce.repository.CityRepository;
import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.repository.UserRepository;

/** HU-18 · The order's state machine and the job that cancels the unpaid ones, against real Postgres and Redis. */
@IntegrationTest
class OrderLifecycleIntegrationTest {

    private static final String LUCIA = "lucia@example.com";

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

    private User lucia;
    private Address address;
    private Product taza;
    private Product plato;

    @BeforeEach
    void aStore() {
        jdbc.execute("TRUNCATE reviews, order_items, orders, address, wishlist, product_images, products, categories, users RESTART IDENTITY CASCADE");
        redis.delete(List.of(MailQueue.QUEUE, MailQueue.SENDING, MailQueue.FAILED));
        var carts = redis.keys("cart:*");
        if (carts != null && !carts.isEmpty()) redis.delete(carts);
        mail.clear();

        Category category = new Category();
        category.setName("Bazar");
        category = categories.save(category);
        taza = product(category, "Taza", 5);
        plato = product(category, "Plato", 4);

        lucia = new User();
        lucia.setName("Lucía");
        lucia.setEmail(LUCIA);
        lucia.setPassword("not-used");
        lucia.setRole(Role.USER);
        lucia.setEmailVerified(true);
        lucia = users.save(lucia);

        address = new Address();
        address.setFirstName("Lucía");
        address.setLastName("Pérez");
        address.setAddress("San Martín 123");
        address.setZipCode("2000");
        address.setCity(cities.findById(jdbc.queryForObject("SELECT id FROM city WHERE name = 'Rosario'", Integer.class))
                .orElseThrow());
        address.setCreatedBy(lucia);
        address = addresses.save(address);
    }

    // ── The state machine ────────────────────────────────────

    @Test
    void aPaidOrderIsShippedAndDeliveredAndTheCustomerIsToldWhenItShips() {
        long order = buy(2, 1);
        orders.markPaid(order);

        orders.transition(order, OrderStatus.ENVIADA);
        assertEquals("ENVIADA", statusOf(order));
        orders.transition(order, OrderStatus.ENTREGADA);
        assertEquals("ENTREGADA", statusOf(order));

        mailQueue.deliverDue(Instant.now());
        assertEquals(1, mail.to(LUCIA, "Tu pedido #" + order + " está en camino").size());
    }

    @Test
    void movesOutsideTheStoryAre409AndChangeNothing() {
        long order = buy(1, 0);

        assertThrows(ConflictException.class, () -> orders.transition(order, OrderStatus.ENVIADA));
        assertThrows(ConflictException.class, () -> orders.transition(order, OrderStatus.ENTREGADA));
        assertEquals("PENDIENTE_PAGO", statusOf(order));

        orders.markPaid(order);
        orders.transition(order, OrderStatus.ENVIADA);
        assertThrows(ConflictException.class, () -> orders.transition(order, OrderStatus.CANCELADA), "already shipped");
        assertThrows(ConflictException.class, () -> orders.transition(order, OrderStatus.PAGADA));
        assertEquals("ENVIADA", statusOf(order));
    }

    /** Cancelling gives every unit back, once: a second cancel is 409 and takes nothing more. */
    @Test
    void cancellingAPaidOrderGivesItsStockBackOnce() {
        long order = buy(2, 3);
        assertEquals(3, stockOf(taza));
        assertEquals(1, stockOf(plato));
        orders.markPaid(order);

        orders.transition(order, OrderStatus.CANCELADA);
        assertThrows(ConflictException.class, () -> orders.transition(order, OrderStatus.CANCELADA));

        assertEquals("CANCELADA", statusOf(order));
        assertEquals(5, stockOf(taza));
        assertEquals(4, stockOf(plato));
    }

    // ── The expiry job ───────────────────────────────────────

    /** Escenario: vencimiento. After 30 minutes without an approved payment it's CANCELADA and the stock is back. */
    @Test
    void theJobCancelsTheOrdersWhoseTimeIsUpAndGivesTheirStockBack() {
        long expired = buy(2, 1);
        long onTime = buy(1, 0);
        jdbc.update("UPDATE orders SET expires_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), expired);
        assertEquals(2, stockOf(taza));

        assertEquals(1, expiry.cancelExpired(LocalDateTime.now()));

        assertEquals("CANCELADA", statusOf(expired));
        assertEquals("PENDIENTE_PAGO", statusOf(onTime));
        assertEquals(4, stockOf(taza), "the 2 of the cancelled order are back, the 1 still on time isn't");
        assertEquals(4, stockOf(plato));
        mailQueue.deliverDue(Instant.now());
        assertTrue(mail.to(LUCIA).isEmpty());
    }

    /** The 30 minutes are counted on the JVM's clock, the one expires_at was written with (UTC). */
    @Test
    void anOrderJustMadeIsCancelledOnlyAfterItsThirtyMinutes() {
        long order = buy(1, 0);

        assertEquals(0, expiry.cancelExpired(LocalDateTime.now().plusMinutes(29)));
        assertEquals("PENDIENTE_PAGO", statusOf(order));

        assertEquals(1, expiry.cancelExpired(LocalDateTime.now().plusMinutes(31)));
        assertEquals("CANCELADA", statusOf(order));
    }

    @Test
    void runningTheJobAgainGivesNothingBackTwice() {
        long order = buy(2, 0);
        LocalDateTime later = LocalDateTime.now().plusMinutes(31);

        expiry.cancelExpired(later);
        assertEquals(0, expiry.cancelExpired(later));

        assertEquals("CANCELADA", statusOf(order));
        assertEquals(5, stockOf(taza));
    }

    /** An approved payment that got there first wins: the job leaves the order paid and its stock taken. */
    @Test
    void anOrderPaidJustBeforeTheJobStaysPaid() {
        long order = buy(2, 0);
        orders.markPaid(order);

        assertFalse(orders.cancelExpired(order), "a late move finds it paid");
        assertEquals(0, expiry.cancelExpired(LocalDateTime.now().plusMinutes(31)));

        assertEquals("PAGADA", statusOf(order));
        assertEquals(3, stockOf(taza));
    }

    // ── helpers ──────────────────────────────────────────────

    /** An order of {@code tazas} cups and {@code platos} plates, through createOrder like a real purchase. */
    private long buy(int tazas, int platos) {
        String cart = "cart:" + lucia.getId();
        if (tazas > 0) redis.opsForHash().put(cart, taza.getId().toString(), String.valueOf(tazas));
        if (platos > 0) redis.opsForHash().put(cart, plato.getId().toString(), String.valueOf(platos));
        OrderRequestDTO request = new OrderRequestDTO();
        request.setAddressId(address.getId());
        return orders.createOrder(lucia.getId(), request).getId();
    }

    private Product product(Category category, String name, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal("1000.00"));
        product.setStock(stock);
        product.setCategory(category);
        return products.save(product);
    }

    private String statusOf(long order) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order);
    }

    private int stockOf(Product product) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, product.getId());
    }
}
