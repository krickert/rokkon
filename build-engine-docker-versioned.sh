#!/bin/bash

# Build Rokkon Engine Docker image with version

set -e

# Get the project version
VERSION=$(./gradlew -q :rokkon-engine:properties | grep "^version:" | awk '{print $2}')
if [ -z "$VERSION" ]; then
    VERSION="1.0.0-SNAPSHOT"
fi

echo "Building Rokkon Engine Docker image version $VERSION..."

# Build the engine with Quarkus
echo "Building Quarkus application..."
./gradlew :rokkon-engine:quarkusBuild -x test

# Check if Dockerfile exists
if [ ! -f "rokkon-engine/src/main/docker/Dockerfile.jvm" ]; then
    echo "Error: Dockerfile.jvm not found for rokkon-engine"
    exit 1
fi

# Build Docker image
echo "Building Docker image..."
cd rokkon-engine
docker build -f src/main/docker/Dockerfile.jvm -t rokkon/rokkon-engine:$VERSION -t rokkon/rokkon-engine:latest .
cd ..

echo "✓ Rokkon Engine image built successfully"
echo ""
echo "Images created:"
echo "  - rokkon/rokkon-engine:$VERSION"
echo "  - rokkon/rokkon-engine:latest"
docker images "rokkon/rokkon-engine" --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}"