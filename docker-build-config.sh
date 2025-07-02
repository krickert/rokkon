#!/bin/bash

# Docker build configuration for Rokkon modules
# Source this file before running build-and-push-all-modules.sh

# Docker Registry Configuration
# Uncomment and modify the appropriate section for your registry

# For Docker Hub:
# export DOCKER_REGISTRY="docker.io"
# export DOCKER_NAMESPACE="your-dockerhub-username"

# For Google Container Registry:
# export DOCKER_REGISTRY="gcr.io"
# export DOCKER_NAMESPACE="your-project-id"

# For Amazon ECR:
# export DOCKER_REGISTRY="123456789012.dkr.ecr.us-east-1.amazonaws.com"
# export DOCKER_NAMESPACE="rokkon"

# For Azure Container Registry:
# export DOCKER_REGISTRY="yourregistry.azurecr.io"
# export DOCKER_NAMESPACE="rokkon"

# For self-hosted registry:
# export DOCKER_REGISTRY="registry.example.com:5000"
# export DOCKER_NAMESPACE="rokkon"

# Default values (using Docker Hub)
export DOCKER_REGISTRY="${DOCKER_REGISTRY:-docker.io}"
export DOCKER_NAMESPACE="${DOCKER_NAMESPACE:-rokkon}"
export DOCKER_TAG="${DOCKER_TAG:-latest}"

# Build options
export QUARKUS_PACKAGE_TYPE="${QUARKUS_PACKAGE_TYPE:-jar}"
export DOCKER_BUILDKIT=1  # Enable BuildKit for better performance

echo "Docker build configuration loaded:"
echo "  Registry: ${DOCKER_REGISTRY}"
echo "  Namespace: ${DOCKER_NAMESPACE}"
echo "  Tag: ${DOCKER_TAG}"
echo "  Package Type: ${QUARKUS_PACKAGE_TYPE}"