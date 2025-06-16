# Integration Requirements - Consul, Kafka, gRPC

## Overview
Detailed requirements for integrating Consul, Kafka, and gRPC services in the Quarkus-based YAPPY Engine, focusing on reliability and ease of testing.

## Consul Integration Requirements

### Service Discovery & Configuration
- **Extension**: Use `quarkus-consul-config` for configuration management
- **Service Registration**: Automatic service registration with health checks
- **Dynamic Configuration**: Real-time configuration updates via Consul KV watches
- **Health Integration**: Consul health checks integrated with Quarkus Health

### Configuration Structure
Maintain existing Consul KV structure:
```
/yappy-clusters/<cluster-name>/
├── config.json              # PipelineClusterConfig
├── pipelines/               # Individual pipeline configs
│   ├── pipeline-1.json
│   └── pipeline-2.json
├── connector-mappings/      # Source routing configs
├── modules/registry/        # Module configurations
│   ├── chunker.json
│   └── embedder.json
└── status/services/         # Runtime status
    ├── engine-status.json
    └── module-status.json
```

### Consul Client Configuration
```properties
# Basic Consul configuration
quarkus.consul-config.enabled=true
quarkus.consul-config.agent.host-port=${CONSUL_HOST:localhost}:${CONSUL_PORT:8500}
quarkus.consul-config.properties-value-keys=yappy-clusters/${CLUSTER_NAME:default}/config

# Service registration
quarkus.consul-config.agent.service-name=rokkon-engine
quarkus.consul-config.agent.service-port=${GRPC_PORT:9090}
quarkus.consul-config.agent.service-check-interval=10s
```

### Dynamic Configuration Implementation
```java
@ApplicationScoped
public class ConsulConfigWatcher {
    
    @ConfigProperty(name = "consul.kv.watch.paths")
    List<String> watchPaths;
    
    // Custom implementation for watching Consul KV changes
    // Trigger configuration reload on changes
    // Implement Compare-And-Set operations
}
```

### Testing Requirements
- **Dev Services**: Use Quarkus Consul dev services for testing
- **Random Ports**: Automatic port assignment for test containers
- **Reliable Startup**: Consul container must start reliably every time
- **No Manual Configuration**: Tests should "just work" without complex setup

```java
@QuarkusTest
@TestProfile(ConsulTestProfile.class)
class ConsulIntegrationTest {
    // Test should automatically get Consul container
    // with proper configuration and random ports
}
```

## gRPC Integration Requirements

### Primary Transport Implementation
- **Extension**: Use `quarkus-grpc` extension
- **Protocol**: gRPC-first approach for all module communication
- **Reactive**: Use Mutiny reactive streams for non-blocking operations

### Server Configuration
```properties
# gRPC Server configuration
quarkus.grpc.server.port=${GRPC_PORT:9090}
quarkus.grpc.server.host=0.0.0.0
quarkus.grpc.server.enable-reflection=true

# Connection management
quarkus.grpc.server.max-inbound-message-size=4MB
quarkus.grpc.server.handshake-timeout=10s
```

### Core gRPC Services
All modules must implement this interface:
```proto
service ModuleService {
  // Core processing method
  rpc ProcessData(ProcessRequest) returns (ProcessResponse);
  
  // Service registration
  rpc GetServiceRegistration() returns (ServiceRegistrationData);
  
  // Health check (standard gRPC health)
  rpc Check(HealthCheckRequest) returns (HealthCheckResponse);
}
```

### gRPC Service Implementation
```java
@GrpcService
public class EngineService implements EngineGrpc {
    
    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        return processDataAsync(request)
            .onFailure()
            .retry()
            .withBackOff(Duration.ofSeconds(1), Duration.ofSeconds(10))
            .atMost(3);
    }
}
```

### Client Connection Management
```java
@ApplicationScoped
public class ModuleClientPool {
    
    // Maintain connection pools per module type
    // Implement circuit breaker pattern
    // Handle connection failures gracefully
    
    @GrpcClient("chunker-service")
    ChunkerService chunkerClient;
    
    @GrpcClient("embedder-service") 
    EmbedderService embedderClient;
}
```

### Connection Discovery
- **Service Discovery**: Discover gRPC services via Consul
- **Load Balancing**: Round-robin or least-connections
- **Health Monitoring**: Regular health checks to registered services
- **Failover**: Automatic failover to healthy instances

