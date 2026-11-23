# ============================================
# STAGE 1: BUILD
# ============================================
# Build stage for API Gateway
# API Gateway routes requests to appropriate microservices
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copy Maven configuration
COPY pom.xml .

# Copy application source
COPY src ./src

# Build the gateway application
# API Gateway uses Spring Cloud Gateway for routing
RUN mvn clean package -DskipTests

# ============================================
# STAGE 2: RUNTIME
# ============================================
# Production runtime image
FROM eclipse-temurin:17-jre-alpine

# Install wget for health checks
RUN apk add --no-cache wget

WORKDIR /app

# Copy compiled JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# API Gateway exposes port 8080
# This is the main entry point for all client requests
# Requests are routed to User Service (8081), Activity Service (8082), etc.
EXPOSE 8080

# Health check for API Gateway
# Critical: If gateway is down, entire system is inaccessible
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Start API Gateway
ENTRYPOINT ["java", "-jar", "app.jar"]
