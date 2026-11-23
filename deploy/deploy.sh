#!/bin/bash
# ============================================
# BLUE-GREEN DEPLOYMENT SCRIPT
# ============================================
# This script implements zero-downtime deployment
# by maintaining two environments (blue and green)
#
# How it works:
# 1. Current environment (blue) is running
# 2. Deploy new version to inactive environment (green)
# 3. Run health checks on green
# 4. If healthy, switch traffic to green
# 5. Stop old blue environment
# 6. If unhealthy, keep blue running and rollback

# Exit immediately if any command fails
set -e

echo "🚀 Starting Blue-Green Deployment..."

# ============================================
# COLORS FOR OUTPUT
# ============================================
GREEN='\033[0;32m'   # Success messages
BLUE='\033[0;34m'    # Info messages
RED='\033[0;31m'     # Error messages
YELLOW='\033[1;33m'  # Warning messages
NC='\033[0m'         # No Color (reset)

# ============================================
# CONFIGURATION
# ============================================
BLUE_ENV="blue"
GREEN_ENV="green"
CURRENT_ENV_FILE="/tmp/current_env"
DOCKER_COMPOSE_FILE="docker-compose.yml"
PROJECT_PREFIX="fitness"

# ============================================
# DETERMINE CURRENT AND NEW ENVIRONMENTS
# ============================================
# Check which environment is currently running
if [ -f "$CURRENT_ENV_FILE" ]; then
    # Read the current environment from file
    CURRENT_ENV=$(cat $CURRENT_ENV_FILE)
    
    # Switch to the other environment
    if [ "$CURRENT_ENV" == "$BLUE_ENV" ]; then
        NEW_ENV=$GREEN_ENV
        echo -e "${BLUE}📘 Current environment: BLUE${NC}"
        echo -e "${GREEN}📗 Deploying to: GREEN${NC}"
    else
        NEW_ENV=$BLUE_ENV
        echo -e "${GREEN}📗 Current environment: GREEN${NC}"
        echo -e "${BLUE}📘 Deploying to: BLUE${NC}"
    fi
else
    # First deployment - no current environment exists
    CURRENT_ENV="none"
    NEW_ENV=$BLUE_ENV
    echo -e "${YELLOW}⚠️  First deployment detected${NC}"
    echo -e "${BLUE}📘 Deploying to: BLUE${NC}"
fi

# ============================================
# PULL LATEST DOCKER IMAGES
# ============================================
echo ""
echo "📦 Pulling latest Docker images from GitHub Container Registry..."
echo "   This downloads pre-built images from CI/CD pipeline"

# Pull all service images
# These were built and pushed by GitHub Actions
docker-compose -f $DOCKER_COMPOSE_FILE pull

echo -e "${GREEN}✅ Images pulled successfully${NC}"

# ============================================
# START NEW ENVIRONMENT
# ============================================
echo ""
echo -e "${GREEN}🟢 Starting $NEW_ENV environment...${NC}"
echo "   Project name: $PROJECT_PREFIX-$NEW_ENV"

# Start all services in the new environment
# -p: Set project name (separates blue and green containers)
# -f: Specify docker-compose file
# up: Create and start containers
# -d: Detached mode (run in background)
docker-compose -p $PROJECT_PREFIX-$NEW_ENV -f $DOCKER_COMPOSE_FILE up -d

echo -e "${GREEN}✅ $NEW_ENV environment started${NC}"

# ============================================
# WAIT FOR SERVICES TO START
# ============================================
echo ""
echo "⏳ Waiting for services to initialize..."
echo "   Services need time to:"
echo "   - Start Spring Boot applications"
echo "   - Connect to MongoDB and RabbitMQ"
echo "   - Register with Eureka"
echo "   - Warm up JVM"

# Wait 60 seconds for services to fully start
# Adjust this based on your services' startup time
sleep 60

echo -e "${GREEN}✅ Initial wait complete${NC}"

