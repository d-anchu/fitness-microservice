package com.fitness.aiservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * In a servlet (MVC) application the reactive WebClient.Builder bean is NOT
 * auto-configured — it only is for WebFlux apps — so we declare it explicitly.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}