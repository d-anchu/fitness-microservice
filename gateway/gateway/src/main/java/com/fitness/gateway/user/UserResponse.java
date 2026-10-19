package com.fitness.gateway.user;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response DTO representing a user entity.
 * Returned after user registration or user queries.
 */
@Data
public class UserResponse {
    /**
     * Internal user ID (MongoDB/database ID)
     */
    private String id;
    
    /**
     * Keycloak user ID - links to Keycloak identity provider
     */
    private String keycloakId;

    /**
     * User's email address
     */
    private String email;

    /**
     * User's password (hashed) - should not be exposed in actual responses
     */
    private String password;
    
    /**
     * User's first name
     */
    private String firstName;
    
    /**
     * User's last name
     */
    private String lastName;

    /**
     * Timestamp when the user was created
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp when the user was last updated
     */
    private LocalDateTime updatedAt;
}
