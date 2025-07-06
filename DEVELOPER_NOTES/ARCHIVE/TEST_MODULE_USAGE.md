## Using the Test Module for Integration Testing

## Overview

This document explains how to use the existing test-module for integration testing of the Pipeline Engine. The test-module is a comprehensive testing harness that addresses all the points highlighted in the FOLLOW_UP_TASKS.md document.

## 1. Docker Setup for Integration Tests

The project already includes a complete Docker setup for integration testing in the `integration-test/docker-compose.yml` file. This setup includes:

- **Consul**: For service discovery and configuration
- **Rokkon Engine**: The main engine container
- **Test Module**: A configurable test module for pipeline steps

### Running the Docker Setup

```bash
# Navigate to the integration-test directory
cd integration-test

# Start the Docker environment
docker-compose up -d

# Check the status of the containers
docker-compose ps

# View logs from all containers
docker-compose logs -f

# View logs from a specific container
docker-compose logs -f engine
```

### Configuration

The Docker Compose file configures all containers to run in the same network with the correct hostnames and ports:

- **Consul**: Hostname `consul`, Port `8500`
- **Rokkon Engine**: Hostname `engine`, Ports `8080` (HTTP) and `48081` (gRPC)
- **Test Module**: Configured to connect to the engine and consul services

### Environment Variables

You can customize the Docker environment by setting environment variables:

```bash
# Set environment variables
export CONSUL_VERSION=1.21.1
export ENGINE_VERSION=latest
export TEST_MODULE_VERSION=latest

# Start with custom versions
docker-compose up -d
```

## 2. Test Module for Pipeline Testing

The test-module is a feature-rich implementation of the PipeStepProcessor interface designed for testing the Rokkon pipeline system. It provides:

### Key Features

- **Configurable Processing Behaviors**: Echo, Transform, Validate, and Simulate modes
- **Error Simulation**: Configurable error rates and types
- **Performance Testing**: Configurable delays and resource usage
- **Validation Framework**: JSON schema validation and custom rules
- **Metrics and Monitoring**: Processing time metrics and health checks

### Building the Test Module

```bash
# Build the module
./gradlew :modules:test-module:build

# Build the Docker image
cd modules/test-module
./docker-build.sh

# Build with specific mode (dev or prod)
./docker-build.sh dev
```

### Configuration Options

The test-module can be configured using environment variables:

| Variable | Description | Default | Options |
|----------|-------------|---------|---------|
| `PROCESSING_MODE` | Processing behavior mode | echo | echo, transform, validate, simulate |
| `ERROR_RATE` | Probability of error (0.0-1.0) | 0.0 | 0.0 to 1.0 |
| `ERROR_TYPE` | Type of errors to generate | random | random, validation, processing, timeout |
| `PROCESSING_DELAY_MS` | Artificial delay in milliseconds | 0 | 0-10000 |
| `VALIDATION_STRICT` | Enable strict validation | false | true/false |
| `METRICS_ENABLED` | Enable metrics collection | true | true/false |
| `TRANSFORM_TYPE` | Type of transformation | uppercase | uppercase, lowercase, reverse, base64 |
| `MEMORY_SIMULATION_MB` | Memory to allocate during processing | 0 | 0-1000 |
| `CPU_SIMULATION_ITERATIONS` | CPU-intensive iterations | 0 | 0-1000000 |

You can set these environment variables in the Docker Compose file:

```yaml
test-module:
  image: rokkon/test-module:latest
  environment:
    PROCESSING_MODE: transform
    ERROR_RATE: 0.1
    TRANSFORM_TYPE: uppercase
```

### Testing Scenarios

The test-module supports various testing scenarios:

#### 1. Basic Echo Testing

Tests basic connectivity and data flow without transformation:

```yaml
test-module:
  environment:
    PROCESSING_MODE: echo
```

#### 2. Transformation Testing

Tests data transformation capabilities:

```yaml
test-module:
  environment:
    PROCESSING_MODE: transform
    TRANSFORM_TYPE: uppercase
```

#### 3. Validation Testing

Tests data validation against schemas:

```yaml
test-module:
  environment:
    PROCESSING_MODE: validate
    VALIDATION_STRICT: true
```

#### 4. Error Simulation

Tests error handling and recovery:

```yaml
test-module:
  environment:
    ERROR_RATE: 0.5
    ERROR_TYPE: validation
```

#### 5. Performance Testing

Tests system performance under load:

```yaml
test-module:
  environment:
    PROCESSING_DELAY_MS: 100
    MEMORY_SIMULATION_MB: 50
    CPU_SIMULATION_ITERATIONS: 10000
```

## 3. Seed Configuration for Integration Tests

The project includes a seed-config tool in the `engine/seed-config` directory for seeding Consul with the required configuration for integration tests.

### Building the Seed Config Tool

```bash
# Build the seed-config tool
./gradlew :engine:seed-config:build
```

### Using the Seed Config Tool

