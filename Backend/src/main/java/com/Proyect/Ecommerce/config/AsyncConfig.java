package com.Proyect.Ecommerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * HU-09 · Lets {@code @Async} methods run on Spring Boot's task executor, so a slow SMTP server
 * doesn't hold the request (and its response time doesn't tell whether an account exists).
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
