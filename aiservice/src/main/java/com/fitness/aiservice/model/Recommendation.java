package com.fitness.aiservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "recommendations")
@Data
@Builder
@NoArgsConstructor   // ← MongoDB needs this to instantiate the class
@AllArgsConstructor  // ← required when @Builder is present
public class Recommendation {
    @Id
    private String id;
    private String activityId;
    private String userId;
    private String activityType;
    private String recommendation;
    private List<String> suggestions;
    private List<String> safety;

    @CreatedDate
    private LocalDateTime createdAt;
}