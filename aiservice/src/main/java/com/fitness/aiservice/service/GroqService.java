package com.fitness.aiservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    @Value("${groq.api.url}")
    private String groqApiUrl;

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.api.model}")
    private String groqModel;

    public GroqService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * Calls the Groq chat-completions endpoint and returns ONLY the assistant
     * message content (choices[0].message.content) as a JSON string.
     * <p>
     * The HTTP body itself is an OpenAI-compatible envelope (id/object/usage/
     * choices/reasoning...), so returning it verbatim would hand the caller the
     * envelope instead of the model's answer.
     */
    public String getAnswer(String question) {
        // Groq is OpenAI-compatible: { "model": ..., "messages": [{ "role": ..., "content": ... }] }
        // response_format=json_object makes Groq guarantee the content is a valid JSON object.
        Map<String, Object> requestBody = Map.of(
                "model", groqModel,
                "messages", List.of(
                        Map.of("role", "user", "content", question)
                ),
                "response_format", Map.of("type", "json_object"),
                "max_tokens", 2048   // ← add this — gives enough room for detailed responses
        );

        String rawResponse = webClient.post()
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

        log.debug("RAW RESPONSE FROM GROQ: {}", rawResponse);
        return extractMessageContent(rawResponse);
    }

    /**
     * Pulls choices[0].message.content out of the Groq envelope. The "reasoning"
     * field that reasoning models (gpt-oss) emit lives next to "content" and is
     * intentionally ignored.
     * <p>
     * Package-private so it can be unit tested without hitting the live API.
     */
    String extractMessageContent(String rawResponse) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawResponse);
        } catch (JsonProcessingException e) {
            throw new GroqApiException(200, "Groq response was not valid JSON: " + rawResponse);
        }

        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
            throw new GroqApiException(200, "Groq response contained no choices[0].message.content: " + rawResponse);
        }
        return stripMarkdownFences(content.asText());
    }

    /**
     * Defensive only: even in json_object mode some models still wrap the object
     * in a ```json ... ``` fence.
     */
    private String stripMarkdownFences(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        String withoutOpeningFence = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
        return withoutOpeningFence.replaceFirst("\\s*```$", "").trim();
    }
}