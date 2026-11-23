# ============================================
# STAGE 1: BUILD
# ============================================
# Use Maven with Java 17 as the build environment
# This stage compiles the Spring Boot application
FROM maven:3.9.6-eclipse-temurin-17 AS build

# Set working directory inside the container
WORKDIR /app

# Copy only pom.xml first for better Docker layer caching
# If pom.xml doesn't change, Docker reuses this layer
COPY pom.xml .

# Copy source code
COPY src ./src

# Build the application
# -DskipTests: Skip running tests during build (tests run in CI/CD)
# This creates a JAR file in target/ directory
RUN mvn clean package -DskipTests

# ============================================
# STAGE 2: RUNTIME
# ============================================
# Use smaller JRE-only image for running the application
# Alpine variant is lightweight (~150MB vs ~400MB for full JDK)
FROM eclipse-temurin:17-jre-alpine

# Install wget for health checks
# --no-cache: Don't cache the package index (keeps image smaller)
RUN apk add --no-cache wget

# Set working directory
WORKDIR /app

# Copy the built JAR file from the build stage
# This creates a smaller final image (only runtime dependencies)
COPY --from=build /app/target/*.jar app.jar

# Expose port 8761 (Eureka Server default port)
# This is documentation - actual port mapping happens in docker-compose
EXPOSE 8761

# Health check configuration
# Docker will periodically check if the container is healthy
# --interval=30s: Check every 30 seconds
# --timeout=3s: Fail if check takes more than 3 seconds
# --start-period=60s: Wait 60 seconds before first check (service startup time)
# --retries=3: Mark unhealthy after 3 consecutive failures
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8761/actuator/health || exit 1

# Run the application
# This is the command that runs when container starts
ENTRYPOINT ["java", "-jar", "app.jar"]
