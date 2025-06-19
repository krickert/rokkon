#!/bin/bash

# Build the application
echo "Building test-module..."
./gradlew clean build

# Build Docker image
echo "Building Docker image..."
docker build -f src/main/docker/Dockerfile.jvm -t rokkon/test-module:latest .

echo "Docker image built successfully!"
echo "Run with: docker run -i --rm -p 49093:49093 rokkon/test-module:latest"