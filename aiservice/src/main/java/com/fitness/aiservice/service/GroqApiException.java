package com.fitness.aiservice.service;

/**
 * Thrown when the Groq API answers with a non-2xx status.
 * Keeps the HTTP status and the raw error body so failures (invalid key,
 * unknown model, rate limit) are visible in the log instead of a bare
 * WebClientResponseException with no payload.
 */
public class GroqApiException extends RuntimeException {

    private final int statusCode;

    public GroqApiException(int statusCode, String responseBody) {
        super("Groq API returned HTTP " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}