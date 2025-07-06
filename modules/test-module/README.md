# Test Module - Comprehensive Testing Harness for Rokkon Pipeline

## Overview

The Test Module is a feature-rich implementation of the PipeStepProcessor interface designed to serve as both a reference implementation and a comprehensive testing harness for the Rokkon pipeline system. It provides configurable behaviors, error simulation, performance testing capabilities, and extensive validation features.

## Key Features

### 1. **Configurable Processing Behaviors**
- **Echo Mode**: Returns input data unchanged for pass-through testing
- **Transform Mode**: Applies configurable transformations to data
- **Validate Mode**: Performs data validation without transformation
- **Simulate Mode**: Generates synthetic data for load testing

### 2. **Error Simulation**
- Configurable error rates for testing error handling
- Different error types: validation errors, processing errors, timeout errors
- Deterministic or random error generation
- Error injection at specific data patterns

### 3. **Performance Testing**
- Configurable processing delays to simulate real workloads
- Memory usage simulation
- CPU-intensive operation simulation
- Throughput limiting for rate testing

### 4. **Validation Framework**
- JSON schema validation
- Custom validation rules
- Field-level validation with detailed error reporting
- Support for complex nested data structures

### 5. **Metrics and Monitoring**
- Processing time metrics
- Success/failure counters
- Data volume tracking
- Custom business metrics
- Health check endpoint with detailed status

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Test Module                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────┐    ┌─────────────────┐               │
│  │  gRPC Service   │    │  Configuration  │               │
│  │  Implementation │◄───┤   Manager       │               │
│  └────────┬────────┘    └─────────────────┘               │
│           │                                                 │
│           ▼                                                 │
│  ┌─────────────────┐    ┌─────────────────┐               │
│  │   Processing    │    │   Validation    │               │
│  │     Engine      │◄───┤   Engine        │               │
│  └────────┬────────┘    └─────────────────┘               │
│           │                                                 │
│           ▼                                                 │
│  ┌─────────────────┐    ┌─────────────────┐               │
│  │    Metrics      │    │  Health Check   │               │
│  │   Collector     │    │    Service      │               │
│  └─────────────────┘    └─────────────────┘               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Building and Deployment

### Prerequisites
- Java 21 or higher
- Docker (for containerized deployment)
- Gradle 8.0+

### Building the Module

```bash
# Build the module
./gradlew :modules:test-module:build

# Build with native compilation
./gradlew :modules:test-module:build -Dquarkus.native.enabled=true
```

### Docker Build

```bash
# Build Docker image
./docker-build.sh

# Or manually
docker build -f src/main/docker/Dockerfile.jvm -t rokkon/test-module:latest .
```

### Running Locally

```bash
# Development mode with live reload
./gradlew :modules:test-module:quarkusDev

# Run the JAR directly
java -jar build/quarkus-app/quarkus-run.jar
```

### Docker Deployment

```bash
# Run with default configuration
docker run -p 48095:48095 rokkon/test-module:latest

# Run with custom configuration
docker run -p 48095:48095 \
  -e PROCESSING_MODE=transform \
  -e ERROR_RATE=0.1 \
  -e PROCESSING_DELAY_MS=100 \
  rokkon/test-module:latest
```

## Configuration

### Environment Variables

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

### Configuration File (application.properties)

```properties
# gRPC Configuration
quarkus.grpc.server.port=48095
quarkus.grpc.server.host=0.0.0.0
quarkus.grpc.server.max-inbound-message-size=4194304

# HTTP Configuration
quarkus.http.port=38095
quarkus.http.host=0.0.0.0

# Health Check Configuration
quarkus.health.extensions.enabled=true

# Metrics Configuration
quarkus.micrometer.export.prometheus.enabled=true
quarkus.micrometer.export.prometheus.path=/metrics
```

## Usage Examples

### Basic Echo Test

```bash
# Send a simple echo request
grpcurl -plaintext -d '{
  "data": {"message": "Hello, World!"},
  "metadata": {"source": "test"}
}' localhost:48095 com.rokkon.pipeline.grpc.PipeStepProcessor/processData
```

### Transform Test

```bash
# Configure for transformation
export PROCESSING_MODE=transform
export TRANSFORM_TYPE=uppercase

# Send data to transform
grpcurl -plaintext -d '{
  "data": {"text": "transform me"},
  "metadata": {"operation": "uppercase"}
}' localhost:48095 com.rokkon.pipeline.grpc.PipeStepProcessor/processData
```