# ============================================
# RUN HEALTH CHECKS
# ============================================
echo ""
echo "🏥 Running health checks on $NEW_ENV environment..."
echo "   This verifies all services are responding correctly"

# Run the health check script
# Pass the project name so it checks the right containers
if ./deploy/health-check.sh $PROJECT_PREFIX-$NEW_ENV; then
    # Health checks passed - deployment successful
    echo ""
    echo -e "${GREEN}✅ All health checks PASSED!${NC}"
    echo -e "${GREEN}✅ $NEW_ENV environment is healthy${NC}"
    
    # ============================================
    # SWITCH TRAFFIC TO NEW ENVIRONMENT
    # ============================================
    echo ""
    echo "🔄 Switching traffic to $NEW_ENV environment..."
    
    # Update the current environment marker
    # This tells future deployments which env is active
    echo $NEW_ENV > $CURRENT_ENV_FILE
    
    echo -e "${GREEN}✅ Traffic switched to $NEW_ENV${NC}"
    
    # ============================================
    # STOP OLD ENVIRONMENT
    # ============================================
    if [ "$CURRENT_ENV" != "none" ]; then
        echo ""
        echo -e "${BLUE}🔵 Stopping old $CURRENT_ENV environment...${NC}"
        echo "   Cleaning up old containers to free resources"
        
        # Stop and remove old containers
        # This frees up memory and ports
        docker-compose -p $PROJECT_PREFIX-$CURRENT_ENV -f $DOCKER_COMPOSE_FILE down
        
        echo -e "${GREEN}✅ Old $CURRENT_ENV environment stopped${NC}"
    fi
    
    # ============================================
    # DEPLOYMENT SUCCESS
    # ============================================
    echo ""
    echo "================================================"
    echo -e "${GREEN}✅ DEPLOYMENT SUCCESSFUL! 🎉${NC}"
    echo "================================================"
    echo "Active environment: $NEW_ENV"
    echo "Deployment time: $(date)"
    echo ""
    echo "🌐 Access your application:"
    echo "   Frontend:    http://localhost:5173"
    echo "   API Gateway: http://localhost:8080"
    echo "   Eureka:      http://localhost:8761"
    echo "================================================"
    
    exit 0
    
else
    # Health checks failed - rollback deployment
    echo ""
    echo -e "${RED}❌ Health checks FAILED!${NC}"
    echo -e "${RED}❌ $NEW_ENV environment is unhealthy${NC}"
    
    # ============================================
    # ROLLBACK DEPLOYMENT
    # ============================================
    echo ""
    echo "🔄 Rolling back deployment..."
    echo "   Stopping unhealthy $NEW_ENV environment"
    
    # Stop and remove the failed new environment
    docker-compose -p $PROJECT_PREFIX-$NEW_ENV -f $DOCKER_COMPOSE_FILE down
    
    echo -e "${YELLOW}⚠️  Unhealthy $NEW_ENV environment removed${NC}"
    
    if [ "$CURRENT_ENV" != "none" ]; then
        echo -e "${BLUE}📘 $CURRENT_ENV environment is still running${NC}"
        echo "   No downtime occurred - users unaffected"
    fi
    
    echo ""
    echo "================================================"
    echo -e "${RED}❌ DEPLOYMENT FAILED${NC}"
    echo "================================================"
    echo "Failed environment: $NEW_ENV"
    echo "Active environment: $CURRENT_ENV"
    echo ""
    echo "🔍 Troubleshooting:"
    echo "   1. Check container logs:"
    echo "      docker-compose -p $PROJECT_PREFIX-$NEW_ENV logs"
    echo "   2. Check service health:"
    echo "      ./deploy/health-check.sh $PROJECT_PREFIX-$NEW_ENV"
    echo "   3. Verify environment variables"
    echo "   4. Check MongoDB and RabbitMQ connections"
    echo "================================================"
    
    exit 1
fi
