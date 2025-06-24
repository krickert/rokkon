#!/bin/bash
set -e

# Display message about disabled Docker image building
echo "ERROR: Docker image building is disabled for the engine/consul project"
echo "The application can still be built and tested, but Docker images cannot be created."

# Build the application only (no Docker image)
echo "Building engine application only..."
cd ../..
./gradlew :engine:consul:build -x test

echo "Build completed successfully. Docker image building is disabled."