## Kafka Integration Requirements (Phase 2)

### SmallRye Reactive Messaging
- **Extension**: Use `quarkus-smallrye-reactive-messaging-kafka`
- **Dynamic Listeners**: Support for runtime topic subscription
- **Message Routing**: Configuration-driven message routing

### Dynamic Kafka Consumer Implementation
```java
@ApplicationScoped
public class DynamicKafkaManager {
    
    @Inject
    KafkaConsumer<String, byte[]> kafkaConsumer;
    
    // Support for dynamic topic subscription based on pipeline config
    public void subscribeToTopics(List<String> topics) {
        // Dynamic subscription logic
        // Vert.x Kafka client may be needed for full dynamic support
    }
}
```

### Kafka Configuration
```properties
# Kafka basic configuration
kafka.bootstrap.servers=${KAFKA_BROKERS:localhost:9092}
kafka.group.id=rokkon-engine
kafka.auto.offset.reset=earliest

# SmallRye Reactive Messaging channels
mp.messaging.incoming.pipeline-requests.connector=smallrye-kafka
mp.messaging.incoming.pipeline-requests.topic=pipeline-requests
mp.messaging.incoming.pipeline-requests.group.id=rokkon-engine

mp.messaging.outgoing.pipeline-responses.connector=smallrye-kafka
mp.messaging.outgoing.pipeline-responses.topic=pipeline-responses
```

### Message Processing
```java
@ApplicationScoped
public class KafkaMessageProcessor {
    
    @Incoming("pipeline-requests")
    @Outgoing("pipeline-responses")
    public Uni<ProcessResponse> processMessage(ProcessRequest request) {
        // Route to appropriate gRPC service based on configuration
        return routeToModule(request);
    }
}
```

### Slot Manager Integration (TBD)
- **Consul Claims**: Maintain Consul-based partition claiming
- **Consumer Coordination**: Prevent excessive Kafka consumers
- **Dynamic Scaling**: Support for dynamic consumer scaling

```java
@ApplicationScoped 
public class KafkaSlotManager {
    
    // Consul-based slot claiming
    // Prevent multiple consumers on same partition
    // Coordinate consumer group management
}
```

## Integration Testing Strategy

### Test Container Setup
```java
@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
class FullIntegrationTest {
    
    // Quarkus dev services should handle:
    // - Consul container with random port
    // - Kafka container with random port  
    // - Proper service discovery setup
    // - Health checks working
}
```

### Test Profile Configuration
```java
public class IntegrationTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.devservices.enabled", "true",
            "quarkus.consul-config.enabled", "true",
            "quarkus.grpc.server.port", "0", // Random port
            "%test.kafka.bootstrap.servers", "localhost:${kafka.port}" // Random port
        );
    }
}
```

### Service Discovery Testing
- **Module Registration**: Test automatic module registration
- **Health Checks**: Verify health check propagation
- **Configuration Updates**: Test dynamic configuration changes
- **Failover Scenarios**: Test service failure and recovery

## Error Handling & Resilience

### Circuit Breaker Pattern
```java
@ApplicationScoped
public class ResilientModuleClient {
    
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 1000)
    @Retry(maxRetries = 3, delay = 500)
    public Uni<ProcessResponse> callModule(ProcessRequest request) {
        // gRPC call with resilience patterns
    }
}
```

### Monitoring & Observability
- **Metrics**: Integration with Micrometer/Prometheus
- **Tracing**: Distributed tracing for request flows
- **Logging**: Structured logging with correlation IDs
- **Health Dashboards**: Comprehensive health monitoring

## Performance Requirements

### Connection Pooling
- **gRPC Connections**: Efficient connection reuse
- **Kafka Consumers**: Optimal consumer group management
- **Consul Clients**: Connection pooling for Consul operations

### Resource Management
- **Memory Usage**: Efficient memory management for large pipelines
- **Thread Pools**: Proper thread pool configuration
- **Backpressure**: Natural backpressure handling with reactive streams

### Startup Performance
- **Fast Startup**: Sub-5 second startup time in JVM mode
- **Service Discovery**: Quick service registration and discovery
- **Configuration Loading**: Efficient configuration loading from Consul