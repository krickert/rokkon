# Test Module Deployment and Registration Guide

This guide provides step-by-step instructions for deploying the test-module and registering it with the Rokkon Engine using the CLI.

## Prerequisites

- Docker and Docker Compose installed
- Rokkon Engine running and healthy (as verified in previous steps)
- Consul service running (as part of the engine deployment)

## Step 1: Build the Test Module

First, we need to build the test-module and its associated CLI tool:

```bash
# Build the test-module and CLI tool
./gradlew :modules:test-module:build :cli:register-module:quarkusBuild -Dquarkus.package.jar.type=uber-jar
```

This command builds:
1. The test-module application (creating JARs in `modules/test-module/build/quarkus-app`)
2. The CLI registration tool as an uber-jar (in `cli/register-module/build/quarkus-app`)

## Step 2: Prepare the Docker Environment

Update the `docker-compose-test-module.yml` file to include the test-module service:

```yaml
# Add this service to the existing docker-compose-test-module.yml file
test-module:
  image: rokkon/test-module:latest
  container_name: rokkon-test-module
  build:
    context: ./modules/test-module
    dockerfile: src/main/docker/Dockerfile.jvm
  ports:
    - "39095:39095"   # HTTP port
    - "49095:49095"   # gRPC port
  environment:
    - MODULE_HOST=test-module
    - MODULE_PORT=49095
    - ENGINE_HOST=rokkon-engine
    - ENGINE_PORT=49000
    - EXTERNAL_MODULE_HOST=test-module
    - CONSUL_HOST=consul
  depends_on:
    rokkon-engine:
      condition: service_healthy
  networks:
    - rokkon-network
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:39095/q/health"]
    interval: 10s
    timeout: 5s
    retries: 5
```

## Step 3: Build and Deploy the Test Module

Build the Docker image and deploy the test-module:

```bash
# Build the test-module Docker image
docker build -f modules/test-module/src/main/docker/Dockerfile.jvm -t rokkon/test-module:latest ./modules/test-module

# Copy the CLI jar to the expected location for the Docker build
mkdir -p modules/test-module/build/docker
cp cli/register-module/build/quarkus-app/quarkus-run.jar modules/test-module/build/docker/rokkon-cli.jar

# Start the test-module service
docker compose -f docker-compose-test-module.yml up --build -d test-module
```

## Step 4: Verify Test Module Deployment

Check that the test-module container is running:

```bash
docker ps --filter "name=rokkon-test-module"
```

View the logs to ensure the module started correctly:

```bash
docker logs rokkon-test-module
```

Look for messages indicating successful startup and registration with the engine.

## Step 5: Manual Registration (if needed)

If the automatic registration in the entrypoint script fails, you can manually register the module using the CLI:

```bash
# Execute the register command inside the container
docker exec rokkon-test-module rokkon register \
  --module-host=localhost \
  --module-port=49095 \
  --engine-host=rokkon-engine \
  --engine-port=49000 \
  --registration-host=test-module \
  --registration-port=49095
```

## Step 6: Verify Registration

Verify that the test-module is registered with the engine and Consul:

1. Check the Consul UI at http://localhost:8500/ui/dc1/services
   - Look for the test-module service in the list
   - Verify it shows as healthy

2. Check the engine logs for registration confirmation:
   ```bash
   docker logs rokkon-engine | grep "test-module"
   ```

3. Check the test-module's health status:
   ```bash
   curl http://localhost:39095/q/health
   ```

## Troubleshooting

If you encounter issues:

1. **Module fails to start**: Check the logs for errors:
   ```bash
   docker logs rokkon-test-module
   ```

2. **Registration fails**: Ensure the engine is running and healthy:
   ```bash
   docker logs rokkon-engine
   ```

3. **Network issues**: Make sure all services are on the same Docker network:
   ```bash
   docker network inspect rokkon-network
   ```

4. **Consul connectivity**: Verify Consul is running and accessible:
   ```bash
   curl http://localhost:8500/v1/status/leader
   ```

## Cleanup

To stop and remove the services:

```bash
docker compose -f docker-compose-test-module.yml down
```

To remove the Docker images as well:

```bash
docker compose -f docker-compose-test-module.yml down --rmi all
```