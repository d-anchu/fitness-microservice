package com.fitness.gateway.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Service to interact with the User Service microservice.
 * Provides methods to validate and register users.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final WebClient userServiceWebClient;

    /**
     * Validates if a user exists in the User Service.
     * 
     * @param userId The user ID to validate
     * @return Mono<Boolean> - true if user exists, false otherwise
     */
    public Mono<Boolean> validateUser(String userId) {
        log.info("Calling User Validation API for userId=['{}'], isNull={}, length={}", 
                userId, userId == null, userId == null ? -1 : userId.length());
        
        return userServiceWebClient.get()
                .uri("/api/users/{userId}/validate", userId)
                .retrieve()
                .bodyToMono(Boolean.class)
                .onErrorResume(WebClientResponseException.class, e -> {
                    // Handle NOT_FOUND - User doesn't exist in the system
                    if(e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        log.warn("User not found in User Service: {}", userId);
                        return Mono.just(false); // Return false instead of error
                    }
                    // Handle BAD_REQUEST - Invalid user ID format or validation failed
                    else if(e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                        log.error("Invalid request for user validation: {}", userId);
                        return Mono.error(new RuntimeException("Invalid user ID format: " + userId));
                    }
                    // Handle other unexpected errors
                    log.error("Unexpected error during user validation: {}", e.getMessage());
                    return Mono.error(new RuntimeException("User validation failed: " + e.getMessage()));
                });
    }

    /**
     * Registers a new user in the User Service.
     * 
     * @param request RegisterRequest containing user details
     * @return Mono<UserResponse> - The created user details
     */
    public Mono<UserResponse> registerUser(RegisterRequest request) {
        log.info("Calling User Registration API for email=['{}']", request.getEmail());
        
        return userServiceWebClient.post()
                .uri("/api/users/register")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(UserResponse.class)
                .onErrorResume(WebClientResponseException.class, e -> {
                    // Handle CONFLICT - User already exists (duplicate email/keycloakId)
                    if(e.getStatusCode() == HttpStatus.CONFLICT) {
                        log.error("User already exists with email: {}", request.getEmail());
                        return Mono.error(new RuntimeException("User already exists: " + request.getEmail()));
                    }
                    // Handle BAD_REQUEST - Invalid registration data
                    else if(e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                        log.error("Invalid registration request for email: {}", request.getEmail());
                        return Mono.error(new RuntimeException("Invalid registration data: " + e.getMessage()));
                    }
                    // Handle INTERNAL_SERVER_ERROR - Server-side issue
                    else if(e.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
                        log.error("Internal server error during user registration: {}", e.getMessage());
                        return Mono.error(new RuntimeException("User registration failed due to server error"));
                    }
                    // Handle other unexpected errors
                    log.error("Unexpected error during user registration: {}", e.getMessage());
                    return Mono.error(new RuntimeException("User registration failed: " + e.getMessage()));
                });
    }
}

