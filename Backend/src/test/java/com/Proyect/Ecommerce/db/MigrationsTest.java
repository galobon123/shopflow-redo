package com.Proyect.Ecommerce.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.Proyect.Ecommerce.repository.CityRepository;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * HU-02 · Escenario: Base vacía.
 * Runs every Flyway migration on a real, empty Postgres (no Docker needed) and boots JPA with
 * ddl-auto=validate, so the context fails if an entity and the schema drift apart.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MigrationsTest {

    private static final EmbeddedPostgres postgres = start();

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo iniciar Postgres embebido", e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    static void stop() throws IOException {
        postgres.close();
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CityRepository cities;

    /** Highest V{n}__ file in db/migration, so the test doesn't need editing with each migration. */
    private static String newestMigration() throws Exception {
        return Arrays.stream(new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql"))
                .map(r -> Integer.parseInt(r.getFilename().substring(1, r.getFilename().indexOf("__"))))
                .max(Integer::compare)
                .orElseThrow()
                .toString();
    }

    @Test
    void appliesMigrationsInOrder() throws Exception {
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
        String last = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1", String.class);
        assertEquals(0, pending);
        assertEquals(newestMigration(), last);
    }

    @Test
    void seedsProvincesAndCities() {
        assertEquals(24, jdbc.queryForObject("SELECT COUNT(*) FROM state", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM city", Integer.class) > 3900);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM city c JOIN state s ON s.id = c.state_id WHERE s.name = 'Santa Fe' AND c.name = 'Rosario'",
                Integer.class));
    }

    @Test
    void findsSeededCityById() {
        Integer rosarioId = jdbc.queryForObject("SELECT id FROM city WHERE name = 'Rosario'", Integer.class);
        assertEquals("Rosario", cities.findById(rosarioId).orElseThrow().getName());
    }

    /**
     * HU-11 · The photo a product had before V4 becomes its cover in product_images.
     * Outside the test transaction: uncommitted inserts would hold the lock V4's ALTER TABLE waits for.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void v4MovesEachProductPhotoToItsCover() {
        String url = postgres.getJdbcUrl("postgres", "postgres");
        Flyway before = Flyway.configure().dataSource(url, "postgres", "postgres")
                .schemas("before_v4").target("3").load();
        before.migrate();
        jdbc.execute("INSERT INTO before_v4.categories (name) VALUES ('Remeras')");
        jdbc.execute("INSERT INTO before_v4.products (name, price, stock, category_id, created_at, image_url) VALUES "
                + "('Con foto', 1000, 1, 1, now(), 'https://cdn.example/remera.jpg'), "
                + "('Sin foto', 1000, 1, 1, now(), NULL)");

        Flyway.configure().dataSource(url, "postgres", "postgres").schemas("before_v4").load().migrate();

        List<Map<String, Object>> images = jdbc.queryForList(
                "SELECT p.name, i.url, i.position FROM before_v4.product_images i JOIN before_v4.products p ON p.id = i.product_id");
        assertEquals(List.of(Map.of("name", "Con foto", "url", "https://cdn.example/remera.jpg", "position", 0)), images);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'before_v4' AND table_name = 'products' AND column_name = 'image_url'",
                Integer.class));
    }

    @Test
    void newStatesGetIdsAfterTheSeed() {
        Integer id = jdbc.queryForObject("INSERT INTO state (name) VALUES ('Prueba') RETURNING id", Integer.class);
        assertTrue(id > 94);
    }
}
