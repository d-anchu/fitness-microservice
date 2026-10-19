package com.fitness.activityservice.config;

import com.mongodb.client.MongoClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing
public class MongoConfig extends AbstractMongoClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    @Value("${spring.data.mongodb.database:fitnessactivity}")
    private String databaseName;

    @PostConstruct
    public void logDatabaseName() {
        log.info("===============================================");
        log.info("MongoDB Configuration - Using database: {}", databaseName);
        log.info("===============================================");
    }

    @Override
    protected String getDatabaseName() {
        return databaseName;
    }
}