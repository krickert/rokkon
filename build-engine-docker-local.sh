#!/bin/bash

# Build Rokkon Engine Docker image for local use (without registry prefix)

set -e

echo "Building Rokkon Engine Docker image for local use..."

# Get the project version
VERSION=$(./gradlew -q :rokkon-engine:properties | grep "^version:" | awk '{print $2}')
if [ -z "$VERSION" ]; then
    VERSION="1.0.0-SNAPSHOT"
fi

echo "Building version: $VERSION"

# Build the application first
echo "Building application..."
./gradlew :rokkon-engine:build -x test

# Build the Docker image using Quarkus container-image extension without registry
echo "Building Docker image with Quarkus (local)..."
./gradlew :rokkon-engine:imageBuild \
    -Dquarkus.container-image.registry= \
    -Dquarkus.container-image.group=rokkon \
    -Dquarkus.container-image.name=rokkon-engine \
    -Dquarkus.container-image.tag=$VERSION \
    -Dquarkus.container-image.additional-tags=latest

echo ""
echo "✓ Rokkon Engine image built successfully"
echo ""
echo "Images created:"
echo "  - rokkon/rokkon-engine:$VERSION"
echo "  - rokkon/rokkon-engine:latest"
docker images "rokkon/rokkon-engine" --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}"