package com.fitness.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main entry point for the API Gateway application.
 * 
 * This gateway provides:
 * - Authentication and authorization via Keycloak JWT validation
 * - User synchronization between Keycloak and User Service
 * - Request routing to downstream microservices
 * - Load balancing through Spring Cloud
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.fitness.gateway", "com/fitness/gateway/user"})
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}
}
