package com.fitness.gateway;


import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// NOTE: this gateway is the SERVLET (WebMVC) flavour of Spring Cloud Gateway
// (see pom.xml -> spring-cloud-starter-gateway-server-webmvc, and the startup log
// "Starting Servlet engine: [Apache Tomcat/11.0.24]"), so it must use the servlet
// security API:
//   HttpSecurity                        (not ServerHttpSecurity)
//   org.springframework.security.web.SecurityFilterChain (not SecurityWebFilterChain)
//   authorizeHttpRequests(...)          (not authorizeExchange(...))
//
// The reactive classes are still visible on the classpath — spring-security-config and
// spring-security-web ship both flavours — so reactive imports COMPILE, but Boot only
// auto-configures the reactive stack for a REACTIVE web application
// (ReactiveWebSecurityAutoConfiguration is @ConditionalOnWebApplication(type = REACTIVE)).
// In this servlet app no ServerHttpSecurity bean is ever created, so a reactive chain
// would be dead code at best and fail at startup at worst.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // NOTE: @Bean methods must be declared as members of the class. In the previous
    // revision this method was declared INSIDE the (otherwise empty) SecurityConfig
    // constructor, which is not valid Java and failed the build with:
    //   SecurityConfig.java:[18,64] ';' expected
    //   SecurityConfig.java:[18,88] ';' expected
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                // Requests authenticate with a bearer JWT, not with a session/cookie,
                // so no CSRF token is issued or checked.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        // Public health/readiness probes. Harmless if actuator is not on
                        // the classpath (the gateway pom does not include it yet).
                        .requestMatchers("/actuator/**").permitAll()
                        // REQUIRED with a servlet gateway: when no route matches (or a
                        // downstream call blows up) Spring's BasicErrorController renders
                        // the response through an ERROR dispatch to /error. That dispatch
                        // is a brand-new filter invocation, and SecurityContextHolderFilter /
                        // BearerTokenAuthenticationFilter skip ERROR dispatches, so the
                        // security context is empty there and `anyRequest().authenticated()`
                        // would answer 401 instead of the real 404/500 — masking every error,
                        // including /actuator/health once actuator is added.
                        // Verified live: "Securing GET /actuator/health" was immediately
                        // followed by "Securing GET /error" and the client saw 401, not 404.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Every other exchange must carry a valid JWT.
                        .anyRequest().authenticated()
                )
                // JWT validation keyed off
                // spring.security.oauth2.resourceserver.jwt.jwk-set-uri
                // (served by configserverc -> config/api-gateway.yml, Keycloak realm
                // fitness-oauth2). Boot builds the servlet JwtDecoder from that property.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-User-Id"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
