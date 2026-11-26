package com.Proyect.Ecommerce.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.Proyect.Ecommerce.IntegrationTest;
import com.Proyect.Ecommerce.RecordingMailSender;
import com.Proyect.Ecommerce.RecordingMailSender.Received;
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

/** HU-20 · The order emails, from the status change to the inbox, against real Postgres and Redis. */
@IntegrationTest
class OrderMailsIntegrationTest {

    private static final String LUCIA = "lucia@example.com";
    private static final String PAID = "Recibimos el pago de tu pedido #";

    @Autowired private OrderService orders;
    @Autowired private MailQueue mailQueue;
    @Autowired private RecordingMailSender mail;
    @Autowired private UserRepository users;
    @Autowired private AddressRepository addresses;
    @Autowired private CityRepository cities;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ApplicationEventPublisher events;

    private User lucia;
    private Product taza;
    private Long orderId;

    @BeforeEach
    void anOrderWaitingForItsPayment() {
        jdbc.execute("TRUNCATE order_items, orders, address, wishlist, products, categories, users RESTART IDENTITY CASCADE");
        redis.delete(List.of(MailQueue.QUEUE, MailQueue.SENDING, MailQueue.FAILED));
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

        lucia = new User();
        lucia.setName("Lucía");
        lucia.setEmail(LUCIA);
        lucia.setPassword("not-used");
        lucia.setRole(Role.USER);
        lucia.setEmailVerified(true);
        lucia = users.save(lucia);

        Address address = new Address();
        address.setFirstName("Lucía");
        address.setLastName("Pérez");
        address.setAddress("San Martín 123");
        address.setZipCode("2000");
        address.setCity(cities.findById(jdbc.queryForObject("SELECT id FROM city WHERE name = 'Rosario'", Integer.class))
                .orElseThrow());
        address.setCreatedBy(lucia);
        address = addresses.save(address);

        redis.opsForHash().put("cart:" + lucia.getId(), taza.getId().toString(), "2");
        OrderRequestDTO request = new OrderRequestDTO();
        request.setAddressId(address.getId());
        orderId = orders.createOrder(lucia.getId(), request).getId();
    }

    private List<Received> paidEmails() {
        return mail.to(LUCIA, PAID + orderId);
    }

