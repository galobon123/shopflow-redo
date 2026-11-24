package com.Proyect.Ecommerce.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.health.registry.ReactiveHealthContributorRegistry;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.Proyect.Ecommerce.IntegrationTest;

/**
 * HU-14 · The Docker HEALTHCHECK reads /actuator/health: Postgres and Redis decide it, Neo4j doesn't,
 * so a Neo4j down never marks the backend unhealthy (the store works without recommendations).
 */
@IntegrationTest
@AutoConfigureMockMvc
class HealthCheckIntegrationTest {

    @Autowired private HealthContributorRegistry health;
    /** Redis (Lettuce) and Neo4j may register here instead, when Reactor is on the classpath. */
    @Autowired private ReactiveHealthContributorRegistry reactiveHealth;
    @Autowired private MockMvc mvc;

    @Test
    void neo4jIsNotPartOfTheHealthCheck() throws Exception {
        assertThat(health.getContributor("db")).isNotNull();
        assertThat(reactiveHealth.getContributor("redis")).isNotNull();
        assertThat(health.getContributor("neo4j")).isNull();
        assertThat(reactiveHealth.getContributor("neo4j")).isNull();

        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
