#!/bin/bash

# Build and push Docker images for all Rokkon modules
# This script can build images locally or push to a registry

set -e

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Default configuration
REGISTRY="${DOCKER_REGISTRY:-local}"
NAMESPACE="${DOCKER_NAMESPACE:-rokkon}"
TAG="${DOCKER_TAG:-latest}"
PUSH="${DOCKER_PUSH:-false}"
PARALLEL="${DOCKER_BUILD_PARALLEL:-true}"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --push)
            PUSH="true"
            shift
            ;;
        --registry)
            REGISTRY="$2"
            shift 2
            ;;
        --namespace)
            NAMESPACE="$2"
            shift 2
            ;;
        --tag)
            TAG="$2"
            shift 2
            ;;
        --no-parallel)
            PARALLEL="false"
            shift
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo "Options:"
            echo "  --push              Push images to registry after building"
            echo "  --registry REGISTRY Docker registry (default: local)"
            echo "  --namespace NS      Docker namespace (default: rokkon)"
            echo "  --tag TAG          Docker tag (default: latest)"
            echo "  --no-parallel      Build sequentially instead of in parallel"
            echo "  --help             Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# List of modules to build
MODULES=(
    "test-module"
    "echo"
    "chunker"
    "parser"
    "embedder"
)

echo -e "${BLUE}=== Rokkon Docker Build ===${NC}"
echo -e "Registry: ${YELLOW}${REGISTRY}${NC}"
echo -e "Namespace: ${YELLOW}${NAMESPACE}${NC}"
echo -e "Tag: ${YELLOW}${TAG}${NC}"
echo -e "Push: ${YELLOW}${PUSH}${NC}"
echo -e "Parallel: ${YELLOW}${PARALLEL}${NC}\n"

# First, build the CLI register (needed by all modules)
echo -e "${YELLOW}Building CLI register...${NC}"
./gradlew :engine:cli-register:quarkusBuild -Dquarkus.package.jar.type=fast-jar

# Function to build a single module
build_module() {
    local module=$1
    local module_name=$(basename $module)
    local log_file="build-${module_name}.log"
    
    {
        echo -e "${YELLOW}[${module_name}] Starting build...${NC}"
        
        # Build Quarkus application
        echo -e "${BLUE}[${module_name}] Building Quarkus application...${NC}"
        ./gradlew :modules:${module}:clean :modules:${module}:quarkusBuild -Dquarkus.package.jar.type=fast-jar
        
        # Build Docker image
        echo -e "${BLUE}[${module_name}] Building Docker image...${NC}"
        cd modules/${module}
        
        if [ "${REGISTRY}" == "local" ]; then
            IMAGE_NAME="${NAMESPACE}/${module_name}:${TAG}"
        else
            IMAGE_NAME="${REGISTRY}/${NAMESPACE}/${module_name}:${TAG}"
        fi
        
        docker build -f src/main/docker/Dockerfile.jvm \
            -t ${IMAGE_NAME} \
            --build-arg JAVA_PACKAGE=quarkus-app \
            .
        
        cd ../..
        
        # Push if requested
        if [ "${PUSH}" == "true" ] && [ "${REGISTRY}" != "local" ]; then
            echo -e "${BLUE}[${module_name}] Pushing image...${NC}"
            docker push ${IMAGE_NAME}
        fi
        
        echo -e "${GREEN}[${module_name}] ✓ Complete${NC}"
        return 0
    } 2>&1 | tee ${log_file}
}

# Build all modules
if [ "${PARALLEL}" == "true" ]; then
    echo -e "${YELLOW}Building modules in parallel...${NC}\n"
    
    # Start all builds in background
    for module in "${MODULES[@]}"; do
        build_module "$module" &
    done
    
    # Wait for all builds to complete
    wait
else
    echo -e "${YELLOW}Building modules sequentially...${NC}\n"
    
    for module in "${MODULES[@]}"; do
        build_module "$module"
    done
fi

echo -e "\n${GREEN}=== Build Summary ===${NC}"

# Show all built images
echo -e "\n${YELLOW}Docker images built:${NC}"
if [ "${REGISTRY}" == "local" ]; then
    docker images "${NAMESPACE}/*:${TAG}" --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}\t{{.CreatedSince}}"
else
    for module in "${MODULES[@]}"; do
        module_name=$(basename $module)
        echo -e "  ${REGISTRY}/${NAMESPACE}/${module_name}:${TAG}"
    done
fi

# Check for build failures
FAILED=false
for module in "${MODULES[@]}"; do
    module_name=$(basename $module)
    log_file="build-${module_name}.log"
    if grep -q "error\|Error\|ERROR\|failed\|Failed\|FAILED" ${log_file} 2>/dev/null; then
        echo -e "${RED}✗ ${module_name} build may have errors (check ${log_file})${NC}"
        FAILED=true
    fi
done

if [ "${FAILED}" == "false" ]; then
    echo -e "\n${GREEN}✓ All modules built successfully!${NC}"
    
    # Clean up log files
    rm -f build-*.log
    
    # Show next steps
    echo -e "\n${YELLOW}Next steps:${NC}"
    if [ "${PUSH}" != "true" ] && [ "${REGISTRY}" != "local" ]; then
        echo -e "  - To push images: ${BLUE}$0 --push --registry ${REGISTRY}${NC}"
    fi
    echo -e "  - To run a module: ${BLUE}docker run --rm -p 8080:8080 -p 9000:9000 ${NAMESPACE}/<module>:${TAG}${NC}"
    echo -e "  - To tag for another registry: ${BLUE}docker tag ${NAMESPACE}/<module>:${TAG} <new-registry>/<namespace>/<module>:${TAG}${NC}"
else
    echo -e "\n${RED}✗ Some builds may have failed. Check the log files.${NC}"
    exit 1
fi