#!/bin/bash
set -e

# Handle dev/prod mode
MODE=${1:-prod}

cd "$(dirname "$0")/../.."

# Ensure directory exists (workaround for Quarkus issue)
mkdir -p modules/echo/build/classes/java/main

# Build everything in one command - CLI and echo-module
./gradlew :engine:cli-register:quarkusBuild :modules:echo:quarkusBuild

cd modules/echo

if [ "$MODE" = "dev" ]; then
    echo "Building development image with tag: rokkon/echo-module:dev"
    docker build -f src/main/docker/Dockerfile.dev -t rokkon/echo-module:dev .
    echo "Run with: docker run -i --rm --network=host rokkon/echo-module:dev"
    echo "Note: Dev image uses host networking - connects to localhost services"
else
    echo "Building production image with tag: rokkon/echo-module:latest"
    docker build -f src/main/docker/Dockerfile.jvm -t rokkon/echo-module:latest .
    echo "Run with: docker run -i --rm -p 49095:49095 -e ENGINE_HOST=engine -e CONSUL_HOST=consul rokkon/echo-module:latest"
    echo "Note: Production image expects engine and consul services in the same network"
fi