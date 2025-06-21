# Engine Consul

## Overview

Engine Consul is the **ONLY** component in the Rokkon Engine ecosystem that can write to Consul. It serves as a gatekeeper, ensuring all Consul modifications go through proper validation and maintain consistency. This architectural decision prevents accidental or malicious configuration corruption.

## Critical Design Principle

**No other component should have write access to Consul.** This is enforced by:
- Using HTTP client (not quarkus-config-consul) for write operations
- Implementing all validation before writes
- Providing a service interface that other components call
- Using CAS (Compare-And-Swap) for atomic updates

## Core Responsibilities

1. **Service Registration** - Register modules as Consul services with health checks
2. **Configuration Management** - Write and update pipeline configurations
3. **Validation** - Ensure all data written to Consul is valid
4. **Atomic Updates** - Use CAS operations to prevent race conditions

## Architecture

### Consul HTTP Client

```java
@RegisterRestClient(configKey = "consul-api")
@Path("/v1")
public interface ConsulClient {
    // KV Store operations
    @GET
    @Path("/kv/{key}")
    Response getKey(@PathParam("key") String key);
    
    @PUT
    @Path("/kv/{key}")
    Response putKey(@PathParam("key") String key, 
                    @QueryParam("cas") Long modifyIndex,
                    String value);
    
    // Service registration
    @PUT
    @Path("/agent/service/register")
    Response registerService(ConsulServiceDefinition service);
    
    // Health checks
    @PUT
    @Path("/agent/check/register")
    Response registerCheck(ConsulCheckDefinition check);
}
```

### Service Registration Flow

1. **Receive registration request** from engine-registration
2. **Validate service definition**
   - Required fields present
   - Valid health check configuration
   - Proper naming conventions
3. **Register service** in Consul
4. **Configure health check** (gRPC only)
5. **Return registration status**

### Configuration Management

Pipeline configurations are stored in Consul KV with validation:

```java
@Retry(maxRetries = 3, delay = 100, jitter = 50, 
       retryOn = CASConflictException.class)
public void updatePipelineConfig(String pipelineId, 
                                PipelineConfig config) {
    // Validate configuration
    validator.validatePipelineConfig(config);
    
    // Get current value with CAS index
    ConsulValue current = consulClient.getValue(
        "pipelines/" + pipelineId
    );
    
    // Update with CAS
    String json = objectMapper.writeValueAsString(config);
    Response response = consulClient.putKey(
        "pipelines/" + pipelineId,
        current.getModifyIndex(),
        json
    );
    
    if (response.getStatus() != 200) {
        throw new CASConflictException("Update failed");
    }
}
```

## Key Services

### ConsulRegistrationService

Handles module registration in Consul:
- Creates service definitions
- Configures gRPC health checks
- Manages service metadata
- Handles deregistration

### ConsulConfigurationService

Manages pipeline configurations:
- Validates configuration schema
- Implements CAS updates
- Handles configuration versioning
- Provides audit trail

### ConsulHealthService

Monitors and updates health status:
- Reads health check results
- Updates service status
- Manages health check definitions

## Configuration

### application.yml
```yaml
quarkus:
  rest-client:
    consul-api:
      url: ${CONSUL_URL:http://localhost:8500}
      connect-timeout: 5000
      read-timeout: 5000

consul:
  datacenter: ${CONSUL_DATACENTER:dc1}
  token: ${CONSUL_TOKEN:}
  
  # CAS retry configuration
  cas:
    max-retries: 3
    retry-delay: 100ms
    
  # Health check defaults
  health:
    interval: 10s
    timeout: 5s
    deregister-critical-after: 1m
```

## API Examples

### Service Registration
```java
@ApplicationScoped
public class ModuleRegistrar {
    @Inject
    ConsulRegistrationService consulService;
    
    public String registerModule(ModuleInfo module) {
        ConsulServiceDefinition service = ConsulServiceDefinition.builder()
            .name(module.getServiceName())
            .id(module.getServiceId())
            .address(module.getHost())
            .port(module.getPort())
            .tags(List.of("rokkon-module", module.getType()))
            .check(ConsulCheck.grpc(
                module.getHost() + ":" + module.getPort(),
                "10s"
            ))
            .build();
            
        return consulService.registerService(service);
    }
}
```

### Configuration Update
```java
@ApplicationScoped
public class PipelineManager {
    @Inject
    ConsulConfigurationService configService;
    
    public void updatePipeline(String id, PipelineConfig config) {
        // Validation happens inside the service
        configService.updatePipelineConfig(id, config);
    }
}
```

## Security Considerations

1. **Access Control**
   - Use Consul ACL tokens for authentication
   - Restrict write permissions to engine-consul only
   - Audit all write operations

2. **Validation**
   - Schema validation for all configurations
   - Business rule validation before writes
   - Prevent injection attacks

3. **Atomic Operations**
   - Always use CAS for updates
   - Handle conflicts with retries
   - Maintain data consistency

## Error Handling

### CAS Conflicts
- Automatic retry with exponential backoff
- Maximum 3 retries by default
- Returns conflict error after max retries

### Validation Failures
- Return detailed error messages
- Log validation failures
- Prevent any writes on validation error

### Network Failures
- Circuit breaker pattern for Consul connection
- Fallback to cached data for reads
- Queue writes for retry

## Monitoring

### Metrics
- `consul_writes_total` - Total write operations
- `consul_write_errors_total` - Failed writes by type
- `consul_cas_conflicts_total` - CAS conflict count
- `consul_validation_failures_total` - Validation failures

### Health Checks
- Consul connection health
- Write permission verification
- Configuration validation status

## Testing

### Unit Tests
- Mock Consul client
- Test validation logic
- Test CAS retry behavior

### Integration Tests
- Real Consul instance (testcontainers)
- Test service registration
- Test configuration updates
- Verify CAS operations

## Best Practices

1. **Never bypass this service** - All Consul writes must go through engine-consul
2. **Validate everything** - Never trust input data
3. **Use CAS always** - Prevent race conditions
4. **Log all operations** - Maintain audit trail
5. **Handle failures gracefully** - Don't corrupt Consul state

## Troubleshooting

### Write Failures
1. Check Consul ACL token permissions
2. Verify network connectivity
3. Check validation error messages
4. Review CAS conflict logs

### Service Registration Issues
1. Verify service definition is complete
2. Check health check endpoint accessibility
3. Ensure unique service IDs
4. Review Consul logs

### Configuration Problems
1. Validate JSON schema
2. Check for CAS conflicts
3. Verify key paths are correct
4. Ensure proper encoding