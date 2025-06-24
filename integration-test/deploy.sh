#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Registry configuration
REGISTRY_URL="nas.rokkon.com:5000"
PUSH_TO_REGISTRY=${1:-false}

echo -e "${GREEN}🚀 Rokkon Platform Deployment Script${NC}"
echo -e "${GREEN}=====================================>${NC}"

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check prerequisites
echo -e "\n${YELLOW}Checking prerequisites...${NC}"
if ! command_exists docker; then
    echo -e "${RED}❌ Docker is not installed${NC}"
    exit 1
fi

# Check for docker compose (plugin or standalone)
if docker compose version >/dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
elif command_exists docker-compose; then
    DOCKER_COMPOSE="docker-compose"
else
    echo -e "${RED}❌ Docker Compose is not installed${NC}"
    exit 1
fi

if ! command_exists ./gradlew; then
    echo -e "${RED}❌ Gradle wrapper not found${NC}"
    exit 1
fi

echo -e "${GREEN}✅ All prerequisites met${NC}"

# Build the CLI registration tool first
echo -e "\n${YELLOW}Building CLI registration tool...${NC}"
./gradlew :engine:cli-register:quarkusBuild

# Build the engine
echo -e "\n${YELLOW}Building Rokkon Engine...${NC}"
./gradlew :rokkon-engine:clean :rokkon-engine:build -Dquarkus.package.jar.type=uber-jar

# Build engine Docker image
echo -e "\n${YELLOW}Building Engine Docker image...${NC}"
cd rokkon-engine
docker build -f src/main/docker/Dockerfile.jvm -t rokkon/engine:latest .
cd ..

# Build the test-module
echo -e "\n${YELLOW}Building Test Module...${NC}"
cd modules/test-module
./docker-build.sh
cd ../..

# Tag images for registry if requested
if [ "$PUSH_TO_REGISTRY" = "push" ]; then
    echo -e "\n${YELLOW}Tagging images for registry...${NC}"
    docker tag rokkon/engine:latest ${REGISTRY_URL}/rokkon/engine:latest
    docker tag rokkon/test-module:latest ${REGISTRY_URL}/rokkon/test-module:latest
    
    echo -e "\n${YELLOW}Pushing images to registry ${REGISTRY_URL}...${NC}"
    docker push ${REGISTRY_URL}/rokkon/engine:latest
    docker push ${REGISTRY_URL}/rokkon/test-module:latest
    
    # Update docker-compose to use registry images
    sed -i.bak "s|image: rokkon/|image: ${REGISTRY_URL}/rokkon/|g" docker-compose.yml
    echo -e "${GREEN}✅ Images pushed to registry${NC}"
fi

# Stop any existing containers
echo -e "\n${YELLOW}Stopping existing containers...${NC}"
$DOCKER_COMPOSE down || true

# Start the platform
echo -e "\n${YELLOW}Starting Rokkon Platform...${NC}"
$DOCKER_COMPOSE up -d

# Wait for services to be healthy
echo -e "\n${YELLOW}Waiting for services to be healthy...${NC}"
for i in {1..30}; do
    if $DOCKER_COMPOSE ps | grep -q "healthy"; then
        echo -e "${GREEN}✅ Services are healthy${NC}"
        break
    fi
    echo -n "."
    sleep 2
done

# Show status
echo -e "\n${YELLOW}Platform Status:${NC}"
$DOCKER_COMPOSE ps

echo -e "\n${GREEN}🎉 Deployment complete!${NC}"
echo -e "\n${YELLOW}Access points:${NC}"
echo -e "  • Rokkon Dashboard: http://localhost:8080"
echo -e "  • Consul UI: http://localhost:8500"
echo -e "  • Engine gRPC: localhost:8081"
echo -e "  • Test Module gRPC: localhost:9090"

echo -e "\n${YELLOW}Useful commands:${NC}"
echo -e "  • View logs: $DOCKER_COMPOSE logs -f"
echo -e "  • Stop platform: $DOCKER_COMPOSE down"
echo -e "  • View test-module registration: docker logs rokkon-test-module"

if [ "$PUSH_TO_REGISTRY" = "push" ]; then
    echo -e "\n${YELLOW}Registry deployment:${NC}"
    echo -e "  • Images have been pushed to: ${REGISTRY_URL}"
    echo -e "  • To deploy on another machine, copy docker-compose.yml and run:"
    echo -e "    $DOCKER_COMPOSE up -d"
fi