package com.fitness.gateway.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for user registration.
 * Contains user details extracted from Keycloak JWT token or registration form.
 */
@Data
public class RegisterRequest {
    /**
     * User's email address - must be valid email format
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    /**
     * User's password - minimum 6 characters
     */
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must have at least 6 characters")
    private String password;

    /**
     * Keycloak user ID (sub claim from JWT)
     * Used to link internal user with Keycloak identity
     */
    private String keycloakId;

    /**
     * User's first name (given_name claim from JWT)
     */
    private String firstName;
    
    /**
     * User's last name (family_name claim from JWT)
     */
    private String lastName;
}
