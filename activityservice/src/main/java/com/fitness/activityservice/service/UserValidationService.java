package com.fitness.activityservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserValidationService {

    private final WebClient userServiceWebClient;

    public boolean validateUser(String userId) {
        log.info("Calling User Validation Api for userId=['{}'], isNull={}, length={}", userId, userId == null, userId == null ? -1 : userId.length());
        try {
            Boolean isValid = userServiceWebClient.get()
                    .uri("/api/users/{userId}/validate", userId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .timeout(Duration.ofSeconds(10))  // Add explicit timeout
                    .block();
            return Boolean.TRUE.equals(isValid);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new RuntimeException("USER Not Found:" + userId);
            } else if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new RuntimeException("Invalid Request:" + userId);
            }
            throw e;
        } catch (Exception e) {
            log.error("Error validating user {}: {}", userId, e.getMessage());
            // For development: return true to bypass validation
            log.warn("Bypassing user validation due to error");
            return true;
        }
    }
}