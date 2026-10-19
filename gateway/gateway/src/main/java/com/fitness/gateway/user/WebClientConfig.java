package com.fitness.gateway.user;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for WebClient used to communicate with other microservices.
 * Uses Spring Cloud LoadBalancer for client-side load balancing.
 */
@Configuration
public class WebClientConfig
{
    /**
     * Creates a load-balanced WebClient builder.
     * The @LoadBalanced annotation enables service discovery and load balancing
     * through Spring Cloud (Eureka/Consul).
     * 
     * @return WebClient.Builder configured for load balancing
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder()
    {
        return WebClient.builder();
    }

    /**
     * Creates a WebClient specifically configured to call the User Service.
     * Uses the service name "USER-SERVICE" which will be resolved through
     * service discovery (e.g., Eureka) to the actual service instances.
     * 
     * @param webClientBuilder Load-balanced WebClient builder
     * @return WebClient configured for User Service communication
     */
    @Bean
    public WebClient userServiceWebClient(WebClient.Builder webClientBuilder)
    {
        return webClientBuilder
                .baseUrl("http://USER-SERVICE") // Service name for discovery
                .build();
    }
}
