package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityAIService {

    // ── injected by @RequiredArgsConstructor (Lombok) ────────────────────────
    private final GroqService groqService;
    private final RecommendationRepository recommendationRepository;

    // ── entry point called from ActivityMessageListener ──────────────────────
    public String generateRecommendation(Activity activity) {
        log.error("═══════════════════════════════════════════════════════════");
        log.error("🔥 GENERATE RECOMMENDATION CALLED FOR ACTIVITY: {}", activity.getId());
        log.error("   User ID: {}", activity.getUserId());
        log.error("   Type: {}", activity.getTypeAsString());
        log.error("═══════════════════════════════════════════════════════════");
        
        String prompt     = createPromptForActivity(activity);
        log.error("📝 Prompt created, calling GROQ...");
        
        String aiResponse = groqService.getAnswer(prompt);  // already clean JSON — no candidates wrapper
        log.error("✅ GROQ RESPONSE RECEIVED, length: {}", aiResponse.length());
        log.error("RESPONSE FROM AI: {}", aiResponse);
        
        processAIResponse(activity, aiResponse);
        log.error("💾 processAIResponse completed");
        log.error("═══════════════════════════════════════════════════════════");
        
        return aiResponse;
    }

    // ── parse clean JSON → build Recommendation → save to MongoDB ────────────
    private void processAIResponse(Activity activity, String aiResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(aiResponse);

            log.info("PARSED RESPONSE FROM AI: {}", root.toPrettyString());

            // analysis.overall → recommendation (String)
            String overall = root.path("analysis")
                    .path("overall")
                    .asText("No analysis available");

            // recommendations[] → simple string array
            List<String> suggestions = new ArrayList<>();
            root.path("recommendations")
                    .forEach(node -> suggestions.add(extractText(node)));

            // improvements[] → may be objects {"area":..., "recommendation":...}
            root.path("improvements")
                    .forEach(node -> {
                        if (node.isObject()) {
                            // extract both area and recommendation as one line
                            String area = node.path("area").asText("");
                            String rec  = node.path("recommendation").asText("");
                            if (!rec.isEmpty()) suggestions.add(area.isEmpty() ? rec : area + ": " + rec);
                        } else {
                            suggestions.add(node.asText());
                        }
                    });

            // suggestions[] → may be objects {"workout":..., "description":...}
            root.path("suggestions")
                    .forEach(node -> {
                        if (node.isObject()) {
                            String workout = node.path("workout").asText("");
                            String desc    = node.path("description").asText("");
                            if (!desc.isEmpty()) suggestions.add(workout.isEmpty() ? desc : workout + ": " + desc);
                        } else {
                            suggestions.add(node.asText());
                        }
                    });

            // safety[] → may be objects or plain strings
            List<String> safety = new ArrayList<>();
            root.path("safety")
                    .forEach(node -> {
                        String text = extractText(node);
                        if (!text.isEmpty()) safety.add(text);  // skip empty strings
                    });

            Recommendation recommendation = Recommendation.builder()
                    .activityId(activity.getId())
                    .userId(activity.getUserId())
                    .activityType(activity.getTypeAsString())
                    .recommendation(overall)
                    .suggestions(suggestions)
                    .safety(safety)
                    .build();

            log.error("📦 Built recommendation object:");
            log.error("   Activity ID: {}", recommendation.getActivityId());
            log.error("   User ID: {}", recommendation.getUserId());
            log.error("   Type: {}", recommendation.getActivityType());
            log.error("   Suggestions count: {}", suggestions.size());
            log.error("   Safety count: {}", safety.size());
            log.error("💾 Calling repository.save()...");
            
            Recommendation saved = recommendationRepository.save(recommendation);
            
            log.error("✅ SAVE RETURNED! Saved recommendation ID: {}", saved.getId());
            log.info("Saved recommendation for activity: {}", activity.getId());

        } catch (Exception e) {
            log.error("Failed to parse AI response for activity {}: {}",
                    activity.getId(), e.getMessage());
        }
    }

    // ── handles both plain string nodes and object nodes gracefully ──────────
    private String extractText(JsonNode node) {
        if (node.isTextual()) return node.asText();
        if (node.isObject()) {
            // try common wrapper keys the AI might use
            for (String key : new String[]{"tip", "text", "value", "content"}) {
                if (node.has(key)) return node.path(key).asText();
            }
            return node.toString(); // fallback: raw JSON string
        }
        return node.asText();
    }

    // ── build the prompt sent to Groq ────────────────────────────────────────
    private String createPromptForActivity(Activity activity) {
        String type  = activity.getTypeAsString();
        int duration = activity.getDuration()       != null ? activity.getDuration()       : 0;
        int calories = activity.getCaloriesBurned() != null ? activity.getCaloriesBurned() : 0;

        Map<String, Object> metrics = activity.getAdditionMetrics();
        double distance = resolveMetric(activity.getDistance(), metrics, "distance");
        int heartRate   = (int) resolveMetric(
                activity.getHeartRate() != null
                        ? activity.getHeartRate().doubleValue()
                        : null,
                metrics, "heartRate");

        return String.format("""
            You are an expert fitness coach. Analyze the activity below and return a detailed,
            personalized fitness report as a single raw JSON object.

            STRICT OUTPUT RULES:
            - Return ONLY the JSON object. No markdown, no code fences, no extra text.
            - Every array must contain exactly 3 items minimum.
            - Every string value must be detailed (2-3 sentences minimum). No one-liners.
            - All arrays must contain plain strings only — no nested objects.

            JSON structure:
            {
              "analysis": {
                "overall":        "Comprehensive 2-3 sentence summary covering effort level, performance quality, and key highlights of this session.",
                "pace":           "Detailed pace breakdown — average speed, what training zone this represents, how it compares to recreational or competitive benchmarks.",
                "heartRate":      "Detailed HR analysis — which zone (1-5), what physiological adaptations this drives, and whether it was appropriate for this activity type.",
                "caloriesBurned": "Detailed calorie context — burn rate per minute, how this compares to similar activities, and what this means for the user's energy balance."
              },
              "recommendations": [
                "Detailed actionable recommendation with specific guidance on what to do, how often, and why it benefits this user based on their current stats.",
                "Second detailed recommendation with specific metrics or techniques mentioned.",
                "Third recommendation focused on recovery, nutrition, or lifestyle that complements this activity."
              ],
              "improvements": [
                "Specific area to improve with a concrete drill, method or technique — mention sets, reps, duration or frequency where relevant.",
                "Second improvement area with measurable targets the user can track.",
                "Third improvement with a clear reason why it matters for this activity type."
              ],
              "suggestions": [
                "Specific complementary workout with name, duration, intensity and how it benefits this user's current fitness level.",
                "Second workout suggestion targeting a different energy system or muscle group.",
                "Third workout suggestion for active recovery or cross-training."
              ],
              "safety": [
                "Specific safety guideline relevant to this activity type, intensity and heart rate — not generic advice.",
                "Second safety tip covering equipment, environment or injury prevention for this activity.",
                "Third safety tip about signs of overtraining or when to rest based on this session's load."
              ]
            }

            Activity details:
              Type              : %s
              Duration          : %d minutes
              Calories Burned   : %d kcal
              Distance          : %.2f km
              Average Heart Rate: %d bpm

            Return ONLY the JSON object. No other text before or after it.
            """,
                type, duration, calories, distance, heartRate
        );
    }

    // ── resolve a metric: direct field → metrics map → 0.0 default ──────────
    private double resolveMetric(Double directValue,
                                 Map<String, Object> metrics,
                                 String key) {
        if (directValue != null) return directValue;
        if (metrics != null && metrics.get(key) instanceof Number n) return n.doubleValue();
        return 0.0;
    }
}