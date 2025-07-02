#!/bin/bash

# Build Test Module Docker image for local use

set -e

echo "Building Test Module Docker image for local use..."

# Get the project version
VERSION=$(./gradlew -q :modules:test-module:properties | grep "^version:" | awk '{print $2}')
if [ -z "$VERSION" ]; then
    VERSION="1.0.0-SNAPSHOT"
fi

echo "Building version: $VERSION"

# Build the application first
echo "Building application..."
./gradlew :modules:test-module:build -x test

# Build the Docker image using Quarkus container-image extension without registry
echo "Building Docker image with Quarkus (local)..."
./gradlew :modules:test-module:imageBuild \
    -Dquarkus.container-image.registry= \
    -Dquarkus.container-image.group=rokkon \
    -Dquarkus.container-image.name=test-module \
    -Dquarkus.container-image.tag=$VERSION \
    -Dquarkus.container-image.additional-tags=latest

echo ""
echo "✓ Test Module image built successfully"
echo ""
echo "Images created:"
echo "  - rokkon/test-module:$VERSION"
echo "  - rokkon/test-module:latest"
docker images "rokkon/test-module" --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}"