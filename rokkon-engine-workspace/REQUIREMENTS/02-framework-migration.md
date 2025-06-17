# Framework Migration Requirements - Micronaut to Quarkus

## Overview
Detailed requirements for migrating from Micronaut framework to Quarkus, focusing on dependency injection, configuration, and core framework services.

## Required Quarkus Extensions

### Core Extensions
```xml
<!-- Core Quarkus -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-arc</artifactId> <!-- CDI -->
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-config-yaml</artifactId>
</dependency>

<!-- gRPC -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-grpc</artifactId>
</dependency>

<!-- Consul Integration -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-consul-config</artifactId>
</dependency>

<!-- Kafka (SmallRye Reactive Messaging) -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-reactive-messaging-kafka</artifactId>
</dependency>

<!-- Health Checks -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-health</artifactId>
</dependency>

<!-- Metrics -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
</dependency>

<!-- Testing -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-junit5</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-test-consul-devservices</artifactId>
    <scope>test</scope>
</dependency>
</xml>
```

## Annotation Migration Map

### Dependency Injection
| Micronaut | Quarkus | Notes |
|-----------|---------|-------|
| `@Singleton` | `@ApplicationScoped` | Main singleton scope |
| `@Prototype` | `@Dependent` | New instance per injection |
| `@Inject` | `@Inject` | Same annotation |
| `@Named` | `@Named` | Same annotation |
| `@Factory` | `@Produces` | Method-level producer |
| `@Bean` | `@Produces` | Method-level producer |

### Configuration
| Micronaut | Quarkus | Notes |
|-----------|---------|-------|
| `@ConfigurationProperties` | `@ConfigProperties` | Class-level config binding |
| `@Property` | `@ConfigProperty` | Field-level config injection |
| `@Value` | `@ConfigProperty` | Property value injection |

### HTTP & REST (if needed)
| Micronaut | Quarkus | Notes |
|-----------|---------|-------|
| `@Controller` | `@Path` | REST endpoint class |
| `@Get` | `@GET` | HTTP GET method |
| `@Post` | `@POST` | HTTP POST method |

### Event Handling
| Micronaut | Quarkus | Notes |
|-----------|---------|-------|
| `@EventListener` | `@Observes` | CDI event observer |
| `ApplicationEventPublisher` | `Event<T>` | Event publishing |

## Configuration Migration

### Application Properties Structure
```properties
# Quarkus configuration format
quarkus.application.name=rokkon-engine
quarkus.profile=dev

# Consul configuration
quarkus.consul-config.enabled=true
quarkus.consul-config.agent.host-port=localhost:8500
quarkus.consul-config.properties-value-keys=rokkon-clusters/default/config

# gRPC configuration
quarkus.grpc.server.port=9090
quarkus.grpc.server.host=0.0.0.0

# Kafka configuration (when added)
kafka.bootstrap.servers=localhost:9092
mp.messaging.incoming.pipeline-requests.connector=smallrye-kafka
mp.messaging.outgoing.pipeline-responses.connector=smallrye-kafka
```

### Dynamic Configuration Requirements
- **Consul KV Watching**: Implement custom configuration source for dynamic updates
- **CAS Operations**: Maintain Compare-And-Set operations for config updates
- **Real-time Updates**: Configuration changes without restart
- **Version Tracking**: Configuration version management

## Service Discovery Migration

### Consul Integration
```java
// Micronaut approach (old)
@Singleton
public class ServiceDiscovery {
    @Inject
    private ConsulClient consulClient;
}

// Quarkus approach (new)
@ApplicationScoped
public class ServiceDiscovery {
    @Inject
    private Vertx vertx; // For Consul client

    // Custom Consul client integration
}
```

### Health Check Migration
```java
// Micronaut (old)
@Singleton
public class ModuleHealthIndicator implements HealthIndicator {
    @Override
    public Publisher<HealthResult> getResult() {
        // health check logic
    }
}

// Quarkus (new)
@ApplicationScoped
public class ModuleHealthCheck {
    @Readiness
    public HealthCheckResponse checkModules() {
        // health check logic
        return HealthCheckResponse.up("modules");
    }
}
```

## gRPC Service Migration

### Server Implementation
```java
// Micronaut (old)
@Singleton
public class PipelineServiceImpl extends PipelineServiceGrpc.PipelineServiceImplBase {
    // implementation
}

// Quarkus (new)
@GrpcService
public class PipelineService implements PipelineGrpc {
    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        // implementation using reactive streams
    }
}
```

### Client Configuration
```java
// Micronaut (old)
@Singleton
public class ModuleClient {
    @GrpcChannel("module-service")
    private ModuleServiceGrpc.ModuleServiceBlockingStub moduleStub;
}

// Quarkus (new)
@ApplicationScoped
public class ModuleClient {
    @GrpcClient("module-service")
    ModuleService moduleService;
}
```

## Testing Migration

### Test Class Structure
```java
// Micronaut (old)
@MicronautTest
@Testcontainers
class EngineIntegrationTest {
    @Container
    static ConsulContainer consul = new ConsulContainer();
}

// Quarkus (new)
@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
class EngineIntegrationTest {
    // Quarkus handles Consul via dev services
}
```

### Test Configuration
```java
// Quarkus test profile
public class IntegrationTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.consul-config.enabled", "true",
            "quarkus.devservices.enabled", "true"
        );
    }
}
```

## Migration Steps

### Phase 1: Project Setup
1. Create new Quarkus project structure
2. Add required extensions to pom.xml
3. Migrate protobuf definitions
4. Setup basic application.properties

### Phase 2: Core Services
1. Migrate configuration classes with new annotations
2. Convert singleton services to ApplicationScoped
3. Update dependency injection points
4. Migrate health check implementations

### Phase 3: gRPC Services
1. Convert gRPC service implementations
2. Update client configurations
3. Migrate interceptors and middleware
4. Test gRPC connectivity

### Phase 4: Integration Points
1. Setup Consul integration
2. Configure dynamic configuration watching
3. Implement service discovery
4. Test end-to-end connectivity

## Pain Points to Address

### Consul Integration Reliability
- **Issue**: Micronaut Consul integration was brittle, often requiring 2-3 hours to debug
- **Solution**: Use Quarkus dev services for testing, simpler Consul client integration
- **Testing**: Automatic random port assignment, reliable container startup

### Configuration Complexity
- **Issue**: Complex multi-module configuration in Micronaut
- **Solution**: Single monolithic configuration with clear property hierarchy
- **Dynamic Updates**: Simpler consul-config integration

### Test Reliability
- **Issue**: Inconsistent integration tests with Micronaut
- **Solution**: Leverage Quarkus testing features and dev services
- **Consul Testing**: Built-in Consul test containers with proper lifecycle management
