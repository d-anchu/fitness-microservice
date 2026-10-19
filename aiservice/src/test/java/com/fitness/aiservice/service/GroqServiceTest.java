package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Groq envelope unwrapping. No HTTP is involved: the
 * service is built with a plain WebClient.Builder and only the pure JSON
 * handling method is exercised.
 */
class GroqServiceTest {

    private final GroqService groqService = new GroqService(WebClient.builder(), new ObjectMapper());

    /**
     * Mirrors a real Groq chat-completions response: the answer we care about is
     * nested in choices[0].message.content, surrounded by envelope noise
     * (id/usage/reasoning) that must NOT be returned to the caller.
     */
    @Test
    void extractsOnlyTheAssistantContentFromTheGroqEnvelope() {
        String envelope = """
                {
                  "id": "chatcmpl-479ef8dc-9eba-45cd-afde-ad87d3684536",
                  "object": "chat.completion",
                  "model": "openai/gpt-oss-120b",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "{\\"analysis\\":{\\"overall\\":\\"Solid run\\"},\\"recommendations\\":[\\"Keep going\\"]}",
                        "reasoning": "User wants a JSON response..."
                      },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {"total_tokens": 1254},
                  "x_groq": {"id": "req_01m2vmsngxer7b9cecqewwchb2"}
                }
                """;

        String result = groqService.extractMessageContent(envelope);

        assertEquals("{\"analysis\":{\"overall\":\"Solid run\"},\"recommendations\":[\"Keep going\"]}", result);
        assertFalse(result.contains("chatcmpl"), "envelope id must not leak into the answer");
        assertFalse(result.contains("reasoning"), "reasoning must not leak into the answer");
    }

    /**
     * Defensive path: some models still wrap the object in a ```json fence even
     * when response_format=json_object is requested.
     */
    @Test
    void stripsMarkdownCodeFences() {
        String envelope = """
                {
                  "choices": [
                    { "message": { "content": "```json\\n{\\"analysis\\":{\\"overall\\":\\"ok\\"}}\\n```" } }
                  ]
                }
                """;

        String result = groqService.extractMessageContent(envelope);

        assertEquals("{\"analysis\":{\"overall\":\"ok\"}}", result);
        assertTrue(result.startsWith("{"));
        assertTrue(result.endsWith("}"));
    }

    @Test
    void throwsWhenTheEnvelopeHasNoChoicesContent() {
        String envelope = """
                {"id":"chatcmpl-x","choices":[]}
                """;

        GroqApiException ex = assertThrows(GroqApiException.class,
                () -> groqService.extractMessageContent(envelope));

        assertTrue(ex.getMessage().contains("no choices[0].message.content"));
    }

    @Test
    void throwsWhenTheResponseIsNotJson() {
        assertThrows(GroqApiException.class,
                () -> groqService.extractMessageContent("<html>502 Bad Gateway</html>"));
    }
}
