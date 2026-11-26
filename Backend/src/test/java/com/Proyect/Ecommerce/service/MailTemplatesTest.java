package com.Proyect.Ecommerce.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.Proyect.Ecommerce.model.Address;
import com.Proyect.Ecommerce.model.City;
import com.Proyect.Ecommerce.model.Order;
import com.Proyect.Ecommerce.model.OrderItem;
import com.Proyect.Ecommerce.model.OrderStatus;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.model.State;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.service.MailQueue.Mail;

/** HU-20 · What the transactional emails say, in HTML and in plain text. */
class MailTemplatesTest {

    private static TimeZone jvmZone;

    private final MailTemplates templates = new MailTemplates("http://localhost:4200/");

    /** Stored dates are the JVM's local time, which EcommerceApplication fixes to UTC; so does this test. */
    @BeforeAll
    static void utc() {
        jvmZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterAll
    static void restoreZone() {
        TimeZone.setDefault(jvmZone);
    }

    @Test
    void thePaidOrderEmailHasTheNumberDateItemsTotalAddressAndLink() {
        Order order = order("Lucía", "Taza", "Plato hondo");

        Mail mail = templates.orderPaid(order);

        assertEquals("lucia@example.com", mail.to());
        assertEquals("Recibimos el pago de tu pedido #42", mail.subject());
        for (String body : new String[] {mail.text(), mail.html()}) {
            assertTrue(body.contains("#42"), body);
            // 2026-11-26 17:05 UTC is 14:05 in Buenos Aires.
            assertTrue(body.contains("26 de noviembre de 2026, 14:05"), body);
            assertTrue(body.contains("Taza") && body.contains("Plato hondo"), body);
            assertTrue(body.contains("$\u00a01.000") && body.contains("$\u00a02.000"), "unit price and subtotal: " + body);
            assertTrue(body.contains("$\u00a01.234,50"), "cents only when there are any: " + body);
            assertTrue(body.contains("$\u00a03.234,50"), "total: " + body);
            assertTrue(body.contains("San Martín 123") && body.contains("Rosario") && body.contains("Santa Fe")
                    && body.contains("2000"), "address: " + body);
            assertTrue(body.contains("http://localhost:4200/account/orders/42"), body);
        }
    }

    @Test
    void whatCustomersWroteIsEscapedInTheHtml() {
        Order order = order("<b>Lucía</b>", "Taza <script>alert(1)</script>", "Plato & \"hondo\"");
        order.getAddress().setAddress("<img src=x onerror=alert(1)> 123");

        Mail mail = templates.orderPaid(order);

        assertFalse(mail.html().contains("<script>"), mail.html());
        assertFalse(mail.html().contains("<img"), mail.html());
        assertFalse(mail.html().contains("<b>Lucía"), mail.html());
        assertTrue(mail.html().contains("Taza &lt;script&gt;alert(1)&lt;/script&gt;"), mail.html());
        assertTrue(mail.html().contains("Plato &amp; &quot;hondo&quot;"), mail.html());
        // Plain text isn't interpreted: it keeps what they wrote.
        assertTrue(mail.text().contains("Taza <script>alert(1)</script>"), mail.text());
    }

    /** A token has nowhere to break: written out, it must wrap instead of widening the email on a phone. */
    @Test
    void theResetLinkWrittenOutWrapsAnywhere() {
        Mail mail = templates.passwordReset("ana@mail.com", "Ana", "abc123", java.time.Instant.now());

        assertTrue(mail.html().contains("<span style=\"word-break:break-all;overflow-wrap:anywhere;\">"
                + "http://localhost:4200/reset-password?token=abc123</span>"), mail.html());
    }

    /** "$ 62.500,50" never splits between the sign and the number. */
    @Test
    void amountsKeepTheSignWithTheNumber() {
        assertEquals("$\u00a062.500,50", MailTemplates.money(new BigDecimal("62500.50")));
        assertEquals("$\u00a048.900", MailTemplates.money(new BigDecimal("48900.00")));
    }

    @Test
    void theShippedOrderEmailSaysItIsOnItsWay() {
        Mail mail = templates.orderShipped(order("Lucía", "Taza", "Plato hondo"));

        assertEquals("Tu pedido #42 está en camino", mail.subject());
        for (String body : new String[] {mail.text(), mail.html()}) {
            assertTrue(body.contains("Taza") && body.contains("San Martín 123"), body);
            assertTrue(body.contains("http://localhost:4200/account/orders/42"), body);
        }
    }

    @Test
    void theWelcomeEmailGreetsByNameAndLinksTheStore() {
        Mail mail = templates.welcome("ana@mail.com", "Ana <3");

        assertEquals("Te damos la bienvenida a ShopFlow", mail.subject());
        assertTrue(mail.text().contains("Hola Ana <3,"), mail.text());
        assertTrue(mail.html().contains("Hola Ana &lt;3,"), mail.html());
        assertTrue(mail.html().contains("href=\"http://localhost:4200\""), mail.html());
    }

    // ── helpers ──────────────────────────────────────────────

    /** Order 42: 2 × Taza at $ 1.000 and 1 × the second product at $ 1.234,50. */
    private static Order order(String customer, String firstProduct, String secondProduct) {
        User user = new User();
        user.setName(customer);
        user.setEmail("lucia@example.com");

        State state = new State();
        state.setName("Santa Fe");
        City city = new City();
        city.setName("Rosario");
        city.setState(state);
        Address address = new Address();
        address.setFirstName("Lucía");
        address.setLastName("Pérez");
        address.setAddress("San Martín 123");
        address.setZipCode("2000");
        address.setCity(city);

        Order order = new Order();
        order.setId(42L);
        order.setUser(user);
        order.setAddress(address);
        order.setStatus(OrderStatus.PAGADA);
        order.setCreatedAt(LocalDateTime.of(2026, 11, 26, 17, 5));
        order.setTotal(new BigDecimal("3234.50"));
        order.setItems(new ArrayList<>());
        order.getItems().add(item(order, firstProduct, 2, "1000.00"));
        order.getItems().add(item(order, secondProduct, 1, "1234.50"));
        return order;
    }

    private static OrderItem item(Order order, String name, int quantity, String unitPrice) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal("99999.00")); // today's price: the email shows the one paid
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(unitPrice));
        return item;
    }
}
