package com.Proyect.Ecommerce;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * HU-27 · Whole application against real databases in Docker.
 * Skipped (not failed) on a machine without Docker; the CI always has it.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "jwt.secret=integration-tests-secret-at-least-32-bytes",
        // Placeholders application.properties needs resolved; @ServiceConnection replaces the real connections.
        "DB_URL=jdbc:postgresql://replaced-by-testcontainers/db", "DB_USER=unused", "DB_PASSWORD=unused",
        "NEO4J_URI=bolt://replaced-by-testcontainers:7687", "NEO4J_USER=neo4j", "NEO4J_PASSWORD=unused",
        // Keep the test database free of the developer's .env admin.
        "app.admin.email=",
        "app.admin.password=",
        // Links in emails and the Google client id don't depend on the developer's .env either.
        "app.public-url=http://localhost:4200",
        "app.google.client-id=",
        // No sample catalog: each test creates the products it needs.
        "app.demo-data.enabled=false",
        // The Neo4j retry job runs when a test calls it, not on the clock.
        "app.neo4j-sync.retry-cron=-",
        // So does the email worker (HU-20): tests call MailQueue.deliverDue themselves.
        "app.mail.worker-cron=-"
})
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
public @interface IntegrationTest {
}
