#!/bin/bash
set -e

echo "Deploying Test Module and registering with Rokkon Engine"
echo "========================================================"

# Step 1: Build the Test Module and CLI tool
echo "Step 1: Building the Test Module and CLI tool..."
./gradlew :modules:test-module:build :cli:register-module:quarkusBuild -Dquarkus.package.jar.type=uber-jar -x test

# Step 2: Prepare the Docker environment
echo "Step 2: Docker environment is prepared in docker-compose-test-module.yml"

# Step 3: Build and Deploy the Test Module
echo "Step 3: Building and deploying the Test Module..."

# Create directory for CLI jar
echo "Creating directory for CLI jar..."
mkdir -p modules/test-module/build/docker

# Copy the CLI jar to the expected location for the Docker build
echo "Copying CLI jar to the expected location..."
cp cli/register-module/build/quarkus-app/quarkus-run.jar modules/test-module/build/docker/rokkon-cli.jar

# Build the test-module Docker image
echo "Building the test-module Docker image..."
docker build -f modules/test-module/src/main/docker/Dockerfile.jvm -t rokkon/test-module:latest ./modules/test-module

# Start the test-module service
echo "Starting the test-module service..."
docker compose -f docker-compose-test-module.yml up --build -d test-module

# Step 4: Verify Test Module Deployment
echo "Step 4: Verifying Test Module deployment..."
echo "Checking that the test-module container is running..."
docker ps --filter "name=rokkon-test-module"

echo "Viewing logs to ensure the module started correctly..."
docker logs rokkon-test-module

# Step 5: Manual Registration (if needed)
echo "Step 5: If automatic registration failed, you can manually register the module using:"
echo "docker exec rokkon-test-module rokkon register \\"
echo "  --module-host=localhost \\"
echo "  --module-port=49095 \\"
echo "  --engine-host=rokkon-engine \\"
echo "  --engine-port=9081 \\"
echo "  --registration-host=test-module \\"
echo "  --registration-port=49095"

# Step 6: Verify Registration
echo "Step 6: Verifying registration..."
echo "1. Check the Consul UI at http://localhost:8500/ui/dc1/services"
echo "   - Look for the test-module service in the list"
echo "   - Verify it shows as healthy"

echo "2. Checking the engine logs for registration confirmation..."
docker logs rokkon-engine | grep "test-module"

echo "3. Checking the test-module's health status..."
curl http://localhost:39095/q/health

echo "Deployment complete! The test-module should now be registered with the Rokkon Engine."
echo "To stop and remove the services, run: docker compose -f docker-compose-test-module.yml down"
