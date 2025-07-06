# Dynamic gRPC Module Context

## Overview
The `engine:dynamic-grpc` module provides dynamic service discovery and gRPC client management for the Pipeline Engine. It enables the engine to discover and connect to module services registered in Consul without requiring pre-configuration in Stork.

## Architecture

### Key Components

1. **ServiceDiscovery Interface** (`com.rokkon.pipeline.engine.grpc.ServiceDiscovery`)
   - Defines the contract for service discovery implementations
   - Methods: `discoverService(name)`, `discoverAllInstances(name)`
   - Returns Stork `ServiceInstance` objects

2. **DynamicConsulServiceDiscovery** 
   - Direct Consul integration without Stork configuration
   - Implements random load balancing across healthy instances
   - Throws `ServiceDiscoveryException` for failures

3. **DynamicGrpcClientFactory**
   - Main factory for creating gRPC clients
   - Supports both traditional `PipeStepProcessor` and `MutinyPipeStepProcessorStub`
   - Built-in channel caching with proper lifecycle management
   - Methods:
     - `getClient(host, port)` - Direct connection
     - `getClientForService(serviceName)` - With discovery and caching
     - `getMutinyClient(host, port)` - Mutiny stub direct connection
     - `getMutinyClientForService(serviceName)` - Mutiny with discovery

4. **GrpcClientProvider**
   - Generic provider supporting any Mutiny stub type
   - Uses reflection to create stubs dynamically
   - Enables type-safe client creation without compile-time dependencies

5. **RandomLoadBalancer**
   - Simple random selection implementation of Stork's LoadBalancer
   - Used by DynamicConsulServiceDiscovery for instance selection

## Integration with Engine

The engine's `GrpcTransportHandler` uses this module to route requests:

```java
@Inject
DynamicGrpcClientFactory grpcClientFactory;

// In routeRequest method:
return grpcClientFactory.getMutinyClientForService(serviceName)
    .flatMap(client -> client.processData(request));
```

## Service Discovery Flow

1. Pipeline configuration specifies service name (e.g., "echo", "test")
2. GrpcTransportHandler requests client for service name
3. DynamicGrpcClientFactory checks cache for existing channel
4. If not cached, DynamicConsulServiceDiscovery queries Consul
5. Consul returns healthy service instances
6. RandomLoadBalancer selects one instance
7. Channel is created and cached
8. Mutiny stub is created from channel
9. Request is sent through the stub

## Configuration

### Application Configuration
```yaml
quarkus:
  cache:
    caffeine:
      "grpc-channels":
        expire-after-write: 10m
        maximum-size: 100

consul:
  host: ${CONSUL_HOST:localhost}
  port: ${CONSUL_PORT:8500}

rokkon:
  service-discovery:
    type: consul-direct  # Enables dynamic discovery
```

### Service Names
- Modules register with simple names: "echo", "test", "chunker", etc.
- No UUID suffixes for load balancing (Consul handles distribution)
- Instance IDs are stored in metadata for tracking

## Testing Strategy

### Unit Tests
1. **DynamicGrpcClientFactoryTest**
   - Test direct client creation
   - Test channel reuse
   - Test service discovery with mocked Consul
   - Test failure scenarios

2. **DynamicConsulServiceDiscoveryTest**
   - Test healthy instance discovery
   - Test load balancing behavior
   - Test empty service handling
   - Test Consul connection failures

### Integration Tests
1. **DynamicGrpcIntegrationTest**
   - Uses Testcontainers for real Consul
   - Tests full service registration and discovery
   - Tests actual gRPC calls through discovered services
   - Tests load balancing across multiple instances

### Testing Challenges
- Need to mock/stub protobuf-generated classes
- Consul container networking (use host.docker.internal)
- Timing issues with service registration
- Channel lifecycle management in tests

## Common Issues and Solutions

### Issue: Services not discovered
**Solution**: Check that:
- Service is registered with correct name in Consul
- Service health checks are passing
- No firewall/network issues between engine and module

### Issue: Channel leaks
**Solution**: 
- Ensure @PreDestroy cleanup is called
- Use try-with-resources for test channels
- Monitor channel count in production

### Issue: Load balancing not working
**Solution**:
- Verify multiple instances registered in Consul
- Check that RandomLoadBalancer is being used
- Use uncached discovery for testing

## Future Enhancements

1. **Additional Load Balancers**
   - Round-robin
   - Least connections
   - Response time based

2. **Circuit Breaker Integration**
   - Fault tolerance for failing services
   - Automatic retry with backoff

3. **Metrics Collection**
   - Channel creation/destruction metrics
   - Service discovery latency
   - Load balancer distribution stats

4. **TLS Support**
   - Secure channel creation
   - Certificate management
   - mTLS for service-to-service

5. **Streaming Support**
   - Bi-directional streaming clients
   - Stream lifecycle management