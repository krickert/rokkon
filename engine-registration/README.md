# Engine Registration

## Overview

The Engine Registration module implements the `ModuleRegistrationService` gRPC service that handles module registration for the Rokkon Engine. This service runs as part of the main engine but is maintained as a separate module for clarity and modularity.

## Architecture

This module provides:
- gRPC service implementation for module registration
- Module validation logic and whitelisting
- Integration with engine-consul for Consul writes
- Module health verification via `RegistrationCheck` RPC

## Core Service: ModuleRegistrationService

```proto
service ModuleRegistration {
  // Register a module with the engine
  rpc RegisterModule(ModuleInfo) returns (RegistrationStatus);
  
  // Unregister a module
  rpc UnregisterModule(ModuleId) returns (UnregistrationStatus);
  
  // Heartbeat from module to engine
  rpc Heartbeat(ModuleHeartbeat) returns (HeartbeatAck);
  
  // Get health status of a specific module
  rpc GetModuleHealth(ModuleId) returns (ModuleHealthStatus);
  
  // List all registered modules
  rpc ListModules(google.protobuf.Empty) returns (ModuleList);
}
```

## Registration Flow

1. **Receive Registration Request**
   - CLI tool (running in module container) calls `RegisterModule`
   - Request includes module metadata, host/port info

2. **Validate Module**
   - Check module name against whitelist
   - Allowed modules: `echo`, `chunker`, `parser`, `embedder`, `opensearch-sink`
   - Verify required fields are present

3. **Health Check**
   - Connect to module's gRPC endpoint
   - Perform standard gRPC health check
   - Call module's `RegistrationCheck` RPC with test data

4. **Register with Consul**
   - Call engine-consul service to write to Consul
   - Never write to Consul directly
   - Include gRPC health check configuration

5. **Return Status**
   - Success with Consul service ID
   - Or failure with detailed error message

## Module Validation

### Whitelist Enforcement
```java
private static final Set<String> WHITELISTED_MODULES = Set.of(
    "echo",
    "chunker", 
    "parser",
    "embedder",
    "opensearch-sink"
);
```

### RegistrationCheck Validation
- Creates a test `ProcessRequest` with minimal data
- Calls module's `RegistrationCheck` RPC
- Expects successful `ProcessResponse`
- Verifies module can handle basic requests

## Integration with Engine-Consul

All Consul operations go through engine-consul:
```java
@Inject
ConsulRegistrationService consulService;

// Register module in Consul
ConsulServiceRegistration registration = ConsulServiceRegistration.builder()
    .name(moduleInfo.getServiceName())
    .id(moduleInfo.getServiceId())
    .address(moduleInfo.getHost())
    .port(moduleInfo.getPort())
    .check(ConsulHealthCheck.grpc(
        moduleInfo.getHost() + ":" + moduleInfo.getPort(),
        "10s"
    ))
    .build();

consulService.registerService(registration);
```

## Configuration

### application.yml
```yaml
quarkus:
  grpc:
    server:
      port: 9099  # gRPC port for registration service
    clients:
      engine-consul:
        host: localhost
        port: 9098  # engine-consul gRPC port

registration:
  validation:
    enabled: true
    whitelist: ${REGISTRATION_WHITELIST:echo,chunker,parser,embedder,opensearch-sink}
  health-check:
    timeout: 5s
    test-data-size: 1024  # bytes for test document
```

## Error Handling

### Registration Failures
- Module not in whitelist → `PERMISSION_DENIED`
- Health check failed → `UNAVAILABLE`
- RegistrationCheck failed → `FAILED_PRECONDITION`
- Consul write failed → `INTERNAL`

### Retry Logic
- Health checks: 3 retries with exponential backoff
- Consul writes: Handled by engine-consul with CAS logic

## Testing

### Unit Tests
- Mock Consul service
- Test whitelist validation
- Test error scenarios

### Integration Tests
- Use test-module for end-to-end flow
- Verify Consul registration
- Test health check integration

## API Usage Example

### From CLI Tool
```java
// Inside module container
ManagedChannel channel = ManagedChannelBuilder
    .forAddress(engineHost, enginePort)
    .usePlaintext()
    .build();

ModuleRegistrationGrpc.ModuleRegistrationBlockingStub stub = 
    ModuleRegistrationGrpc.newBlockingStub(channel);

ModuleInfo moduleInfo = ModuleInfo.newBuilder()
    .setServiceName("chunker")
    .setServiceId("chunker-" + UUID.randomUUID())
    .setHost(containerHostname)
    .setPort(9090)
    .putMetadata("version", "1.0.0")
    .build();

RegistrationStatus status = stub.registerModule(moduleInfo);
```

## Monitoring

### Metrics
- `module_registrations_total` - Counter of registration attempts
- `module_registration_duration_seconds` - Histogram of registration time
- `active_modules` - Gauge of currently registered modules

### Logging
- INFO: Successful registrations
- WARN: Validation failures
- ERROR: System failures (Consul, network)

## Future Enhancements

- [ ] Module capability discovery
- [ ] Version compatibility checks
- [ ] Resource requirement validation
- [ ] Module dependency resolution
- [ ] Automatic re-registration on failure