# ============================================
# STAGE 1: BUILD
# ============================================
# Multi-stage build for Config Server
# This stage compiles the Spring Boot application
FROM maven:3.9.6-eclipse-temurin-17 AS build

# Set working directory
WORKDIR /app

# Copy pom.xml first (leverages Docker layer caching)
# Maven dependencies are cached if pom.xml doesn't change
COPY pom.xml .

# Copy source code
COPY src ./src

# Build the application and create executable JAR
# Package phase: compile → test → package into JAR
RUN mvn clean package -DskipTests

# ============================================
# STAGE 2: RUNTIME
# ============================================
# Smaller runtime image with only JRE (no build tools)
FROM eclipse-temurin:17-jre-alpine

# Install wget for health check endpoint testing
RUN apk add --no-cache wget

# Set working directory for the application
WORKDIR /app

# Copy only the JAR file from build stage (not source code or build artifacts)
# This keeps the final image small and secure
COPY --from=build /app/target/*.jar app.jar

# Expose Config Server port
# Config Server serves configuration to other microservices
EXPOSE 8888

# Configure Docker health check
# Checks Spring Boot Actuator health endpoint
# If this fails 3 times, Docker marks container as unhealthy
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8888/actuator/health || exit 1

# Start the Config Server application
ENTRYPOINT ["java", "-jar", "app.jar"]
