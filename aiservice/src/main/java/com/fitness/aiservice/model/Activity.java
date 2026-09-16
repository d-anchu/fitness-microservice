package com.fitness.aiservice.model;


import lombok.Data;

import org.springframework.data.annotation.Id;


import java.time.LocalDateTime;
import java.util.Map;


@Data


public class Activity {
    @Id
    private String id;
    private String userId;
    private String type;
    private Integer duration;
    private Integer caloriesBurned;
    private Double distance;
    private Integer heartRate;
    private LocalDateTime startTime;


    private Map<String,Object> additionMetrics;


    private LocalDateTime createdAt;


    private LocalDateTime updatedAt;

}
