#!/bin/bash

# Push Docker images to registry

set -e

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration - Update these for your registry
REGISTRY="${DOCKER_REGISTRY:-docker.io}"
NAMESPACE="${DOCKER_NAMESPACE:-your-username}"  # Change this!
TAG="${DOCKER_TAG:-latest}"

# List of modules
MODULES=(
    "test-module"
    "echo"
    "chunker"
    "parser"
    "embedder"
)

echo -e "${BLUE}=== Rokkon Docker Push ===${NC}"
echo -e "Registry: ${YELLOW}${REGISTRY}${NC}"
echo -e "Namespace: ${YELLOW}${NAMESPACE}${NC}"
echo -e "Tag: ${YELLOW}${TAG}${NC}\n"

# Check if namespace is set
if [ "${NAMESPACE}" == "your-username" ]; then
    echo -e "${RED}Error: Please set your Docker namespace!${NC}"
    echo -e "Export DOCKER_NAMESPACE environment variable or edit this script."
    echo -e "Example: export DOCKER_NAMESPACE=mycompany"
    exit 1
fi

# Check Docker login
echo -e "${YELLOW}Checking Docker login...${NC}"
if ! docker info >/dev/null 2>&1; then
    echo -e "${RED}Docker is not running${NC}"
    exit 1
fi

# Function to tag and push image
push_image() {
    local module=$1
    local source_image="rokkon/${module}:latest"
    local target_image="${REGISTRY}/${NAMESPACE}/${module}:${TAG}"
    
    echo -e "\n${YELLOW}Processing ${module}...${NC}"
    
    # Check if source image exists
    if ! docker image inspect "${source_image}" >/dev/null 2>&1; then
        echo -e "${RED}✗ Source image not found: ${source_image}${NC}"
        return 1
    fi
    
    # Tag the image
    echo -e "${BLUE}Tagging ${source_image} -> ${target_image}${NC}"
    docker tag "${source_image}" "${target_image}"
    
    # Also tag as latest if not already
    if [ "${TAG}" != "latest" ]; then
        docker tag "${source_image}" "${REGISTRY}/${NAMESPACE}/${module}:latest"
    fi
    
    # Push the image
    echo -e "${BLUE}Pushing ${target_image}...${NC}"
    docker push "${target_image}"
    
    if [ "${TAG}" != "latest" ]; then
        docker push "${REGISTRY}/${NAMESPACE}/${module}:latest"
    fi
    
    echo -e "${GREEN}✓ ${module} pushed successfully${NC}"
}

# Main execution
echo -e "\n${YELLOW}Starting push process...${NC}"

# Push all images
for module in "${MODULES[@]}"; do
    push_image "$module"
done

echo -e "\n${GREEN}=== Push Summary ===${NC}"
echo -e "\n${YELLOW}Images pushed to registry:${NC}"
for module in "${MODULES[@]}"; do
    echo -e "  ${GREEN}✓${NC} ${REGISTRY}/${NAMESPACE}/${module}:${TAG}"
    if [ "${TAG}" != "latest" ]; then
        echo -e "  ${GREEN}✓${NC} ${REGISTRY}/${NAMESPACE}/${module}:latest"
    fi
done

echo -e "\n${GREEN}All images pushed successfully!${NC}"

# Show pull commands
echo -e "\n${YELLOW}To pull these images:${NC}"
for module in "${MODULES[@]}"; do
    echo -e "  docker pull ${REGISTRY}/${NAMESPACE}/${module}:${TAG}"
done