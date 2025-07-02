#!/bin/bash
set -e

# Handle dev/prod mode
MODE=${1:-prod}

cd "$(dirname "$0")/../.."

# Ensure directory exists (workaround for Quarkus issue)
mkdir -p modules/test-module/build/classes/java/main

# Build everything in one command - CLI (as uber-jar) and test-module
./gradlew :engine:cli-register:quarkusBuild -Dquarkus.package.jar.type=uber-jar :modules:test-module:quarkusBuild

cd modules/test-module

if [ "$MODE" = "dev" ]; then
    echo "Building development image with tag: pipeline/test-module:dev"
    docker build -f src/main/docker/Dockerfile.dev -t pipeline/test-module:dev .
    echo "Run with: docker run -i --rm --network=host pipeline/test-module:dev"
    echo "Note: Dev image uses host networking - connects to localhost services"
else
    echo "Building production image with tag: pipeline/test-module:latest"
    docker build -f src/main/docker/Dockerfile.jvm -t pipeline/test-module:latest .
    echo "Run with: docker run -i --rm -p 49095:49095 -e ENGINE_HOST=engine -e CONSUL_HOST=consul pipeline/test-module:latest"
    echo "Note: Production image expects engine and consul services in the same network"
fi
