package com.fitness.gateway;


import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import com.fitness.gateway.user.RegisterRequest;
import com.fitness.gateway.user.UserService;

/**
 * Filter to synchronize Keycloak authenticated users with the internal User Service.
 * This filter intercepts requests containing JWT tokens, validates if the user exists 
 * in the User Service, and registers them if they don't exist.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KeycloakUserSyncFilter implements WebFilter {
    private final UserService userService;
    
    /**
     * Main filter method that processes incoming requests.
     * Checks if user exists in User Service and creates them if necessary.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain)
    {
        // Extract user ID and JWT token from request headers
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");

        // Process only if both userId and token are present
        if(userId != null && token != null)
        {
            return userService.validateUser(userId)
                    .flatMap(exist -> {
                        // User doesn't exist in User Service - need to register
                        if(!exist) {
                            log.info("User not found in User Service, attempting to register userId: {}", userId);
                            
                            // Extract user details from JWT token
                            RegisterRequest registerRequest = getUserDetails(token);
                            
                            if(registerRequest != null)
                            {
                                // Register the user in User Service
                                return userService.registerUser(registerRequest)
                                        .then(Mono.empty());
                            } else {
                                log.error("Failed to extract user details from token for userId: {}", userId);
                                return Mono.empty();
                            }
                        }
                        else {
                            // User already exists - proceed with request
                            log.info("User exists, proceeding with request for userId: {}", userId);
                            return Mono.empty();
                        }
                    })
                    // Continue the filter chain after user validation/registration
                    .then(Mono.defer(() -> {
                        // Propagate user ID and token to downstream services
                        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                .header("X-User-Id", userId)
                                .header("Authorization", token)
                                .build();

                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    }));
        }
        
        // No userId or token present - continue without user sync
        return chain.filter(exchange);
    }

    /**
     * Extracts user details from JWT token.
     * Parses the JWT and creates a RegisterRequest object with user information.
     * 
     * @param token The Authorization header value containing the JWT
     * @return RegisterRequest object with user details, or null if parsing fails
     */
    private RegisterRequest getUserDetails(String token) {
        try {
            // Remove "Bearer " prefix from token
            String tokenWithoutBearer = token.replace("Bearer ", "").trim();
            
            // Parse the JWT token
            SignedJWT signedJWT = SignedJWT.parse(tokenWithoutBearer);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            // Create registration request from JWT claims
            RegisterRequest registerRequest = new RegisterRequest();
            registerRequest.setKeycloakId(claims.getStringClaim("sub"));
            registerRequest.setEmail(claims.getStringClaim("email"));
            registerRequest.setPassword("dummy@123123"); // Placeholder password
            registerRequest.setFirstName(claims.getStringClaim("given_name"));
            registerRequest.setLastName(claims.getStringClaim("family_name"));
            
            return registerRequest;
            
        } catch(Exception e) {
            log.error("Error parsing JWT token: {}", e.getMessage(), e);
            return null;
        }
    }
}
