#!/bin/bash
set -e

# Handle dev/prod mode
MODE=${1:-prod}

cd "$(dirname "$0")/../.."

# Ensure directory exists (workaround for Quarkus issue)
mkdir -p modules/parser/build/classes/java/main

# Build everything in one command - CLI and parser-module
./gradlew :engine:cli-register:quarkusBuild :modules:parser:quarkusBuild

cd modules/parser

if [ "$MODE" = "dev" ]; then
    echo "Building development image with tag: rokkon/parser-module:dev"
    docker build -f src/main/docker/Dockerfile.dev -t rokkon/parser-module:dev .
    echo "Run with: docker run -i --rm --network=host rokkon/parser-module:dev"
    echo "Note: Dev image uses host networking - connects to localhost services"
else
    echo "Building production image with tag: rokkon/parser-module:latest"
    docker build -f src/main/docker/Dockerfile.jvm -t rokkon/parser-module:latest .
    echo "Run with: docker run -i --rm -p 49095:49095 -e ENGINE_HOST=engine -e CONSUL_HOST=consul rokkon/parser-module:latest"
    echo "Note: Production image expects engine and consul services in the same network"
fi