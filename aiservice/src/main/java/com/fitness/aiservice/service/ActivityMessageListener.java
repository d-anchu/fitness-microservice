package com.fitness.aiservice.service;

import com.fitness.aiservice.model.Activity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityMessageListener {

    private final ActivityAIService aiService;

    @RabbitListener(queues = "activity.queue")
    public void processActivity(Activity activity) {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("Received Activity for processing:");
        log.info("  Activity ID: {}", activity.getId());
        log.info("  User ID: {}", activity.getUserId());
        log.info("  Type: {}", activity.getTypeAsString());
        log.info("  Duration: {}", activity.getDuration());
        log.info("  Calories: {}", activity.getCaloriesBurned());
        log.info("═══════════════════════════════════════════════════════════");
        
        try {
            log.info("Calling AI Service to generate recommendation...");
            // ActivityAIService handles both AI generation AND saving to MongoDB
            String aiResponse = aiService.generateRecommendation(activity);
            log.info("✅ Successfully generated recommendation!");
            log.info("AI Response length: {} characters", aiResponse.length());
            log.info("═══════════════════════════════════════════════════════════");
        } catch (Exception e) {
            log.error("❌ FAILED to process recommendation for activity {}", activity.getId());
            log.error("Error type: {}", e.getClass().getName());
            log.error("Error message: {}", e.getMessage());
            log.error("Stack trace:", e);
            log.error("═══════════════════════════════════════════════════════════");
        }
    }
}