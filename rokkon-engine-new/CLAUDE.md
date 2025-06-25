# Rokkon Mock Engine Development Guidelines

## Project Overview

This document provides comprehensive guidelines for developing the Rokkon mock engine in the `rokkon-engine-new/pipestream-mock` directory. The mock engine serves as:
- Phase 0 of the connector server development
- A permanent testing fixture for connector development
- A validation platform for the new architecture
- A testing tool that will be used throughout the project lifecycle

**IMPORTANT**: 
- This is a standalone testing project under `rokkon-engine-new/pipestream-mock`
- It will NOT become the real pipestream (that will be `rokkon-engine-new/pipestream`)
- It should NOT depend on the existing `rokkon-engine` code
- It will be used for testing both the connector server and future pipestream development

## Architecture Principles

### 1. Dual Interface Support
The mock engine MUST support both:
- **Legacy Interface**: `ConnectorEngine` from `connector_service.proto`
- **New Interface**: `PipeStepProcessor` from `pipe_step_processor_service.proto`

### 2. Isolation
- No dependencies on `rokkon-engine` code
- Only import `rokkon-protobuf` for proto definitions
- Create minimal shared components in `rokkon-engine-new/commons` if needed

### 3. Testing Focus
- This is a mock for testing, not production use
- Prioritize test control features (REST API, request storage, verification)
- Make debugging easy with clear logging and UI

## Development Standards

### 1. Mutiny gRPC Implementation

#### Build Configuration (build.gradle.kts)
```kotlin
plugins {
    java
    id("io.quarkus")
}

dependencies {
    // Import the BOM for version management
    implementation(platform(project(":rokkon-bom")))
    
    // Core dependencies from BOM
    // io.quarkus:quarkus-grpc is included
    // rokkon-protobuf for proto definitions
    implementation(project(":rokkon-protobuf"))
    
    // Additional dependencies
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-config-yaml")
    
    // Test dependencies
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
}

// Configure Mutiny code generation
quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}
```

#### Application Configuration (application.yml)
```yaml
quarkus:
  application:
    name: rokkon-mock-engine
  
  # Generate Mutiny-based gRPC code
  generate-code:
    grpc:
      scan-for-proto: com.rokkon.pipeline:rokkon-protobuf,com.google.api.grpc:proto-google-common-protos
  
  # gRPC server configuration
  grpc:
    server:
      port: 49000
      host: 0.0.0.0
      enable-reflection-service: true
      max-inbound-message-size: 1073741824  # 1GB
      services:
        # Both services will be registered
        connector-engine:
          enable: true
        pipe-step-processor:
          enable: true
  
  # HTTP configuration for REST API
  http:
    port: 8080
    host: 0.0.0.0
```

#### Service Implementation Pattern
```java
// Legacy ConnectorEngine implementation
@GrpcService
@Singleton
public class MockConnectorEngine implements ConnectorEngine {
    
    @Inject
    RequestStore requestStore;
    
    @Override
    public Uni<ConnectorResponse> processConnectorDoc(ConnectorRequest request) {
        return Uni.createFrom().item(() -> {
            // Store request for verification
            requestStore.storeConnectorRequest(request);
            
            // Return configurable response
            return ConnectorResponse.newBuilder()
                .setStreamId(generateStreamId())
                .setAccepted(true)
                .setMessage("Mock accepted")
                .build();
        });
    }
}

// New PipeStepProcessor implementation
@GrpcService
@Singleton
public class MockPipeStepProcessor implements PipeStepProcessor {
    
    @Inject
    RequestStore requestStore;
    
    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        return Uni.createFrom().item(() -> {
            // Store request for verification
            requestStore.storePipelineRequest(request);
            
            // Return success response
            return ProcessResponse.newBuilder()
                .setSuccess(true)
                .setOutputDoc(request.getDocument())
                .build();
        });
    }
    
    @Override
    public Uni<ProcessResponse> testProcessData(ProcessRequest request) {
        // Same as processData for mock
        return processData(request);
    }
    
    @Override
    public Uni<ServiceRegistrationResponse> getServiceRegistration(RegistrationRequest request) {
        return Uni.createFrom().item(() -> {
            return ServiceRegistrationResponse.newBuilder()
                .setModuleName("mock-pipeline-module")
                .setVersion("1.0.0")
                .setHealthCheckPassed(true)
                .setHealthCheckMessage("Mock module healthy")
                .build();
        });
    }
}
```

