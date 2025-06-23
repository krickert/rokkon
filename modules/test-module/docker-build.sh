#!/bin/bash

# Build the CLI project first
echo "Building cli-register project..."
cd ../../
./gradlew :engine:cli-register:quarkusBuild

# Build the test-module application
echo "Building test-module..."
cd modules/test-module
./gradlew clean build

# Build Docker image
echo "Building Docker image..."
docker build -f src/main/docker/Dockerfile.jvm -t rokkon/test-module:latest .

echo "Docker image built successfully!"
echo "Run with: docker run -i --rm -p 9090:9090 -p 8080:8080 -e ENGINE_HOST=host.docker.internal rokkon/test-module:latest"
