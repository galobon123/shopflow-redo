package com.Proyect.Ecommerce.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.search.ProductSearchIndex;
import com.Proyect.Ecommerce.service.GraphBackfill;

/**
 * Local development only (app.demo-data.enabled, on in the dev profile): fills the store so every page
 * has something to show. Each part runs only when it's missing, so it also completes an existing database:
 * <ul>
 * <li>db/demo/productos.sql: categories and products, when the catalog is empty;</li>
 * <li>db/demo/fotos.sql: photos, for the sample products that have none;</li>
 * <li>db/demo/uso.sql: customers (password {@value #PASSWORD}), orders, reviews and favourites, when the
 * demo customers don't exist yet. Then the whole store goes to Neo4j for the recommendations (HU-15,
 * GraphBackfill); a database loaded before that needs it once with {@code app.neo4j.backfill=true}.</li>
 * </ul>
 * Not a migration on purpose: it never reaches production and leaves no trace in Flyway's history, which the
 * Docker backend validates against the same database.
 */
@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    static final String PASSWORD = "demo1234";

    /** Every demo customer's email ends like this. */
    private static final String DEMO_EMAILS = "%@demo.local";

    private final ProductRepository productRepository;
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;
    private final GraphBackfill graphBackfill;

    public DemoDataLoader(ProductRepository productRepository, DataSource dataSource, JdbcTemplate jdbc,
                          PasswordEncoder passwordEncoder, PlatformTransactionManager transactionManager,
                          GraphBackfill graphBackfill) {
        this.productRepository = productRepository;
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.transaction = new TransactionTemplate(transactionManager);
        this.graphBackfill = graphBackfill;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (productRepository.count() == 0) {
            runScript("db/demo/productos.sql");
            log.info("Catálogo vacío: se cargaron {} productos de demo.", productRepository.count());
        }
        runScript("db/demo/fotos.sql");
        ProductSearchIndex.productsChanged();

        Integer customers = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE email LIKE ?", Integer.class, DEMO_EMAILS);
        if (customers != null && customers == 0) {
            // One transaction: uso.sql matches order items to their order by created_at, which uses now().
            transaction.executeWithoutResult(status -> {
                runScript("db/demo/uso.sql");
                jdbc.update("UPDATE users SET password = ? WHERE email LIKE ?",
                        passwordEncoder.encode(PASSWORD), DEMO_EMAILS);
            });
            log.info("Se cargaron clientes de demo con pedidos, reseñas y favoritos (contraseña {}).", PASSWORD);
            // Without Neo4j the store still works: the backfill only warns, and can be run again later.
            graphBackfill.backfill();
        }
    }

    private void runScript(String path) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(path));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);
    }
}
