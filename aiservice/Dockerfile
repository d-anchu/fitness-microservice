# ============================================
# STAGE 1: BUILD
# ============================================
# Build stage for AI Service
# AI Service generates personalized fitness recommendations using GROQ AI
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copy Maven configuration
COPY pom.xml .

# Copy AI Service source code
COPY src ./src

# Build AI Service
# This service integrates with GROQ API for AI-powered recommendations
RUN mvn clean package -DskipTests

# ============================================
# STAGE 2: RUNTIME
# ============================================
# Runtime environment for AI Service
FROM eclipse-temurin:17-jre-alpine

# Install wget for health checks
RUN apk add --no-cache wget

WORKDIR /app

# Copy the built JAR file
COPY --from=build /app/target/*.jar app.jar

# AI Service port
# Listens to RabbitMQ for activity events
# Calls GROQ API to generate recommendations
# Saves recommendations to MongoDB
EXPOSE 8083

# Environment variable for GROQ API Key
# This will be injected at runtime from docker-compose or GitHub Secrets
# Format: gsk_XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
ENV GROQ_API_KEY=${GROQ_API_KEY}

# Health check for AI Service
# Verifies:
# - Service is running
# - MongoDB connection (for storing recommendations)
# - RabbitMQ connection (for consuming activity events)
# Note: Doesn't check GROQ API (would cause rate limiting)
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8083/actuator/health || exit 1

# Start AI Service
ENTRYPOINT ["java", "-jar", "app.jar"]
