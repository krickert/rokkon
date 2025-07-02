#!/bin/bash

# Simple Docker build script for Rokkon modules

set -e

echo "Building Rokkon module Docker images..."

# Build CLI register first
echo "Building CLI register..."
./gradlew :engine:cli-register:quarkusBuild

# List of modules
MODULES=(
    "test-module"
    "echo"
    "chunker"
    "parser"
    "embedder"
)

# Build each module
for module in "${MODULES[@]}"; do
    echo ""
    echo "========================================="
    echo "Building ${module}..."
    echo "========================================="
    
    # Build Quarkus app
    ./gradlew :modules:${module}:quarkusBuild
    
    # Build Docker image
    cd modules/${module}
    docker build -f src/main/docker/Dockerfile.jvm -t rokkon/${module}:latest .
    cd ../..
    
    echo "✓ ${module} built successfully"
done

echo ""
echo "All images built successfully!"
echo ""
echo "Images created:"
docker images "rokkon/*:latest" --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}"