package com.Proyect.Ecommerce.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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

    @Test
    void appliesMigrationsInOrder() {
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
        String last = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1", String.class);
        assertEquals(0, pending);
        assertEquals("2", last);
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

    @Test
    void newStatesGetIdsAfterTheSeed() {
        Integer id = jdbc.queryForObject("INSERT INTO state (name) VALUES ('Prueba') RETURNING id", Integer.class);
        assertTrue(id > 94);
    }
}
