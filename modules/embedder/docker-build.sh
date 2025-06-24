#!/bin/bash
set -e

# Handle dev/prod mode
MODE=${1:-prod}

cd "$(dirname "$0")/../.."

# Ensure directory exists (workaround for Quarkus issue)
mkdir -p modules/embedder/build/classes/java/main

# Build everything in one command - CLI and embedder-module
./gradlew :engine:cli-register:quarkusBuild :modules:embedder:quarkusBuild

cd modules/embedder

if [ "$MODE" = "dev" ]; then
    echo "Building development image with tag: rokkon/embedder-module:dev"
    docker build -f src/main/docker/Dockerfile.dev -t rokkon/embedder-module:dev .
    echo "Run with: docker run -i --rm --network=host rokkon/embedder-module:dev"
    echo "Note: Dev image uses host networking - connects to localhost services"
else
    echo "Building production image with tag: rokkon/embedder-module:latest"
    docker build -f src/main/docker/Dockerfile.jvm -t rokkon/embedder-module:latest .
    echo "Run with: docker run -i --rm -p 49093:49093 -e ENGINE_HOST=engine -e CONSUL_HOST=consul rokkon/embedder-module:latest"
    echo "Note: Production image expects engine and consul services in the same network"
fi