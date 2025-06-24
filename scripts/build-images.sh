#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}🚀 Building Rokkon Docker Images${NC}"
echo -e "${GREEN}==================================${NC}"

# Build the CLI registration tool first
echo -e "\n${YELLOW}Building CLI registration tool...${NC}"
./gradlew :engine:cli-register:quarkusBuild -x test

# Build the engine (skip tests for speed)
echo -e "\n${YELLOW}Building Rokkon Engine...${NC}"
./gradlew :rokkon-engine:clean :rokkon-engine:build -x test -Dquarkus.container-image.build=false

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

echo -e "\n${GREEN}✅ All images built successfully!${NC}"

# List built images
echo -e "\n${YELLOW}Built images:${NC}"
docker images | grep -E "(rokkon/engine|rokkon/test-module)" | head -5