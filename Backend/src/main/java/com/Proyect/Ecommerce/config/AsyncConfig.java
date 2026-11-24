package com.Proyect.Ecommerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * HU-09 · Lets {@code @Async} methods run on Spring Boot's task executor, so a slow SMTP server
 * doesn't hold the request (and its response time doesn't tell whether an account exists).
 * HU-14 · Also runs the job that retries the Neo4j sync ({@code @Scheduled}).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
