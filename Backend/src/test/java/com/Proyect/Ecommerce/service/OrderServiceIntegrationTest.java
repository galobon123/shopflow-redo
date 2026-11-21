package com.Proyect.Ecommerce.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.Proyect.Ecommerce.IntegrationTest;
import com.Proyect.Ecommerce.dto.order.OrderRequestDTO;
import com.Proyect.Ecommerce.dto.order.OrderResponseDTO;
import com.Proyect.Ecommerce.exception.BadRequestException;
import com.Proyect.Ecommerce.exception.ConflictException;
import com.Proyect.Ecommerce.exception.ForbiddenException;
import com.Proyect.Ecommerce.exception.NotFoundException;
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

/** HU-27 · The money rules of createOrder, against real Postgres and Redis. */
@IntegrationTest
class OrderServiceIntegrationTest {

    @Autowired private OrderService orders;
    @Autowired private UserRepository users;
    @Autowired private AddressRepository addresses;
    @Autowired private CityRepository cities;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;

    private Category category;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE order_items, orders, address, wishlist, products, categories, users RESTART IDENTITY CASCADE");
        var cartKeys = redis.keys("cart:*");
        if (cartKeys != null && !cartKeys.isEmpty()) {
            redis.delete(cartKeys);
        }
        category = new Category();
        category.setName("Bazar");
        category = categories.save(category);
    }

    @Test
    void buysExactlyTheStockLeft() {
        User lucia = user("lucia");
        Product taza = product("Taza", "1000.00", 2);
        putInCart(lucia, taza, 2);

        OrderResponseDTO order = orders.createOrder(lucia.getId(), orderTo(address(lucia)));

        assertEquals(0, new BigDecimal("2000.00").compareTo(order.getTotal()));
        assertEquals(0, stockOf(taza));
        assertFalse(redis.hasKey(cartKey(lucia)), "the cart is emptied after buying");
    }

    @Test
    void refusesWhenThereIsNotEnoughStockAndKeepsTheCart() {
        User lucia = user("lucia");
        Product taza = product("Taza", "1000.00", 1);
        putInCart(lucia, taza, 2);

        ConflictException error = assertThrows(ConflictException.class,
                () -> orders.createOrder(lucia.getId(), orderTo(address(lucia))));

        assertEquals("Sin stock de Taza.", error.getMessage());
        assertEquals(1, stockOf(taza));
        assertTrue(redis.hasKey(cartKey(lucia)));
    }

    /** HU-31 · Escenario: Todavía no confirmé mi email. */
    @Test
    void anUnverifiedEmailCannotBuyAndKeepsCartAndStock() {
        User lucia = user("lucia");
        lucia.setEmailVerified(false);
        users.save(lucia);
        Product taza = product("Taza", "1000.00", 2);
        putInCart(lucia, taza, 1);

        ForbiddenException error = assertThrows(ForbiddenException.class,
                () -> orders.createOrder(lucia.getId(), orderTo(address(lucia))));

        assertEquals("Confirmá tu email para comprar. Te mandamos un enlace al registrarte.", error.getMessage());
        assertEquals(2, stockOf(taza));
        assertTrue(redis.hasKey(cartKey(lucia)));
    }

    @Test
    void undoesEarlierItemsWhenALaterOneHasNoStock() {
        User lucia = user("lucia");
        Product taza = product("Taza", "1000.00", 5);
        Product plato = product("Plato", "2500.00", 0);
        putInCart(lucia, taza, 2);
        putInCart(lucia, plato, 1);

        assertThrows(ConflictException.class, () -> orders.createOrder(lucia.getId(), orderTo(address(lucia))));

        assertEquals(5, stockOf(taza), "the transaction rolls back the units already taken");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
    }

    /** HU-17/HU-27 · Escenario: Dos clientes compran la última unidad. */
    @Test
    void twoCustomersBuyTheLastUnit() throws Exception {
        User lucia = user("lucia");
        User martin = user("martin");
        Product taza = product("Taza", "1000.00", 1);
        putInCart(lucia, taza, 1);
        putInCart(martin, taza, 1);
        OrderRequestDTO luciaOrder = orderTo(address(lucia));
        OrderRequestDTO martinOrder = orderTo(address(martin));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<OrderResponseDTO>> results = new ArrayList<>();
        try {
            results.add(pool.submit(waitThen(start, () -> orders.createOrder(lucia.getId(), luciaOrder))));
            results.add(pool.submit(waitThen(start, () -> orders.createOrder(martin.getId(), martinOrder))));
            start.countDown();

            int bought = 0;
            int refused = 0;
            for (Future<OrderResponseDTO> result : results) {
                try {
                    result.get();
                    bought++;
                } catch (ExecutionException e) {
                    assertTrue(e.getCause() instanceof ConflictException, "unexpected error: " + e.getCause());
                    refused++;
                }
            }

            assertEquals(1, bought);
            assertEquals(1, refused);
            assertEquals(0, stockOf(taza), "never negative");
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void someoneElsesAddressLooksMissing() {
        User lucia = user("lucia");
        User martin = user("martin");
        Product taza = product("Taza", "1000.00", 3);
        putInCart(lucia, taza, 1);
        Address martinsAddress = address(martin);

        assertThrows(NotFoundException.class, () -> orders.createOrder(lucia.getId(), orderTo(martinsAddress)));
        assertEquals(3, stockOf(taza));
    }

    @Test
    void refusesAnEmptyCart() {
        User lucia = user("lucia");

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> orders.createOrder(lucia.getId(), orderTo(address(lucia))));
        assertEquals("El carrito está vacío", error.getMessage());
    }

    // ── helpers ──────────────────────────────────────────────

    private static <T> Callable<T> waitThen(CountDownLatch start, Callable<T> action) {
        return () -> {
            start.await();
            return action.call();
        };
    }

    private User user(String name) {
        User user = new User();
        user.setName(name);
        user.setEmail(name + "@example.com");
        user.setPassword("not-used");
        user.setRole(Role.USER);
        user.setEmailVerified(true);
        return users.save(user);
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

    private Product product(String name, String price, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setCategory(category);
        return products.save(product);
    }

    private void putInCart(User user, Product product, int quantity) {
        redis.opsForHash().put(cartKey(user), product.getId().toString(), String.valueOf(quantity));
    }

    private static String cartKey(User user) {
        return "cart:" + user.getId();
    }

    private static OrderRequestDTO orderTo(Address address) {
        OrderRequestDTO dto = new OrderRequestDTO();
        dto.setAddressId(address.getId());
        return dto;
    }

    private int stockOf(Product product) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, product.getId());
    }
}
