# Integration Test Setup for Rokkon Engine

## Overview

This document outlines the requirements and steps for setting up the Docker environment for integration tests of the Rokkon Engine. The integration tests require a complex setup with multiple Docker containers running in the same network, with specific hostnames and ports.

## Requirements

1. **Docker Containers**:
   - Consul: For service discovery and configuration
   - Rokkon Engine: The main engine container
   - Test Module: A test module container for pipeline steps

2. **Network Configuration**:
   - All containers must be in the same Docker network
   - Containers must be able to communicate with each other using specific hostnames

3. **Container Configuration**:
   - Consul:
     - Hostname: `consul`
     - Port: `8500`
   - Rokkon Engine:
     - Hostname: `engine`
     - HTTP Port: `8080`
     - gRPC Port: `48081`
     - Environment Variables:
       - `CONSUL_HOST=consul`
       - `CONSUL_PORT=8500`
   - Test Module:
     - Environment Variables:
       - `ENGINE_HOST=engine`
       - `ENGINE_PORT=48081`
       - `CONSUL_HOST=consul`
       - `CONSUL_PORT=8500`

## Existing Docker Compose Setup

The project already includes a Docker Compose setup in `integration-test/docker-compose.yml` that can be used as a reference for the integration test environment. This setup includes all the required containers and network configuration.

```yaml
# Excerpt from integration-test/docker-compose.yml
version: '3.8'

services:
  consul:
    image: hashicorp/consul:1.21.1
    container_name: rokkon-consul
    # ... configuration ...

  engine:
    image: rokkon/engine:latest
    container_name: rokkon-engine
    # ... configuration ...

  test-module:
    image: rokkon/test-module:latest
    container_name: rokkon-test-module
    # ... configuration ...

networks:
  rokkon-network:
    driver: bridge
    name: rokkon-network
```

## Current Integration Test Setup

The current integration test setup uses `ConsulTestResource` to start a Consul container using Testcontainers. However, this setup does not include the Rokkon Engine container or set up the required network configuration.

```java
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
@TestProfile(PipelineDefinitionResourceIT.RandomClusterProfile.class)
@TestInstance(Lifecycle.PER_CLASS)
public class PipelineDefinitionResourceIT extends PipelineDefinitionResourceTestBase {
    // ...
}
```

## Proposed Solution

To properly set up the Docker environment for integration tests, we need to:

1. Create a custom Testcontainers resource that:
   - Starts a Consul container
   - Builds and starts a Rokkon Engine container
   - Starts a Test Module container
   - Creates a Docker network for all containers
   - Configures the containers with the correct hostnames and ports

2. Update the integration test classes to use this custom resource

## Implementation Steps

1. **Create a Custom Testcontainers Resource**:
   - Extend `QuarkusTestResourceLifecycleManager`
   - Use the Docker Compose file as a reference for container configuration
   - Ensure proper startup order and health checks

```java
public class RokkonTestResource implements QuarkusTestResourceLifecycleManager {
    private DockerComposeContainer<?> environment;

    @Override
    public Map<String, String> start() {
        environment = new DockerComposeContainer<>(
                new File("../integration-test/docker-compose.yml"))
                .withExposedService("consul", 8500)
                .withExposedService("engine", 8080)
                .withExposedService("test-module", 9090);
        
        environment.start();
        
        // Seed configuration using the seed-config tool
        // ...
        
        return Map.of(
            "consul.host", environment.getServiceHost("consul", 8500),
            "consul.port", environment.getServicePort("consul", 8500).toString(),
            "engine.host", environment.getServiceHost("engine", 8080),
            "engine.port", environment.getServicePort("engine", 8080).toString()
        );
    }

    @Override
    public void stop() {
        if (environment != null) {
            environment.stop();
        }
    }
}
```

2. **Update Integration Test Classes**:
   - Update `PipelineDefinitionResourceIT` to use the new resource
   - Ensure tests wait for the environment to be fully initialized

