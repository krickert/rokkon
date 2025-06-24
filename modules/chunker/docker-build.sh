#!/bin/bash
set -e

# Handle dev/prod mode
MODE=${1:-prod}

cd "$(dirname "$0")/../.."

# Ensure directory exists (workaround for Quarkus issue)
mkdir -p modules/chunker/build/classes/java/main

# Build everything in one command - CLI and chunker-module
./gradlew :engine:cli-register:quarkusBuild :modules:chunker:quarkusBuild

cd modules/chunker

if [ "$MODE" = "dev" ]; then
    echo "Building development image with tag: rokkon/chunker-module:dev"
    docker build -f src/main/docker/Dockerfile.dev -t rokkon/chunker-module:dev .
    echo "Run with: docker run -i --rm --network=host rokkon/chunker-module:dev"
    echo "Note: Dev image uses host networking - connects to localhost services"
else
    echo "Building production image with tag: rokkon/chunker-module:latest"
    docker build -f src/main/docker/Dockerfile.jvm -t rokkon/chunker-module:latest .
    echo "Run with: docker run -i --rm -p 49095:49095 -e ENGINE_HOST=engine -e CONSUL_HOST=consul rokkon/chunker-module:latest"
    echo "Note: Production image expects engine and consul services in the same network"
fi