#!/bin/bash

# Test script to build a single module's Docker image

set -e

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

MODULE="${1:-echo}"  # Default to echo module if not specified

echo -e "${YELLOW}Testing Docker build for module: ${MODULE}${NC}\n"

# Source configuration
if [ -f "docker-build-config.sh" ]; then
    source docker-build-config.sh
else
    echo -e "${YELLOW}Using default Docker configuration${NC}"
    export DOCKER_REGISTRY="docker.io"
    export DOCKER_NAMESPACE="rokkon"
    export DOCKER_TAG="test"
fi

# Build the Quarkus application
echo -e "${GREEN}Step 1: Building Quarkus application...${NC}"
./gradlew :modules:${MODULE}:clean :modules:${MODULE}:quarkusBuild -Dquarkus.package.type=jar

# Check if build was successful
if [ ! -d "modules/${MODULE}/build/quarkus-app" ]; then
    echo -e "${RED}Quarkus build failed - no quarkus-app directory found${NC}"
    exit 1
fi

# Build Docker image
echo -e "\n${GREEN}Step 2: Building Docker image...${NC}"
cd modules/${MODULE}

if [ -f "src/main/docker/Dockerfile.jvm" ]; then
    # Build with explicit tag
    IMAGE_NAME="${DOCKER_REGISTRY}/${DOCKER_NAMESPACE}/${MODULE}:${DOCKER_TAG}"
    echo -e "Building image: ${IMAGE_NAME}"
    
    docker build -f src/main/docker/Dockerfile.jvm \
        -t ${IMAGE_NAME} \
        --build-arg JAVA_PACKAGE=quarkus-app \
        .
    
    echo -e "\n${GREEN}✓ Successfully built Docker image: ${IMAGE_NAME}${NC}"
    
    # Show image info
    echo -e "\n${YELLOW}Image details:${NC}"
    docker images ${IMAGE_NAME}
    
    # Option to test run the container
    echo -e "\n${YELLOW}To test run the container:${NC}"
    echo -e "docker run --rm -p 8080:8080 -p 9000:9000 ${IMAGE_NAME}"
else
    echo -e "${RED}✗ Dockerfile.jvm not found for ${MODULE}${NC}"
    exit 1
fi

cd ../..