```java
@QuarkusIntegrationTest
@QuarkusTestResource(RokkonTestResource.class)
@TestProfile(PipelineDefinitionResourceIT.RandomClusterProfile.class)
@TestInstance(Lifecycle.PER_CLASS)
public class PipelineDefinitionResourceIT extends PipelineDefinitionResourceTestBase {
    // ...
}
```

3. **Add Seed Configuration**:
   - Use the seed-config tool to seed Consul with the required configuration
   - Create a test-specific configuration file

## Challenges and Considerations

1. **Build Time**:
   - Building the Rokkon Engine container may take significant time
   - Consider pre-building the container before running tests
   - Use layer caching to speed up builds

2. **Resource Usage**:
   - The Docker setup requires significant resources
   - Ensure sufficient memory and CPU are available
   - Consider running tests in a dedicated CI/CD environment

3. **Cleanup**:
   - Ensure containers are properly stopped and removed after tests
   - Handle potential resource leaks
   - Use try-finally blocks to ensure cleanup even if tests fail

4. **CI/CD Integration**:
   - Ensure the setup works in CI/CD environments
   - Consider caching strategies for Docker images
   - Use environment variables to configure the test environment

## Example Test Resource Implementation

Here's a more complete example of a test resource implementation:

```java
public class RokkonTestResource implements QuarkusTestResourceLifecycleManager {
    private static final Logger LOG = Logger.getLogger(RokkonTestResource.class);
    private DockerComposeContainer<?> environment;
    private Process seedProcess;

    @Override
    public Map<String, String> start() {
        LOG.info("Starting Rokkon test environment");
        
        // Start Docker Compose environment
        environment = new DockerComposeContainer<>(
                new File("../integration-test/docker-compose.yml"))
                .withExposedService("consul", 8500)
                .withExposedService("engine", 8080)
                .withExposedService("test-module", 9090)
                .withLocalCompose(true)
                .withPull(true)
                .withTailChildContainers(true);
        
        environment.start();
        LOG.info("Docker Compose environment started");
        
        // Get service information
        String consulHost = environment.getServiceHost("consul", 8500);
        Integer consulPort = environment.getServicePort("consul", 8500);
        String engineHost = environment.getServiceHost("engine", 8080);
        Integer enginePort = environment.getServicePort("engine", 8080);
        
        LOG.info("Consul running at {}:{}", consulHost, consulPort);
        LOG.info("Engine running at {}:{}", engineHost, enginePort);
        
        // Seed configuration
        try {
            LOG.info("Seeding Consul configuration");
            seedProcess = new ProcessBuilder(
                    "java", "-jar", "../engine/seed-config/build/quarkus-app/quarkus-run.jar",
                    "--host", consulHost,
                    "--port", consulPort.toString(),
                    "--key", "config/application",
                    "--config", "../engine/seed-config/seed-data.json",
                    "--force"
            ).inheritIO().start();
            
            int exitCode = seedProcess.waitFor();
            if (exitCode != 0) {
                LOG.error("Seed process failed with exit code {}", exitCode);
            } else {
                LOG.info("Consul configuration seeded successfully");
            }
        } catch (Exception e) {
            LOG.error("Failed to seed Consul configuration", e);
        }
        
        // Return configuration for tests
        return Map.of(
            "consul.host", consulHost,
            "consul.port", consulPort.toString(),
            "engine.host", engineHost,
            "engine.port", enginePort.toString()
        );
    }

    @Override
    public void stop() {
        LOG.info("Stopping Rokkon test environment");
        
        if (seedProcess != null && seedProcess.isAlive()) {
            seedProcess.destroy();
        }
        
        if (environment != null) {
            environment.stop();
            LOG.info("Docker Compose environment stopped");
        }
    }
}
```

## Conclusion

Setting up the Docker environment for integration tests is a complex task that requires careful consideration of container configuration, networking, and resource management. By leveraging the existing Docker Compose setup and creating a custom Testcontainers resource, we can create a reliable and reproducible test environment for integration tests.