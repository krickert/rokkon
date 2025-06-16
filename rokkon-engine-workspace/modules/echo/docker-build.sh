#!/bin/bash

# Docker build and push script for echo module
# Usage: ./docker-build.sh [build|push|build-push]

# Set default action
ACTION=${1:-build}

# Export environment variables for Quarkus container image
export QUARKUS_CONTAINER_IMAGE_USERNAME="${DOCKER_REGISTRY_USERNAME:-admin}"
export QUARKUS_CONTAINER_IMAGE_PASSWORD="${DOCKER_REGISTRY_PASSWORD:-admin}"

case $ACTION in
  build)
    echo "Building application and Docker image..."
    ./gradlew build -Dquarkus.container-image.build=true
    ;;
  push)
    echo "Pushing Docker image to registry..."
    ./gradlew build -Dquarkus.container-image.push=true -Dquarkus.container-image.build=false
    ;;
  build-push)
    echo "Building and pushing Docker image..."
    ./gradlew build -Dquarkus.container-image.build=true -Dquarkus.container-image.push=true
    ;;
  *)
    echo "Usage: $0 [build|push|build-push]"
    exit 1
    ;;
esac