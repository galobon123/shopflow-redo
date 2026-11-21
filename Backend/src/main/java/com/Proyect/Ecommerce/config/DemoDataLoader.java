package com.Proyect.Ecommerce.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.search.ProductSearchIndex;

/**
 * Local development only (app.demo-data.enabled, on in the dev profile): loads the sample
 * categories and products from db/demo/productos.sql when the catalog is empty, so the store
 * has something to show. Not a migration on purpose: it never reaches production and leaves
 * no trace in Flyway's history, which the Docker backend validates against the same database.
 */
@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final ProductRepository productRepository;
    private final DataSource dataSource;

    public DemoDataLoader(ProductRepository productRepository, DataSource dataSource) {
        this.productRepository = productRepository;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0) {
            return;
        }
        new ResourceDatabasePopulator(new ClassPathResource("db/demo/productos.sql")).execute(dataSource);
        ProductSearchIndex.productsChanged();
        log.info("Catálogo vacío: se cargaron {} productos de demo.", productRepository.count());
    }
}
