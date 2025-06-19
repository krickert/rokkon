# Test Module

## Overview

The Test Module is a reference implementation of a Rokkon Engine pipeline module. It demonstrates how to build a "dumb" gRPC service that can be registered and orchestrated by the engine. This module is used for integration testing and serves as a template for new module development.

## Architecture

This module exemplifies the key principles:
- **No Consul dependency** - Module doesn't know about service discovery
- **Pure gRPC service** - Implements `PipeStepProcessor` interface
- **Container-friendly** - Includes CLI tool for self-registration
- **Language-agnostic pattern** - Can be replicated in any language

## Core Implementation

### PipeStepProcessor Service

```java
@GrpcService
@Singleton
public class TestProcessorService implements PipeStepProcessor {
    
    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        // Simple echo behavior with optional delay
        return Uni.createFrom().item(() -> {
            // Process the document
            PipeDoc outputDoc = processDocument(request.getDocument());
            
            return ProcessResponse.newBuilder()
                .setSuccess(true)
                .setOutputDoc(outputDoc)
                .addProcessorLogs("Test module processed document")
                .build();
        });
    }
    
    @Override
    public Uni<ServiceRegistrationData> getServiceRegistration(Empty request) {
        return Uni.createFrom().item(
            ServiceRegistrationData.newBuilder()
                .setModuleName("test-module")
                .setJsonConfigSchema(getConfigSchema())
                .build()
        );
    }
    
    @Override
    public Uni<ProcessResponse> registrationCheck(ProcessRequest request) {
        // Lightweight test to verify module is working
        return Uni.createFrom().item(
            ProcessResponse.newBuilder()
                .setSuccess(true)
                .addProcessorLogs("Registration check passed")
                .build()
        );
    }
}
```

## Container Structure

### Dockerfile
```dockerfile
FROM openjdk:21-slim

# Copy module JAR
COPY build/quarkus-app /app

# Copy registration CLI
COPY rokkon-cli /usr/local/bin/

# Make CLI executable
RUN chmod +x /usr/local/bin/rokkon-cli

# Module runs on port 9090 (gRPC) and 8080 (HTTP/health)
EXPOSE 9090 8080

# Start script handles registration
COPY docker-entrypoint.sh /
ENTRYPOINT ["/docker-entrypoint.sh"]
```

### docker-entrypoint.sh
```bash
#!/bin/bash

# Start the module in background
java -jar /app/quarkus-run.jar &
MODULE_PID=$!

# Wait for module to be ready
echo "Waiting for module to start..."
sleep 10

# Register with engine if ENGINE_HOST is set
if [ ! -z "$ENGINE_HOST" ]; then
    echo "Registering with engine at $ENGINE_HOST:$ENGINE_PORT"
    rokkon-cli register \
        --module-port=9090 \
        --engine-host=$ENGINE_HOST \
        --engine-port=$ENGINE_PORT
    
    if [ $? -eq 0 ]; then
        echo "Registration successful"
    else
        echo "Registration failed, but module will continue running"
    fi
fi

# Keep module running
wait $MODULE_PID
```

## Configuration

### application.yml
```yaml
quarkus:
  application:
    name: test-module
  grpc:
    server:
      port: 9090
      host: 0.0.0.0
      enable-reflection-service: true
      max-inbound-message-size: 1073741824  # 1GB
    clients:
      testService:
        max-inbound-message-size: 1073741824  # 1GB
        
# Module-specific configuration
test:
  processor:
    # Configurable processing delay for testing
    delay-ms: ${TEST_DELAY_MS:0}
    # Behavior mode: echo, transform, error
    mode: ${TEST_MODE:echo}
    
# Health check endpoint
quarkus:
  health:
    enabled: true
    path: /q/health
```

## Module Behavior Modes

### Echo Mode (Default)
- Returns the input document unchanged
- Adds a processing timestamp
- Useful for testing pipeline flow

### Transform Mode
- Modifies document content
- Adds test metadata
- Simulates real processing

### Error Mode
- Returns configurable errors
- Tests error handling in pipeline
- Validates DLQ behavior

## Testing the Module

### Unit Tests
```java
@QuarkusTest
class TestProcessorServiceTest {
    @GrpcClient
    PipeStepProcessor processor;
    
    @Test
    void testProcessData() {
        ProcessRequest request = createTestRequest();
        ProcessResponse response = processor.processData(request)
            .await().indefinitely();
            
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
    }
    
    @Test
    void testRegistrationCheck() {
        ProcessRequest request = createMinimalRequest();
        ProcessResponse response = processor.registrationCheck(request)
            .await().indefinitely();
            
        assertThat(response.getSuccess()).isTrue();
    }
}
```

### Integration Tests
```java
@QuarkusIntegrationTest
class TestModuleIntegrationTest {
    @Test
    void testModuleRegistration() {
        // Test full registration flow with engine
        // Verify Consul registration
        // Test health checks
    }
}
```

## Running the Module

### Standalone Development
```bash
# Run with Quarkus dev mode
./gradlew quarkusDev

# Access at localhost:9090 (gRPC)
# Health check at localhost:8080/q/health
```

### Docker Compose
```yaml
version: '3.8'
services:
  test-module:
    image: rokkon/test-module:latest
    environment:
      ENGINE_HOST: rokkon-engine
      ENGINE_PORT: 9099
      TEST_MODE: echo
      TEST_DELAY_MS: 100
    networks:
      - rokkon-network
    depends_on:
      - rokkon-engine
      - consul
```

### Kubernetes
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: test-module
spec:
  replicas: 1
  selector:
    matchLabels:
      app: test-module
  template:
    metadata:
      labels:
        app: test-module
    spec:
      containers:
      - name: test-module
        image: rokkon/test-module:latest
        env:
        - name: ENGINE_HOST
          value: rokkon-engine-service
        - name: ENGINE_PORT
          value: "9099"
        ports:
        - containerPort: 9090
          name: grpc
        - containerPort: 8080
          name: http
```

## Monitoring

### Health Endpoints
- `/q/health` - Overall health
- `/q/health/live` - Liveness probe
- `/q/health/ready` - Readiness probe

### Metrics
- `grpc_server_requests_total` - Total gRPC requests
- `grpc_server_request_duration_seconds` - Request latency
- `test_module_documents_processed_total` - Documents processed

### Logging
```properties
# Detailed logging for debugging
quarkus.log.category."com.rokkon.testmodule".level=DEBUG
quarkus.log.category."io.grpc".level=INFO
```

## Development Guide

### Creating a New Module

1. **Copy this module** as a template
2. **Implement processing logic** in `processData()`
3. **Update module name** in `getServiceRegistration()`
4. **Configure ports** to avoid conflicts
5. **Add to whitelist** in engine-registration
6. **Build container** with CLI tool included
7. **Test registration** with integration tests

### Best Practices

1. **Keep it simple** - Modules should do one thing well
2. **Handle errors gracefully** - Return proper error responses
3. **Log appropriately** - Help with debugging
4. **Test thoroughly** - Unit and integration tests
5. **Document behavior** - Clear README and examples

## Troubleshooting

### Registration Failures
- Check ENGINE_HOST and ENGINE_PORT are set
- Verify module is in engine whitelist
- Ensure gRPC port is accessible
- Check registrationCheck implementation

### Processing Errors
- Review processor logs in response
- Check document size limits (1GB max)
- Verify configuration is valid
- Monitor memory usage

### Health Check Issues
- Ensure HTTP port 8080 is exposed
- Check Quarkus health extensions are included
- Verify no startup errors in logs