### Error Simulation Test

```bash
# Configure 50% error rate
export ERROR_RATE=0.5
export ERROR_TYPE=validation

# Send multiple requests to see errors
for i in {1..10}; do
  grpcurl -plaintext -d '{
    "data": {"iteration": '$i'},
    "metadata": {"test": "error-simulation"}
  }' localhost:48095 com.rokkon.pipeline.grpc.PipeStepProcessor/processData
done
```

### Performance Test

```bash
# Configure performance simulation
export PROCESSING_DELAY_MS=500
export MEMORY_SIMULATION_MB=100
export CPU_SIMULATION_ITERATIONS=100000

# Run performance test
time grpcurl -plaintext -d '{
  "data": {"test": "performance"},
  "metadata": {"scenario": "load-test"}
}' localhost:48095 com.rokkon.pipeline.grpc.PipeStepProcessor/processData
```

## Testing Scenarios

### 1. **Integration Testing**
- Verify pipeline connectivity
- Test data flow through multiple modules
- Validate error propagation

### 2. **Load Testing**
- Configure delays and resource usage
- Generate high volumes of requests
- Monitor performance metrics

### 3. **Chaos Engineering**
- Random error injection
- Timeout simulation
- Resource exhaustion testing

### 4. **Validation Testing**
- Schema validation
- Business rule validation
- Data quality checks

## Monitoring and Observability

### Health Check Endpoint

```bash
# Check module health
curl http://localhost:38095/q/health

# Response example:
{
  "status": "UP",
  "checks": [{
    "name": "test-module-health",
    "status": "UP",
    "data": {
      "mode": "echo",
      "errorRate": 0.0,
      "requestsProcessed": 1234,
      "errorsGenerated": 0
    }
  }]
}
```

### Metrics Endpoint

```bash
# Get Prometheus metrics
curl http://localhost:38095/q/metrics

# Key metrics:
# - test_module_requests_total
# - test_module_errors_total
# - test_module_processing_duration_seconds
# - test_module_active_requests
```

## Module Registration

The test module supports automatic registration with the Rokkon engine using the included CLI tool:

```bash
# Manual registration
java -jar pipeline-cli.jar register 
  --host engine-host 
  --port 39001 
  --module-name test-module 
  --module-host localhost 
  --module-port 39100

# Automatic registration on startup (Docker)
# Set in docker-compose.yml or kubernetes deployment
environment:
  - AUTO_REGISTER=true
  - ENGINE_HOST=rokkon-engine
  - ENGINE_PORT=48081
```

## Development Guide

### Adding New Processing Modes

1. Create a new processor class implementing `DataProcessor`:
```java
@ApplicationScoped
public class MyCustomProcessor implements DataProcessor {
    @Override
    public ProcessResponse process(ProcessRequest request) {
        // Implementation
    }
}
```

2. Register in `ProcessingEngine`:
```java
@ApplicationScoped
public class ProcessingEngine {
    @Inject
    MyCustomProcessor myProcessor;
    
    public ProcessResponse process(ProcessRequest request, String mode) {
        return switch (mode) {
            case "my-custom" -> myProcessor.process(request);
            // ... other cases
        };
    }
}
```

### Adding Custom Metrics

```java
@Inject
MeterRegistry registry;

// Counter
Counter customCounter = Counter.builder("test_module_custom_total")
    .description("Custom metric description")
    .register(registry);

// Timer
Timer customTimer = Timer.builder("test_module_custom_duration")
    .description("Custom operation duration")
    .register(registry);
```

## Troubleshooting

### Common Issues

1. **Module fails to start**
   - Check port availability: `lsof -i :48095`
   - Verify Java version: `java -version` (requires 21+)
   - Check logs: `docker logs <container-id>`

2. **Registration fails**
   - Ensure engine is running and accessible
   - Verify network connectivity
   - Check firewall rules

3. **High memory usage**
   - Reduce `MEMORY_SIMULATION_MB`
   - Check for memory leaks in custom processors
   - Monitor with: `docker stats <container-id>`

## Contributing

When adding features to the test module:

1. Ensure backward compatibility
2. Add appropriate configuration options
3. Update this README with examples
4. Add unit tests for new functionality
5. Test with the full pipeline

## License

This module is part of the Rokkon Pipeline project and follows the same licensing terms.