### 2. Docker Image Creation

#### Dockerfile.jvm (Standard JVM Build)
```dockerfile
FROM registry.access.redhat.com/ubi9/openjdk-21:1.21

ENV LANGUAGE='en_US:en'

# Copy Quarkus application layers
COPY --chown=185 build/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 build/quarkus-app/*.jar /deployments/
COPY --chown=185 build/quarkus-app/app/ /deployments/app/
COPY --chown=185 build/quarkus-app/quarkus/ /deployments/quarkus/

# Expose ports
EXPOSE 8080 49000

USER 185

# Environment configuration
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
```

#### docker-compose.yml
```yaml
version: '3.8'
services:
  mock-engine:
    build:
      context: .
      dockerfile: src/main/docker/Dockerfile.jvm
    ports:
      - "8080:8080"   # REST API
      - "49000:49000" # gRPC
    environment:
      - QUARKUS_LOG_LEVEL=INFO
      - QUARKUS_LOG_CATEGORY_COM_ROKKON_LEVEL=DEBUG
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/q/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
  
  consul:
    image: consul:1.19
    ports:
      - "8500:8500"
    environment:
      - CONSUL_BIND_INTERFACE=eth0
    command: agent -server -bootstrap-expect=1 -ui -client=0.0.0.0
```

### 3. CLI Integration

For the mock engine, we will NOT include the full rokkon CLI since:
- The mock engine doesn't need to register with a real engine
- We want to keep it lightweight
- The mock should be self-contained

Instead, we'll provide mock module registrations via the REST API:
```java
@Path("/api/mock/modules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MockModuleResource {
    
    @Inject
    MockModuleRegistry registry;
    
    @POST
    public Response registerModule(ModuleRegistration registration) {
        registry.register(registration);
        return Response.ok().build();
    }
    
    @GET
    public List<ModuleRegistration> listModules() {
        return registry.list();
    }
}
```

### 4. Testing Strategy

#### Unit Tests
Since the mock engine is itself a testing tool, unit tests should focus on:
- Request storage and retrieval
- Response configuration
- REST API endpoints
- State management

```java
@QuarkusTest
class MockConnectorEngineTest {
    
    @Inject
    RequestStore requestStore;
    
    @Test
    void testRequestStorage() {
        // Given
        ConnectorRequest request = ConnectorRequest.newBuilder()
            .setConnectorType("test")
            .build();
        
        // When
        requestStore.storeConnectorRequest(request);
        
        // Then
        assertThat(requestStore.getConnectorRequests()).hasSize(1);
        assertThat(requestStore.getConnectorRequests().get(0))
            .extracting(ConnectorRequest::getConnectorType)
            .isEqualTo("test");
    }
}
```

#### Integration Tests
**Decision**: We will NOT create separate integration tests for the mock engine because:
1. The mock engine IS the integration test infrastructure
2. Using it in connector tests validates its functionality
3. It would be testing a test tool, which adds little value

Instead, we'll provide:
- Example test scenarios in the documentation
- Test helpers for common patterns
- Clear usage examples

## Project Structure

