#!/bin/bash
# ============================================
# ROLLBACK SCRIPT
# ============================================
# Rolls back to the previous environment if deployment fails
# or if issues are discovered after deployment
#
# This script:
# 1. Identifies the currently active environment
# 2. Stops the current (problematic) environment
# 3. Starts the previous (stable) environment
# 4. Updates the environment marker
#
# Usage:
#   ./rollback.sh

# Exit on any error
set -e

echo "🔄 Starting Rollback Procedure..."
echo ""

# ============================================
# COLORS FOR OUTPUT
# ============================================
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# ============================================
# CONFIGURATION
# ============================================
CURRENT_ENV_FILE="/tmp/current_env"
DOCKER_COMPOSE_FILE="docker-compose.yml"
PROJECT_PREFIX="fitness"

# ============================================
# CHECK IF ROLLBACK IS POSSIBLE
# ============================================
if [ ! -f "$CURRENT_ENV_FILE" ]; then
    echo -e "${RED}❌ Error: No deployment history found${NC}"
    echo ""
    echo "Cannot rollback:"
    echo "  - No current environment file exists"
    echo "  - This may be the first deployment"
    echo "  - Or the environment file was deleted"
    echo ""
    exit 1
fi

# ============================================
# IDENTIFY ENVIRONMENTS
# ============================================
# Read the currently active environment
CURRENT_ENV=$(cat $CURRENT_ENV_FILE)

# Determine which environment to roll back to
if [ "$CURRENT_ENV" == "blue" ]; then
    PREVIOUS_ENV="green"
    echo -e "${BLUE}📘 Current environment: BLUE${NC}"
    echo -e "${GREEN}📗 Rolling back to: GREEN${NC}"
elif [ "$CURRENT_ENV" == "green" ]; then
    PREVIOUS_ENV="blue"
    echo -e "${GREEN}📗 Current environment: GREEN${NC}"
    echo -e "${BLUE}📘 Rolling back to: BLUE${NC}"
else
    echo -e "${RED}❌ Error: Invalid environment: $CURRENT_ENV${NC}"
    exit 1
fi

# ============================================
# CONFIRMATION PROMPT
# ============================================
echo ""
echo -e "${YELLOW}⚠️  WARNING: You are about to rollback the deployment${NC}"
echo ""
echo "This will:"
echo "  1. Stop current $CURRENT_ENV environment"
echo "  2. Start previous $PREVIOUS_ENV environment"
echo "  3. Switch all traffic back to $PREVIOUS_ENV"
echo ""
read -p "Do you want to continue? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo ""
    echo "Rollback cancelled"
    exit 0
fi

# ============================================
# STOP CURRENT ENVIRONMENT
# ============================================
echo ""
echo -e "${RED}🛑 Stopping current $CURRENT_ENV environment...${NC}"
echo "   This will terminate all running containers"

# Stop and remove containers from current environment
docker-compose -p $PROJECT_PREFIX-$CURRENT_ENV -f $DOCKER_COMPOSE_FILE down

echo -e "${GREEN}✅ Current $CURRENT_ENV environment stopped${NC}"

# ============================================
# START PREVIOUS ENVIRONMENT
# ============================================
echo ""
echo -e "${GREEN}🟢 Starting previous $PREVIOUS_ENV environment...${NC}"
echo "   Bringing back the last stable deployment"

# Check if previous environment containers exist
# If they were stopped (not removed), we can start them quickly
# If they were removed, we need to recreate them
CONTAINER_COUNT=$(docker ps -a -f name=$PROJECT_PREFIX-$PREVIOUS_ENV -q | wc -l)

if [ "$CONTAINER_COUNT" -gt 0 ]; then
    echo "   Found existing $PREVIOUS_ENV containers, starting them..."
    docker-compose -p $PROJECT_PREFIX-$PREVIOUS_ENV -f $DOCKER_COMPOSE_FILE start
else
    echo "   No existing containers found, creating new ones..."
    docker-compose -p $PROJECT_PREFIX-$PREVIOUS_ENV -f $DOCKER_COMPOSE_FILE up -d
fi

echo -e "${GREEN}✅ Previous $PREVIOUS_ENV environment started${NC}"

# ============================================
# WAIT FOR SERVICES TO BE READY
# ============================================
echo ""
echo "⏳ Waiting for services to initialize..."
sleep 30

# ============================================
# VERIFY PREVIOUS ENVIRONMENT
# ============================================
echo ""
echo "🏥 Verifying $PREVIOUS_ENV environment health..."

if ./deploy/health-check.sh $PROJECT_PREFIX-$PREVIOUS_ENV; then
    # Previous environment is healthy
    echo ""
    echo -e "${GREEN}✅ Previous $PREVIOUS_ENV environment is healthy${NC}"
    
    # ============================================
    # UPDATE ENVIRONMENT MARKER
    # ============================================
    echo ""
    echo "🔄 Updating active environment marker..."
    
    # Update the current environment file
    echo $PREVIOUS_ENV > $CURRENT_ENV_FILE
    
    echo -e "${GREEN}✅ Environment marker updated${NC}"
    
    # ============================================
    # ROLLBACK SUCCESS
    # ============================================
    echo ""
    echo "================================================"
    echo -e "${GREEN}✅ ROLLBACK SUCCESSFUL! 🎉${NC}"
    echo "================================================"
    echo "Active environment: $PREVIOUS_ENV"
    echo "Stopped environment: $CURRENT_ENV"
    echo "Rollback time: $(date)"
    echo ""
    echo "🌐 Access your application:"
    echo "   Frontend:    http://localhost:5173"
    echo "   API Gateway: http://localhost:8080"
    echo "   Eureka:      http://localhost:8761"
    echo ""
    echo "📝 Next steps:"
    echo "   1. Investigate why $CURRENT_ENV failed"
    echo "   2. Check logs: docker-compose -p $PROJECT_PREFIX-$CURRENT_ENV logs"
    echo "   3. Fix issues and redeploy"
    echo "================================================"
    
    exit 0
else
    # Previous environment is also unhealthy - critical situation
    echo ""
    echo -e "${RED}❌ CRITICAL: Previous $PREVIOUS_ENV environment is UNHEALTHY!${NC}"
    echo ""
    echo "================================================"
    echo -e "${RED}⚠️  CRITICAL FAILURE${NC}"
    echo "================================================"
    echo "Both environments are unhealthy:"
    echo "  - Current: $CURRENT_ENV (stopped)"
    echo "  - Previous: $PREVIOUS_ENV (unhealthy)"
    echo ""
    echo "🚨 Immediate actions required:"
    echo "   1. Check if MongoDB is running:"
    echo "      docker ps | grep mongo"
    echo "   2. Check if RabbitMQ is running:"
    echo "      docker ps | grep rabbitmq"
    echo "   3. Check system resources:"
    echo "      free -h (memory)"
    echo "      df -h (disk space)"
    echo "   4. Review all logs:"
    echo "      docker-compose -p $PROJECT_PREFIX-$PREVIOUS_ENV logs"
    echo "   5. Consider manual recovery or infrastructure restart"
    echo "================================================"
    
    exit 1
fi
