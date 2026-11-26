package com.Proyect.Ecommerce.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.Proyect.Ecommerce.IntegrationTest;
import com.Proyect.Ecommerce.model.Category;
import com.Proyect.Ecommerce.model.OrderStatus;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.model.Role;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.repository.CategoryRepository;
import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.repository.UserRepository;

import jakarta.persistence.EntityManagerFactory;

/** HU-19 · "Mis pedidos": GET /api/orders (paged, newest first) and GET /api/orders/{id}, only the owner's. */
@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class MyOrdersIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private User lucia;
    private long address;
    private Product taza;
    private Product mate;
    private Statistics statistics;

    @BeforeEach
    void emptyStore() {
        jdbc.execute("TRUNCATE reviews, order_items, orders, address, wishlist, product_images, products, categories, users RESTART IDENTITY CASCADE");
        lucia = customer("Lucia");
        address = addressOf(lucia);
        taza = product("Taza", "1000.00");
        mate = product("Mate", "2500.50");
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @AfterEach
    void stopCounting() {
        statistics.setStatisticsEnabled(false);
    }

    /** Escenario: Ver mis pedidos. */
    @Test
    void theThreeOrdersComeNewestFirstWithTheirStatus() throws Exception {
        long first = order(lucia, address, OrderStatus.ENTREGADA, LocalDateTime.of(2026, 9, 1, 12, 0), taza);
        long second = order(lucia, address, OrderStatus.ENVIADA, LocalDateTime.of(2026, 10, 1, 12, 0), mate);
        long third = order(lucia, address, OrderStatus.PENDIENTE_PAGO, LocalDateTime.of(2026, 11, 1, 12, 0), taza, mate);

        mvc.perform(get("/api/orders").with(as(lucia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].id").value(third))
                .andExpect(jsonPath("$.items[0].status").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.items[0].items.length()").value(2))
                .andExpect(jsonPath("$.items[1].id").value(second))
                .andExpect(jsonPath("$.items[1].status").value("ENVIADA"))
                .andExpect(jsonPath("$.items[2].id").value(first))
                .andExpect(jsonPath("$.items[2].status").value("ENTREGADA"))
                // An instant (UTC), so the browser can show it in Argentina's time zone.
                .andExpect(jsonPath("$.items[2].createdAt").value("2026-09-01T12:00:00Z"));
    }

    @Test
    void theListHasOnlyTheCustomersOwnOrders() throws Exception {
        User martin = customer("Martin");
        order(martin, addressOf(martin), OrderStatus.PAGADA, LocalDateTime.now(), taza);
        long mine = order(lucia, address, OrderStatus.PAGADA, LocalDateTime.now(), mate);

        mvc.perform(get("/api/orders").with(as(lucia)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(mine));
    }

    @Test
    void ordersComeTenPerPage() throws Exception {
        for (int day = 1; day <= 12; day++) {
            order(lucia, address, OrderStatus.PAGADA, LocalDateTime.of(2026, 10, day, 12, 0), taza);
        }

        mvc.perform(get("/api/orders").with(as(lucia)))
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/orders").param("page", "1").with(as(lucia)))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].createdAt").value("2026-10-01T12:00:00Z"));
        mvc.perform(get("/api/orders").param("page", "-1").with(as(lucia)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La página no puede ser negativa."));
    }

    /**
     * No N+1: a full page reads the ids, their count, the orders with items and address, the photos (batched)
     * and the reviews of the delivered ones, however many orders and products it has. And the LIMIT stays in
     * the database (no HHH90003004).
     */
    @Test
    void aFullPageTakesTheSameFewQueries(CapturedOutput output) throws Exception {
        for (int day = 1; day <= 10; day++) {
            Product one = product("Producto " + day, "100.00");
            Product other = product("Otro " + day, "200.00");
            order(lucia, address, day % 2 == 0 ? OrderStatus.ENTREGADA : OrderStatus.PAGADA,
                    LocalDateTime.of(2026, 10, day, 12, 0), one, other);
        }
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        mvc.perform(get("/api/orders").with(as(lucia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].items[0].imageUrl").value(endsWith(".jpg")));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
        assertThat(output).doesNotContain("HHH90003004");
    }

    @Test
    void theDetailHasTheItemsAndTheWholeAddress() throws Exception {
        long id = order(lucia, address, OrderStatus.PENDIENTE_PAGO, LocalDateTime.now(), taza, mate);
        Integer rosario = jdbc.queryForObject("SELECT id FROM city WHERE name = 'Rosario'", Integer.class);
        Integer santaFe = jdbc.queryForObject("SELECT state_id FROM city WHERE id = ?", Integer.class, rosario);

        mvc.perform(get("/api/orders/{id}", id).with(as(lucia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.total").value(3500.5))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].productName").value("Taza"))
                .andExpect(jsonPath("$.items[0].subtotal").value(1000.0))
                .andExpect(jsonPath("$.items[0].imageUrl").value("https://img.example.com/taza.jpg"))
                .andExpect(jsonPath("$.items[0].canReview").value(false))
                .andExpect(jsonPath("$.address.cityId").value(rosario))
                .andExpect(jsonPath("$.address.stateId").value(santaFe))
                .andExpect(jsonPath("$.address.cityName").value("Rosario"));
    }

    /** Escenario: Pedido de otro cliente. Someone else's order and a missing one answer the same. */
    @Test
    void someoneElsesOrderIsNotFound() throws Exception {
        User martin = customer("Martin");
        long martins = order(martin, addressOf(martin), OrderStatus.PAGADA, LocalDateTime.now(), taza);

        mvc.perform(get("/api/orders/{id}", martins).with(as(lucia)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pedido no encontrado."));
        mvc.perform(get("/api/orders/{id}", martins + 100).with(as(lucia)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pedido no encontrado."));
    }

    @Test
    void withoutASessionThereAreNoOrders() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders/1")).andExpect(status().isUnauthorized());
    }

    /** "Calificar" only for products of a delivered order that are still for sale and not reviewed yet. */
    @Test
    void aDeliveredProductCanBeReviewedUntilItIs() throws Exception {
        Product gone = product("Termo", "9000.00");
        jdbc.update("UPDATE products SET active = false WHERE id = ?", gone.getId());
        long delivered = order(lucia, address, OrderStatus.ENTREGADA, LocalDateTime.now(), taza, mate, gone);
        jdbc.update("INSERT INTO reviews (product_id, user_id, order_id, rating, created_at) VALUES (?, ?, ?, 5, now())",
                mate.getId(), lucia.getId(), delivered);

        mvc.perform(get("/api/orders/{id}", delivered).with(as(lucia)))
                .andExpect(jsonPath("$.items[0].canReview").value(true))
                .andExpect(jsonPath("$.items[1].canReview").value(false))
                .andExpect(jsonPath("$.items[2].canReview").value(false));
        mvc.perform(get("/api/orders").with(as(lucia)))
                .andExpect(jsonPath("$.items[0].items[0].canReview").value(true))
                .andExpect(jsonPath("$.items[0].items[1].canReview").value(false));
    }

    // ── helpers ──────────────────────────────────────────────

    /** An order with one unit of each product, at their price. */
    private long order(User owner, long shipTo, OrderStatus status, LocalDateTime createdAt, Product... items) {
        BigDecimal total = List.of(items).stream().map(Product::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        long id = jdbc.queryForObject("INSERT INTO orders (created_at, user_id, total, address_id, status, expires_at) "
                + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                createdAt, owner.getId(), total, shipTo, status.name(), createdAt.plusMinutes(30));
        for (Product product : items) {
            jdbc.update("INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, 1, ?)",
                    id, product.getId(), product.getPrice());
        }
        return id;
    }

    private long addressOf(User owner) {
        Integer rosario = jdbc.queryForObject("SELECT id FROM city WHERE name = 'Rosario'", Integer.class);
        return jdbc.queryForObject("INSERT INTO address (first_name, last_name, address, city_id, zip_code, created_by) "
                + "VALUES (?, 'Test', 'San Martín 123', ?, '2000', ?) RETURNING id", Long.class,
                owner.getName(), rosario, owner.getId());
    }

    private Product product(String name, String price) {
        Category category = new Category();
        category.setName("Bazar " + name);
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStock(10);
        product.setCategory(categories.save(category));
        product = products.save(product);
        jdbc.update("INSERT INTO product_images (product_id, url, position) VALUES (?, ?, 0)",
                product.getId(), "https://img.example.com/" + name.toLowerCase().replace(' ', '-') + ".jpg");
        return product;
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