```
rokkon-engine-new/
├── pipestream-mock/              # Mock engine for testing (permanent)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/rokkon/mock/
│   │   │   │   ├── engine/
│   │   │   │   │   ├── MockConnectorEngine.java      # Legacy interface
│   │   │   │   │   ├── MockPipeStepProcessor.java    # New interface
│   │   │   │   │   └── RequestStore.java             # Shared storage
│   │   │   │   ├── api/
│   │   │   │   │   ├── MockEngineResource.java       # REST API
│   │   │   │   │   └── dto/                          # DTOs for REST
│   │   │   │   └── config/
│   │   │   │       └── MockEngineConfig.java         # Configuration
│   │   │   ├── resources/
│   │   │   │   ├── application.yml
│   │   │   │   └── META-INF/resources/index.html     # Simple UI
│   │   │   └── docker/
│   │   │       └── Dockerfile.jvm
│   │   └── test/
│   │       └── java/com/rokkon/mock/
│   │           └── engine/
│   │               ├── MockConnectorEngineTest.java
│   │               ├── MockPipeStepProcessorTest.java
│   │               └── RequestStoreTest.java
│   ├── docker-compose.yml
│   ├── build.gradle.kts
│   └── README.md
├── pipestream/                   # Future real pipestream (created later)
└── connector/                    # Connector server (can use pipestream-mock for testing)
```

## Implementation Checklist

When implementing the mock engine, ensure:

### Core Functionality
- [ ] Both gRPC services implemented with Mutiny
- [ ] Request storage with thread-safe access
- [ ] Configurable responses via REST API
- [ ] Request verification endpoints
- [ ] Health check endpoints

### Docker Support
- [ ] Dockerfile.jvm created
- [ ] docker-compose.yml with Consul
- [ ] Health checks configured
- [ ] Proper port exposure

### Testing Infrastructure
- [ ] REST API for test control
- [ ] Request counting and verification
- [ ] Reset functionality
- [ ] Scenario loading

### Documentation
- [ ] README with usage examples
- [ ] API documentation
- [ ] Integration test examples
- [ ] Migration guide for filesystem-crawler

## Common Patterns

### Request Storage Pattern
```java
@ApplicationScoped
public class RequestStore {
    private final List<ConnectorRequest> connectorRequests = new CopyOnWriteArrayList<>();
    private final List<ProcessRequest> pipelineRequests = new CopyOnWriteArrayList<>();
    private final AtomicInteger expectedCount = new AtomicInteger(-1);
    
    public void reset() {
        connectorRequests.clear();
        pipelineRequests.clear();
        expectedCount.set(-1);
    }
    
    public void reset(int expected) {
        reset();
        expectedCount.set(expected);
    }
}
```

### REST API Pattern
```java
@Path("/api/mock")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MockEngineResource {
    
    @GET
    @Path("/status")
    public MockStatus getStatus() {
        // Return overall status
    }
    
    @POST
    @Path("/reset")
    public Response reset() {
        // Reset all state
    }
    
    @POST
    @Path("/scenario/{name}")
    public Response loadScenario(@PathParam("name") String name) {
        // Load predefined test scenario
    }
}
```

## Error Handling

- Use `google.rpc.Status` for gRPC errors
- Return appropriate HTTP status codes for REST
- Log all errors with context
- Provide clear error messages for test failures

## Performance Considerations

Since this is a mock for testing:
- Prioritize clarity over performance
- Keep all requests in memory (reasonable for tests)
- Add configurable delays for realistic simulation
- Support concurrent request handling

## Next Session Instructions

When continuing development:
1. Start by reading this CLAUDE.md file
2. Check the current state of `rokkon-engine-new/pipestream-mock`
3. Review the todo list for pending tasks
4. Follow the patterns and standards defined here
5. Do NOT modify `rokkon-engine` code
6. Keep the mock engine self-contained and testable

Remember: 
- This mock engine is in `pipestream-mock` (not `pipestream`)
- It's a permanent testing fixture, not a temporary solution
- It enables testing of both connectors and the future pipestream
- The real pipestream will be developed separately in `rokkon-engine-new/pipestream`
- This separation ensures clean architecture and no bleeding of test code into production