    private String statusInDatabase() {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    /** Escenario: Confirmación de compra. */
    @Test
    void whenTheOrderIsPaidTheWorkerSendsTheNumberItemsAndTotal() {
        orders.markPaid(orderId);
        assertTrue(paidEmails().isEmpty(), "the worker sends it, not the payment");

        mailQueue.deliverDue(Instant.now());

        Received email = paidEmails().getFirst();
        for (String body : List.of(email.getText(), email.getHtml())) {
            assertTrue(body.contains("#" + orderId), body);
            assertTrue(body.contains("Taza") && body.contains("$\u00a01.000") && body.contains("$\u00a02.000"), body);
            assertTrue(body.contains("San Martín 123") && body.contains("Rosario"), body);
            assertTrue(body.contains("http://localhost:4200/account/orders/" + orderId), body);
        }
    }

    /** The email shows what was paid: the price kept in the order, the name the product had when it was paid. */
    @Test
    void theEmailShowsWhatWasPaidEvenIfTheProductChanges() {
        taza.setPrice(new BigDecimal("1500.00"));
        products.save(taza);
        orders.markPaid(orderId);
        taza.setName("Taza XL");
        products.save(taza);

        mailQueue.deliverDue(Instant.now());

        String text = paidEmails().getFirst().getText();
        assertTrue(text.contains("Taza: 2 × $\u00a01.000 = $\u00a02.000"), text);
        assertTrue(text.contains("Total: $\u00a02.000"), text);
        assertFalse(text.contains("1.500") || text.contains("Taza XL"), text);
    }

    /** Sent once: running the worker again finds nothing to send. */
    @Test
    void anEmailIsSentOnlyOnce() {
        orders.markPaid(orderId);

        mailQueue.deliverDue(Instant.now());
        mailQueue.deliverDue(Instant.now());
        mailQueue.deliverDue(Instant.now().plus(Duration.ofHours(1)));

        assertEquals(1, paidEmails().size());
        assertEquals(0, redis.opsForZSet().size(MailQueue.QUEUE));
        assertEquals(0, redis.opsForZSet().size(MailQueue.SENDING));
    }

    /** Escenario: El servidor de email está caído. Retried after 1, 5 and 15 minutes, then set aside. */
    @Test
    void withTheSmtpDownTheOrderStaysPaidAndTheEmailIsRetriedThreeTimes() {
        mail.goDown();

        orders.markPaid(orderId);
        assertEquals("PAGADA", statusInDatabase());

        Instant paid = Instant.now();
        mailQueue.deliverDue(paid);
        assertEquals(1, mail.refused(), "first send");
        mailQueue.deliverDue(paid.plusSeconds(59));
        assertEquals(1, mail.refused(), "waits a minute");
        mailQueue.deliverDue(paid.plusSeconds(61));
        assertEquals(2, mail.refused(), "first retry");
        mailQueue.deliverDue(paid.plus(Duration.ofMinutes(5)));
        assertEquals(2, mail.refused(), "waits 5 minutes");
        mailQueue.deliverDue(paid.plus(Duration.ofMinutes(7)));
        assertEquals(3, mail.refused(), "second retry");
        mailQueue.deliverDue(paid.plus(Duration.ofMinutes(21)));
        assertEquals(3, mail.refused(), "waits 15 minutes");
        mailQueue.deliverDue(paid.plus(Duration.ofMinutes(23)));
        assertEquals(4, mail.refused(), "third and last retry");
        mailQueue.deliverDue(paid.plus(Duration.ofHours(2)));
        assertEquals(4, mail.refused(), "no more");

        assertEquals(0, redis.opsForZSet().size(MailQueue.QUEUE));
        List<String> failed = redis.opsForList().range(MailQueue.FAILED, 0, -1);
        assertEquals(1, failed.size());
        assertTrue(failed.getFirst().contains(PAID + orderId), failed.getFirst());
        assertEquals("PAGADA", statusInDatabase());
    }

    @Test
    void whenTheSmtpComesBackTheQueuedEmailArrives() {
        mail.goDown();
        orders.markPaid(orderId);
        Instant paid = Instant.now();
        mailQueue.deliverDue(paid);

        mail.comeBack();
        mailQueue.deliverDue(paid.plusSeconds(61));

        assertEquals(1, paidEmails().size());
        assertEquals(0, redis.opsForZSet().size(MailQueue.QUEUE));
    }

    /** Queued after the commit: a payment that rolls back sends nothing. */
    @Test
    void aPaymentThatRollsBackSendsNothing() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            orders.markPaid(orderId);
            status.setRollbackOnly();
        });

        mailQueue.deliverDue(Instant.now());

        assertEquals("PENDIENTE_PAGO", statusInDatabase());
        assertTrue(mail.to(LUCIA).isEmpty());
    }

    @Test
    void anOrderThatWasNotWaitingForItsPaymentSendsNothing() {
        orders.markPaid(orderId);
        mailQueue.deliverDue(Instant.now());

        assertThrows(ConflictException.class, () -> orders.markPaid(orderId));
        mailQueue.deliverDue(Instant.now());

        assertEquals(1, paidEmails().size());
    }

    /** Nothing ships orders yet (HU-18, HU-23): this is what happens once something does. */
    @Test
    void whenTheOrderIsShippedTheCustomerIsTold() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("UPDATE orders SET status = 'ENVIADA' WHERE id = ?", orderId);
            events.publishEvent(new OrderStatusChanged(orderId, OrderStatus.ENVIADA));
        });

        mailQueue.deliverDue(Instant.now());

        Received email = mail.to(LUCIA, "Tu pedido #" + orderId + " está en camino").getFirst();
        assertTrue(email.getText().contains("Taza") && email.getText().contains("San Martín 123"), email.getText());
    }
}
