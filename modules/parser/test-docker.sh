#!/bin/bash
set -e

# This script tests the parser module Docker container by running it with test parameters
# It assumes you've already built the Docker image with docker-build.sh

# Default to dev mode for testing
MODE=${1:-dev}
TAG=${2:-latest}

if [ "$MODE" = "dev" ]; then
    # Run in dev mode with host networking
    echo "Testing parser module in DEV mode..."
    docker run -i --rm --network=host \
        -e QUARKUS_LOG_LEVEL=INFO \
        -e QUARKUS_LOG_CATEGORY__COM_ROKKON__LEVEL=DEBUG \
        rokkon/parser-module:dev \
        --test-mode
else
    # Run in prod mode with port mapping
    echo "Testing parser module in PROD mode..."
    docker run -i --rm -p 49095:49095 \
        -e QUARKUS_LOG_LEVEL=INFO \
        -e QUARKUS_LOG_CATEGORY__COM_ROKKON__LEVEL=DEBUG \
        -e ENGINE_HOST=localhost \
        -e CONSUL_HOST=localhost \
        rokkon/parser-module:$TAG \
        --test-mode
fi

echo "Test complete. Check the logs above for any errors."
echo "If successful, you should see the module start up and report it's ready to process data."
echo "The --test-mode flag prevents it from trying to register with the engine."
