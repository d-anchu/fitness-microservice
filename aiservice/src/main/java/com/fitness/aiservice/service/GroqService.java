package com.fitness.aiservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GroqService {

    private final WebClient webClient;

    @Value("${groq.api.url}")
    private String groqApiUrl;

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.api.model}")
    private String groqModel;

    public GroqService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String getAnswer(String question) {
        // Groq is OpenAI-compatible: { "model": ..., "messages": [{ "role": ..., "content": ... }] }
        Map<String, Object> requestBody = Map.of(
                "model", groqModel,
                "messages", List.of(
                        Map.of("role", "user", "content", question)
                )
        );

        return webClient.post()
                .uri(groqApiUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + groqApiKey)
                .bodyValue(requestBody)
                .retrieve()
                // Surface Groq's error body (invalid key, unknown model, rate limit) instead of a bare exception.
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            log.error("Groq API call failed - status={} body={}", response.statusCode().value(), body);
                            return Mono.error(new GroqApiException(response.statusCode().value(), body));
                        }))
                .bodyToMono(String.class)
                .block();
    }
}