package com.fitness.aiservice.service;

import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityAIService {

    private final GroqService groqService;

    public String generateRecommendation(Activity activity) {
        String prompt = createPromptForActivity(activity);
        String aiResponse = groqService.getAnswer(prompt);
        log.info("RESPONSE FROM AI: {} ", aiResponse);
        return aiResponse;
    }

    private String createPromptForActivity(Activity activity) {
        String type = activity.getType() != null ? activity.getType() : "UNKNOWN";
        int duration = activity.getDuration() != null ? activity.getDuration() : 0;
        int calories = activity.getCaloriesBurned() != null ? activity.getCaloriesBurned() : 0;

        // distance & heart rate aren't top-level fields on the wire yet —
        // fall back to the additionMetrics map before defaulting to 0.
        Map<String, Object> metrics = activity.getAdditionMetrics();
        Double distance = activity.getDistance();
        if (distance == null) {
            if (metrics != null && metrics.get("distance") instanceof Number n) {
                distance = n.doubleValue();
            } else {
                distance = 0.0;
            }
        }
        Integer heartRate = activity.getHeartRate();
        if (heartRate == null) {
            if (metrics != null && metrics.get("heartRate") instanceof Number n) {
                heartRate = n.intValue();
            } else {
                heartRate = 0;
            }
        }

        return String.format("""
            Analyze this fitness activity and provide detailed recommendations in the following JSON format:
            {
              "analysis": {
                "overall": "Overall analysis here",
                "pace": "Pace analysis here",
                "heartRate": "Heart rate analysis here",
                "caloriesBurned": "Calories analysis here"
              },
              "recommendations": [
                "Recommendation 1",
                "Recommendation 2"
              ],
              "improvements": [
                "Improvement suggestion 1",
                "Improvement suggestion 2"
              ]
            }

            Activity Details:
            - Type: %s
            - Duration: %d minutes
            - Calories Burned: %d
            - Distance: %.2f km
            - Heart Rate: %d bpm
            """,
                type,
                duration,
                calories,
                distance,
                heartRate
        );
    }
}