# ============================================
# STAGE 1: BUILD
# ============================================
# Build stage for Activity Service
# Activity Service manages fitness activities (running, cycling, etc.)
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copy Maven project file
COPY pom.xml .

# Copy Activity Service source code
COPY src ./src

# Build Activity Service
# This service uses MongoDB for data persistence
RUN mvn clean package -DskipTests

# ============================================
# STAGE 2: RUNTIME
# ============================================
# Production runtime image
FROM eclipse-temurin:17-jre-alpine

# Install wget for health checks
RUN apk add --no-cache wget

WORKDIR /app

# Copy compiled JAR file
COPY --from=build /app/target/*.jar app.jar

# Activity Service port
# Endpoints: /api/activities (GET, POST, PUT, DELETE)
EXPOSE 8082

# Health check for Activity Service
# Verifies:
# - Service is running
# - MongoDB connection is healthy
# - RabbitMQ connection is healthy (for publishing activity events)
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

# Start Activity Service
ENTRYPOINT ["java", "-jar", "app.jar"]
