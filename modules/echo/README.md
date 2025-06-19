# Echo Module

## Overview
The Echo module is a simple pass-through service for the Rokkon Engine pipeline. It's primarily used for testing, debugging, and as a reference implementation for new module developers. The module receives documents and returns them unchanged, logging the processing for visibility.

## Architecture

### Service Implementation
- **gRPC Service**: Implements `PipeStepProcessor` interface
- **Port**: 9090
- **Main Class**: `EchoServiceImpl`

### Core Functionality
1. **Pass-through Processing**
   - Receives documents and returns them unchanged
   - Logs all received documents for debugging
   - Validates pipeline connectivity

2. **Testing Support**
   - Verifies pipeline configuration
   - Tests module registration flow
   - Validates gRPC communication

3. **Reference Implementation**
   - Minimal module example
   - Shows proper service structure
   - Demonstrates registration pattern

## Container Deployment

### Docker Structure
```
echo-container/
├── echo-service (this module)
├── rokkon-cli (registration tool)
└── docker-entrypoint.sh
```

### Registration Flow
1. Container starts echo service on port 9090
2. CLI tool connects to localhost:9090
3. Calls `GetServiceRegistration()` to get module info
4. Registers with Rokkon Engine's ModuleRegistrationService
5. Engine validates via `RegistrationCheck()`
6. Engine registers service in Consul with gRPC health checks

### Example docker-entrypoint.sh
```bash
#!/bin/bash
# Start the echo service
java -jar /app/echo-service.jar &

# Wait for service to be ready
while ! grpc_health_probe -addr=localhost:9090 2>/dev/null; do
  echo "Waiting for echo service..."
  sleep 1
done

# Register with engine
rokkon-cli register \
  --module-port=9090 \
  --engine-host=${ENGINE_HOST} \
  --engine-port=${ENGINE_PORT}

# Keep container running
wait
```

## Configuration

### application.yml
```yaml
quarkus:
  application:
    name: echo
  grpc:
    server:
      port: 9090
      host: 0.0.0.0
      enable-reflection-service: true
  log:
    level: INFO
    category:
      "com.rokkon.echo":
        level: DEBUG
```

## Development

### Building
```bash
# Standard build
./gradlew build

# Build with tests
./gradlew build test

# Build Docker image
./gradlew build -Dquarkus.container-image.build=true
```

### Testing
```bash
# Run unit tests
./gradlew test

# Run integration tests
./gradlew quarkusIntTest

# Run in dev mode with live reload
./gradlew quarkusDev
```

### Local Development
```bash
# Start in dev mode
./gradlew quarkusDev

# Test with grpcurl
grpcurl -plaintext localhost:9090 list
grpcurl -plaintext localhost:9090 com.rokkon.PipeStepProcessor/GetServiceRegistration

# Send test document
grpcurl -plaintext -d '{"document": {"id": "test-123", "content": "dGVzdCBjb250ZW50"}}' \
  localhost:9090 com.rokkon.PipeStepProcessor/ProcessData
```

## Integration with Pipeline

### Input Format
Receives `ProcessRequest` with:
- Document data (any format)
- Pipeline context
- Processing metadata

### Output Format
Returns `ProcessResponse` with:
- Same document unchanged
- Success status
- Processing log entry

### Example Usage
```java
// Any document type
Document doc = Document.newBuilder()
    .setId("test-123")
    .setContent(ByteString.copyFromUtf8("test content"))
    .build();

ProcessRequest request = ProcessRequest.newBuilder()
    .setDocument(doc)
    .setPipelineId("test-pipeline")
    .build();

// Echo returns the same document
ProcessResponse response = echoService.processData(request).await();
assert response.getOutputDoc().equals(doc);
assert response.getSuccess();
```

## Use Cases

### 1. Pipeline Testing
- Verify end-to-end connectivity
- Test module registration
- Validate Consul integration

### 2. Debugging
- Log document flow through pipeline
- Inspect document structure
- Monitor processing timestamps

### 3. Development Template
- Copy as starting point for new modules
- Reference for proper structure
- Example of minimal implementation

## Health Checks

The module exposes standard gRPC health check endpoints:
- Overall service health: `/grpc.health.v1.Health/Check`
- Watch health changes: `/grpc.health.v1.Health/Watch`

Consul monitors these endpoints every 10 seconds to maintain service availability.

## Troubleshooting

### Common Issues

1. **Registration Failures**
   - Verify engine connectivity
   - Check environment variables
   - Ensure CLI tool is present

2. **Health Check Failures**
   - Verify gRPC server is running
   - Check port availability
   - Review service logs

3. **No Output**
   - Enable DEBUG logging
   - Check processor logs in response
   - Verify input document structure

### Debug Logging
```yaml
quarkus:
  log:
    category:
      "com.rokkon.echo":
        level: DEBUG
      "io.grpc":
        level: DEBUG
```

## Dependencies
- Quarkus gRPC
- Rokkon Protobuf definitions
- Rokkon Commons utilities

## Minimal Implementation Example

```java
@GrpcService
@Singleton
public class EchoServiceImpl implements PipeStepProcessor {
    
    private static final Logger LOG = Logger.getLogger(EchoServiceImpl.class);
    
    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        LOG.debugf("Echo received: %s", request.hasDocument() ? 
                   request.getDocument().getId() : "no document");
        
        ProcessResponse response = ProcessResponse.newBuilder()
                .setSuccess(true)
                .setOutputDoc(request.getDocument())
                .addProcessorLogs("Echo: processed successfully")
                .build();
                
        return Uni.createFrom().item(response);
    }
    
    @Override
    public Uni<ServiceRegistrationData> getServiceRegistration(Empty request) {
        return Uni.createFrom().item(
            ServiceRegistrationData.newBuilder()
                .setModuleName("echo")
                .build()
        );
    }
    
    @Override
    public Uni<ProcessResponse> registrationCheck(ProcessRequest request) {
        // Same as processData for echo
        return processData(request);
    }
}