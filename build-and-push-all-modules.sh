#!/bin/bash

# Script to build and push Docker images for all Rokkon modules

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Docker registry (update this to your registry)
REGISTRY="${DOCKER_REGISTRY:-docker.io}"
NAMESPACE="${DOCKER_NAMESPACE:-rokkon}"
TAG="${DOCKER_TAG:-latest}"

echo -e "${YELLOW}Building and pushing Docker images for all modules...${NC}"
echo -e "Registry: ${REGISTRY}"
echo -e "Namespace: ${NAMESPACE}"
echo -e "Tag: ${TAG}\n"

# List of modules to build
MODULES=(
    "test-module"
    "echo"
    "chunker"
    "parser"
    "embedder"
    # "proxy-module" # Excluded from build
    # "connectors/filesystem-crawler" # Skipping for now
)

# Function to build and push a module
build_and_push_module() {
    local module=$1
    local module_name=$(basename $module)
    
    echo -e "\n${YELLOW}========================================${NC}"
    echo -e "${YELLOW}Building module: ${module_name}${NC}"
    echo -e "${YELLOW}========================================${NC}\n"
    
    # Build the module with Quarkus
    echo -e "${GREEN}Step 1: Building Quarkus application...${NC}"
    ./gradlew :modules:${module}:quarkusBuild -Dquarkus.package.type=jar
    
    # Build Docker image
    echo -e "\n${GREEN}Step 2: Building Docker image...${NC}"
    cd modules/${module}
    
    # Check if Dockerfile.jvm exists
    if [ -f "src/main/docker/Dockerfile.jvm" ]; then
        docker build -f src/main/docker/Dockerfile.jvm \
            -t ${REGISTRY}/${NAMESPACE}/${module_name}:${TAG} \
            -t ${REGISTRY}/${NAMESPACE}/${module_name}:latest \
            .
        
        echo -e "\n${GREEN}Step 3: Pushing Docker image...${NC}"
        docker push ${REGISTRY}/${NAMESPACE}/${module_name}:${TAG}
        docker push ${REGISTRY}/${NAMESPACE}/${module_name}:latest
        
        echo -e "${GREEN}✓ Successfully built and pushed ${module_name}${NC}"
    else
        echo -e "${RED}✗ Dockerfile.jvm not found for ${module_name}${NC}"
    fi
    
    cd ../..
}

# Function to check Docker login
check_docker_login() {
    echo -e "${YELLOW}Checking Docker login status...${NC}"
    if ! docker info >/dev/null 2>&1; then
        echo -e "${RED}Docker is not running or not accessible${NC}"
        exit 1
    fi
    
    # Try to pull a small image to test authentication
    if [ "${REGISTRY}" != "docker.io" ]; then
        echo -e "${YELLOW}Testing registry access...${NC}"
        if ! docker pull ${REGISTRY}/library/alpine:latest >/dev/null 2>&1; then
            echo -e "${RED}Not logged in to ${REGISTRY}. Please run: docker login ${REGISTRY}${NC}"
            exit 1
        fi
    fi
    
    echo -e "${GREEN}✓ Docker login verified${NC}\n"
}

# Main execution
main() {
    # Check prerequisites
    check_docker_login
    
    # Build and push each module
    for module in "${MODULES[@]}"; do
        build_and_push_module "$module"
    done
    
    echo -e "\n${GREEN}========================================${NC}"
    echo -e "${GREEN}All modules built and pushed successfully!${NC}"
    echo -e "${GREEN}========================================${NC}\n"
    
    # List all images
    echo -e "${YELLOW}Docker images created:${NC}"
    for module in "${MODULES[@]}"; do
        module_name=$(basename $module)
        echo -e "  ${REGISTRY}/${NAMESPACE}/${module_name}:${TAG}"
    done
}

# Run main function
main