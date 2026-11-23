#!/bin/bash
# ============================================
# HEALTH CHECK SCRIPT
# ============================================
# Verifies that all microservices are healthy and responding
#
# Usage:
#   ./health-check.sh [project-name]
#
# Example:
#   ./health-check.sh fitness-blue
#   ./health-check.sh fitness-green
#
# Health check endpoints:
# - Spring Boot services use /actuator/health
# - Frontend uses custom /health endpoint

# Project name from argument (defaults to fitness-blue)
PROJECT_NAME=${1:-fitness-blue}

echo "🏥 Running health checks for: $PROJECT_NAME"
echo "================================================"

# ============================================
# HEALTH CHECK CONFIGURATION
# ============================================
# Define all services and their health check endpoints
# Format: ["service-name"]="http://host:port/health-endpoint"

declare -A SERVICES=(
    # Service Registry
    ["eureka-server"]="http://localhost:8761/actuator/health"
    
    # Configuration Server
    ["config-server"]="http://localhost:8888/actuator/health"
    
    # API Gateway (main entry point)
    ["api-gateway"]="http://localhost:8080/actuator/health"
    
    # User Service (authentication & user management)
    ["user-service"]="http://localhost:8081/actuator/health"
    
    # Activity Service (fitness tracking)
    ["activity-service"]="http://localhost:8082/actuator/health"
    
    # AI Service (recommendations)
    ["ai-service"]="http://localhost:8083/actuator/health"
    
    # Frontend (React app)
    ["frontend"]="http://localhost:5173/health"
)

# Track failures
FAILED=0
TOTAL=${#SERVICES[@]}
PASSED=0

# ============================================
# RUN HEALTH CHECKS FOR ALL SERVICES
# ============================================
echo ""
echo "Checking $TOTAL services..."
echo ""

for service in "${!SERVICES[@]}"; do
    # Get the health check URL for this service
    url=${SERVICES[$service]}
    
    # Print service being checked
    printf "%-20s" "$service"
    
    # Perform health check
    # -s: Silent mode (no progress bar)
    # -o /dev/null: Discard response body
    # -w "%{http_code}": Output only HTTP status code
    # --max-time 5: Timeout after 5 seconds
    # || echo "000": If curl fails, return "000"
    response=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 $url 2>/dev/null || echo "000")
    
    # Check if response is 200 OK
    if [ "$response" == "200" ]; then
        # Service is healthy
        echo -e " ✅ HEALTHY   (HTTP $response)"
        PASSED=$((PASSED + 1))
    else
        # Service is unhealthy or unreachable
        echo -e " ❌ UNHEALTHY (HTTP $response)"
        FAILED=1
        
        # Additional diagnostics
        if [ "$response" == "000" ]; then
            echo "    └─ Connection failed (service not responding)"
        elif [ "$response" == "503" ]; then
            echo "    └─ Service unavailable (may still be starting up)"
        elif [ "$response" == "404" ]; then
            echo "    └─ Health endpoint not found (check configuration)"
        fi
    fi
done

# ============================================
# SUMMARY
# ============================================
echo ""
echo "================================================"
echo "Health Check Summary"
echo "================================================"
echo "Total services:   $TOTAL"
echo "Passed:           $PASSED"
echo "Failed:           $((TOTAL - PASSED))"
echo "================================================"

# ============================================
# DETAILED DEPENDENCY CHECKS
# ============================================
if [ $FAILED -eq 0 ]; then
    echo ""
    echo "✅ All services are HEALTHY!"
    echo ""
    echo "🔗 Service Dependencies:"
    echo "   ✓ Eureka Server is up (service registry)"
    echo "   ✓ Config Server is up (configuration management)"
    echo "   ✓ API Gateway is up (request routing)"
    echo "   ✓ User Service is up (authentication)"
    echo "   ✓ Activity Service is up (fitness tracking)"
    echo "   ✓ AI Service is up (recommendations)"
    echo "   ✓ Frontend is up (user interface)"
    echo ""
    echo "🗄️  External Dependencies:"
    echo "   Note: MongoDB and RabbitMQ health is implicit"
    echo "   If services are healthy, database connections are working"
    echo ""
    
    # Exit with success code
    exit 0
else
    echo ""
    echo "❌ Some services are UNHEALTHY!"
    echo ""
    echo "🔍 Troubleshooting steps:"
    echo ""
    echo "1. Check container status:"
    echo "   docker-compose -p $PROJECT_NAME ps"
    echo ""
    echo "2. View logs for failed services:"
    echo "   docker-compose -p $PROJECT_NAME logs [service-name]"
    echo ""
    echo "3. Common issues:"
    echo "   - MongoDB not ready: Check mongo container"
    echo "   - RabbitMQ not ready: Check rabbitmq container"
    echo "   - Port conflicts: Check if ports are in use"
    echo "   - Memory issues: Check available system memory"
    echo "   - Startup time: Services may need more time"
    echo ""
    echo "4. Check all containers:"
    echo "   docker ps -a | grep $PROJECT_NAME"
    echo ""
    
    # Exit with failure code
    exit 1
fi
