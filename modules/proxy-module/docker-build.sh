#!/bin/bash

# Build the CLI project first
echo "Building cli-register project..."
cd ../../
./gradlew :engine:cli-register:quarkusBuild

# Build the proxy module application
echo "Building proxy module..."
cd modules/proxy-module
./gradlew clean build

# Copy the proxy entrypoint script to the build directory
echo "Copying proxy entrypoint script..."
cp proxy-entrypoint.sh build/

# Build Docker image
echo "Building Docker image..."
docker build -f src/main/docker/Dockerfile.jvm -t rokkon/proxy-module:latest .

echo "Docker image built: rokkon/proxy-module:latest"
echo "Run with: docker run -i --rm -p 9090:9090 -e MODULE_HOST=host.docker.internal rokkon/proxy-module:latest"