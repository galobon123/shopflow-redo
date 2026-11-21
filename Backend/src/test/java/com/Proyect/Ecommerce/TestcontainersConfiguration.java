package com.Proyect.Ecommerce;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.redis.testcontainers.RedisContainer;

/**
 * HU-27 · Real Postgres, Redis and Neo4j for integration tests, same versions as docker-compose.yml.
 * Spring wires the connections (@ServiceConnection) and reuses the containers across test classes.
 * Requires Docker: test classes using it are marked @Testcontainers(disabledWithoutDocker = true).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-bookworm").asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    @ServiceConnection
    RedisContainer redis() {
        return new RedisContainer(DockerImageName.parse("redis:7-alpine"));
    }

    @Bean
    @ServiceConnection
    Neo4jContainer neo4j() {
        return new Neo4jContainer(DockerImageName.parse("neo4j:5-community")).withoutAuthentication();
    }

    /** Replaces the SMTP sender (Spring's mail auto-configuration backs off when one exists). */
    @Bean
    RecordingMailSender mailSender() {
        return new RecordingMailSender();
    }
}
