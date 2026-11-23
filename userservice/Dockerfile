# ============================================
# STAGE 1: BUILD
# ============================================
# Build stage for User Service
# User Service manages user registration, authentication, and profiles
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copy Maven dependencies configuration
COPY pom.xml .

# Copy User Service source code
COPY src ./src

# Compile and package User Service
# Creates user-service.jar in target/ directory
RUN mvn clean package -DskipTests

# ============================================
# STAGE 2: RUNTIME
# ============================================
# Lightweight runtime environment
FROM eclipse-temurin:17-jre-alpine

# Install wget for health endpoint checks
RUN apk add --no-cache wget

WORKDIR /app

# Copy only the executable JAR (keeps image small)
COPY --from=build /app/target/*.jar app.jar

# User Service listens on port 8081
# Handles: /api/users/register, /api/users/login, etc.
EXPOSE 8081

# Health check configuration
# Verifies User Service is responding to requests
# Important: Other services depend on this for user validation
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# Launch User Service
ENTRYPOINT ["java", "-jar", "app.jar"]
