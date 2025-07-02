#!/bin/bash

# Build Pipeline Engine Docker image

set -e

echo "Building Pipeline Engine Docker image..."

# Build the engine with Quarkus
echo "Building Quarkus application..."
./gradlew :engine:pipestream:quarkusBuild

# Check if Dockerfile exists
if [ ! -f "engine/pipestream/src/main/docker/Dockerfile.jvm" ]; then
    echo "Error: Dockerfile.jvm not found for pipeline-engine"
    exit 1
fi

# Build Docker image
echo "Building Docker image..."
cd engine/pipestream
docker build -f src/main/docker/Dockerfile.jvm -t pipeline/engine:latest .
cd ../..

echo "✓ Pipeline Engine image built successfully"
echo ""
echo "Image created: pipeline/engine:latest"
docker images "pipeline/engine:latest" --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}"