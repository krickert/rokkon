#!/bin/bash

# Build Rokkon Engine Docker image

set -e

echo "Building Rokkon Engine Docker image..."

# Build the engine with Quarkus
echo "Building Quarkus application..."
./gradlew :rokkon-engine:quarkusBuild

# Check if Dockerfile exists
if [ ! -f "rokkon-engine/src/main/docker/Dockerfile.jvm" ]; then
    echo "Error: Dockerfile.jvm not found for rokkon-engine"
    exit 1
fi

# Build Docker image
echo "Building Docker image..."
cd rokkon-engine
docker build -f src/main/docker/Dockerfile.jvm -t rokkon/engine:latest .
cd ..

echo "✓ Rokkon Engine image built successfully"
echo ""
echo "Image created: rokkon/engine:latest"
docker images "rokkon/engine:latest" --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}"