```bash
# Seed Consul with default configuration
java -jar engine/seed-config/build/quarkus-app/quarkus-run.jar \
  --host consul \
  --port 8500 \
  --key config/application

# Seed Consul with custom configuration file
java -jar engine/seed-config/build/quarkus-app/quarkus-run.jar \
  --host consul \
  --port 8500 \
  --key config/application \
  --config path/to/custom-config.json

# Validate configuration without writing to Consul
java -jar engine/seed-config/build/quarkus-app/quarkus-run.jar \
  --config path/to/custom-config.json \
  --validate
```

### Configuration Files

The seed-config tool supports both JSON and properties file formats:

- **JSON**: `engine/seed-config/seed-data.json`
- **Properties**: `engine/seed-config/seed-data.properties`

These files contain configuration for the engine, Consul, modules, and default cluster.

### Creating Test-Specific Configuration

For integration tests, you may want to create a test-specific configuration file:

```json
{
  "rokkon": {
    "engine": {
      "grpc-port": 48081,
      "rest-port": 8080,
      "debug": true
    },
    "consul": {
      "cleanup": {
        "enabled": false
      }
    },
    "modules": {
      "auto-discover": true,
      "require-whitelist": false
    },
    "default-cluster": {
      "name": "test-cluster",
      "auto-create": true
    }
  }
}
```

Save this file as `integration-test/test-config.json` and use it with the seed-config tool.

## 4. Improving Test Stability and Performance

The test-module includes features for optimizing test execution and improving stability:

### Caching Docker Images

```bash
# Build and cache the Docker images
./modules/test-module/docker-build.sh
docker tag rokkon/test-module:latest rokkon/test-module:cache

# Use cached images in docker-compose.yml
# services:
#   test-module:
#     image: rokkon/test-module:cache
```

### Resource Management

The test-module allows fine-tuning of resource usage:

- Configure memory usage with `MEMORY_SIMULATION_MB`
- Configure CPU usage with `CPU_SIMULATION_ITERATIONS`
- Set processing delays with `PROCESSING_DELAY_MS`

You can also limit container resources in the Docker Compose file:

```yaml
test-module:
  image: rokkon/test-module:latest
  deploy:
    resources:
      limits:
        cpus: '0.5'
        memory: 512M
```

### Cleanup

```bash
# Stop and remove containers
docker-compose down

# Remove volumes
docker-compose down -v

# Remove images
docker rmi rokkon/test-module:latest rokkon/engine:latest
```

### Health Checks

The Docker Compose file includes health checks for all containers:

```yaml
consul:
  healthcheck:
    test: ["CMD", "consul", "members"]
    interval: 10s
    timeout: 5s
    retries: 5

engine:
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8080/q/health"]
    interval: 10s
    timeout: 5s
    retries: 5
```

## Integration with QuarkusTest

To use the Docker setup with QuarkusTest, create a custom test resource:

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
        
        // Seed configuration
        try {
            LOG.info("Seeding Consul configuration");
            seedProcess = new ProcessBuilder(
                    "java", "-jar", "../engine/seed-config/build/quarkus-app/quarkus-run.jar",
                    "--host", consulHost,
                    "--port", consulPort.toString(),
                    "--key", "config/application",
                    "--config", "../integration-test/test-config.json",
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
            "engine.host", environment.getServiceHost("engine", 8080),
            "engine.port", environment.getServicePort("engine", 8080).toString()
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

## Troubleshooting

### Common Issues

1. **Containers fail to start**
   - Check Docker logs: `docker-compose logs`
   - Ensure ports are not in use: `lsof -i :8500`
   - Check resource limits: `docker stats`

2. **Engine can't connect to Consul**
   - Verify Consul is running: `docker-compose ps consul`
   - Check network connectivity: `docker exec rokkon-engine ping consul`
   - Verify environment variables: `docker exec rokkon-engine env | grep CONSUL`

3. **Test module doesn't register with engine**
   - Check engine logs: `docker-compose logs engine`
   - Verify test module is running: `docker-compose ps test-module`
   - Check network connectivity: `docker exec rokkon-test-module ping engine`

4. **Tests fail with timeout**
   - Increase test timeout: `@Test(timeout = 60000)`
   - Check for resource constraints: `docker stats`
   - Verify health checks are passing: `docker inspect rokkon-engine | grep -A 10 Health`

## Conclusion

The test-module, combined with the Docker Compose setup and seed-config tool, provides a comprehensive solution for integration testing of the Rokkon Engine. It addresses all the points highlighted in the FOLLOW_UP_TASKS.md document:

1. **Comprehensive Docker Setup**: The `integration-test/docker-compose.yml` file provides a complete Docker environment.
2. **Test Module for Pipeline Testing**: The test-module is a feature-rich implementation for testing pipeline steps.
3. **Seed Configuration**: The seed-config tool can seed Consul with the required configuration.
4. **Test Stability and Performance**: The test-module includes features for optimizing test execution and improving stability.

By leveraging these existing tools, you can implement comprehensive integration tests for the Rokkon Engine without the need for additional development.