# Rokkon Engine Development Guide

## Module Registration Architecture

### Overview
Modules in Rokkon Engine are "dumb" gRPC services that don't know about Consul or the engine. Each module container includes a CLI tool that handles registration with the engine on the module's behalf.

### Registration Flow
1. **Module container starts up**
   - Module exposes gRPC service implementing `PipeStepProcessor` interface
   - Implements three key RPCs:
     - `ProcessData()` - Main processing logic
     - `GetServiceRegistration()` - Returns module name and optional schema
     - `RegistrationCheck()` - Validation endpoint (similar to ProcessData but for testing)
   - Module does NOT connect to Consul or engine directly

2. **CLI tool runs inside the module container**
   - Called as part of container startup or via CI/CD after deployment
   - Connects to localhost to reach the module's gRPC service
   - Calls `GetServiceRegistration()` to get module's basic info
   - will also act as a full cli for developers

3. **CLI registers module with engine**
   - Connects to engine's `ModuleRegistrationService` (implemented in engine-registration, runs in rokkon-engine)
   - Provides full registration details including:
     - Module name and implementation ID
     - Host/port information
     - Health check configuration (gRPC only) TODO: delete other protocols but keep the placeholder
   - Engine validates the module:
     - Checks against whitelist (echo, chunker, parser, embedder, opensearch-sink)
     - Performs health check
     - Calls `RegistrationCheck()` to verify module functionality

4. **Engine writes to Consul**
   - Only the engine-consul component can write to Consul
   - Registers module as a gRPC service
   - Configures gRPC health checks
   - Stores module metadata for pipeline configuration
   - ALL calls to write to consul are validated and validate the entire config validation. 
   - There is an entire validation framework under engine-validators that help with this

5. **Consul monitors health**
   - Performs regular gRPC health checks using standard protocol
   - Marks service as healthy/unhealthy
   - Engine queries Consul for healthy services during pipeline execution

### Key Design Principles
- **Modules are stateless**: No Consul dependencies, just gRPC services
- **In-container registration**: CLI tool runs inside module container
- **Engine owns Consul writes**: Only engine-consul project writes to Consul
- **Health monitoring**: Consul handles gRPC health checks
- **Validation before registration**: RegistrationCheck ensures module works

### Module Validation with RegistrationCheck

The `RegistrationCheck` RPC is a new addition to the `PipeStepProcessor` interface:

```proto
service PipeStepProcessor {
  // Main processing logic
  rpc ProcessData(ProcessRequest) returns (ProcessResponse);
  
  // Returns basic module info for CLI
  rpc GetServiceRegistration(google.protobuf.Empty) returns (ServiceRegistrationData);
  
  // Validation endpoint - tests module functionality
  rpc RegistrationCheck(ProcessRequest) returns (ProcessResponse);
}
```

The `RegistrationCheck`:
- Receives a test `ProcessRequest` from the engine
- Should execute a lightweight version of the module's logic
- Returns success/failure to indicate if the module is functioning
- Helps prevent broken modules from being registered
- forces module author to think about how a dummy document should not end up on a prod system

### CLI Tool Architecture

The CLI tool is a simple utility that:
- Ships with each module container (language-agnostic)
- Runs after module startup (via entrypoint script or CI/CD webhook)
- Connects to the module on localhost
- Bridges the gap between the module and engine

Example container entrypoint:
```bash
#!/bin/bash
# Start the module
./my-module-service &

# Wait for module to be ready
sleep 5

# Register with engine
rokkon-cli register \
  --module-port=9090 \
  --engine-host=$ENGINE_HOST \
  --engine-port=$ENGINE_PORT
```

## Configuration Management - Critical Design Decisions

### Consul Access Pattern (IMPORTANT)
To prevent developers from bypassing validation and directly modifying the configuration graph:

1. **engine-consul (WRITER)** - Uses HTTP client to access Consul API
   - Implements validation before writes
   - Handles module registration from CLI tool
   - All configuration changes go through validation
   
2. **engine modules (READER ONLY)** - Uses quarkus-config-consul extension
   - Read-only access via configuration properties
   - Cannot write even if developer wants to bypass validation
   - No KV client library available

### CAS Implementation with Pure Quarkus
```java
@RegisterRestClient(configKey = "consul-api")
@Path("/v1/kv")
public interface ConsulKVClient {
    @GET
    @Path("/{key}")
    Response getValueWithMetadata(@PathParam("key") String key);
    
    @PUT
    @Path("/{key}")
    Response putValueCAS(
        @PathParam("key") String key,
        @QueryParam("cas") Long modifyIndex,
        String value
    );
}
```

### Retry Pattern with SmallRye Fault Tolerance
```java
@Retry(
    maxRetries = 3,
    delay = 100,
    jitter = 50,
    retryOn = CASConflictException.class
)
public void updateConfig(String key, Function<PipelineGraphConfig, PipelineGraphConfig> updater) {
    // CAS logic with automatic retry
}
```

**Required extension:** `quarkus ext add smallrye-fault-tolerance`

This approach ensures:
- No temptation to bypass validation
- Clear separation of concerns
- Audit trail for all modifications
- Atomic updates with proper conflict resolution

## Module Development Procedure

### Overview
This guide documents the successful procedure for creating Quarkus gRPC modules for the Rokkon Engine. This procedure was validated with the echo module migration and ensures both unit tests (@QuarkusTest) and integration tests (@QuarkusIntegrationTest) work properly.

### Key Principles
1. **One Source of Truth**: Proto definitions are maintained in the proto-definitions project and extracted at build time
2. **No Heroic Actions**: Follow the established patterns exactly, avoid over-engineering
3. **Methodical Approach**: Test each step thoroughly before proceeding
4. **Reference Implementation**: Always refer to working examples in reference-implementations/

### Prerequisites
- Java 21
- Quarkus 3.23.3
- Gradle 8.13
- proto-definitions project published to Maven local

### Step-by-Step Module Creation

#### 1. Create Module Structure
```bash
# Create module directory
mkdir -p modules/[module-name]
cd modules/[module-name]

# Initialize Quarkus project with CLI
quarkus create app com.rokkon.pipeline:[module-name] \
  --java=21 \
  --gradle-kotlin-dsl \
  --extension=grpc,config-yaml,container-image-docker
```

#### 2. Configure build.gradle.kts
Key requirements for the build file:

```kotlin
plugins {
    java
    alias(libs.plugins.quarkus)
    `maven-publish`
}

dependencies {
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation(enforcedPlatform(libs.quarkus.bom))  // Use enforcedPlatform, not platform
    implementation(libs.quarkus.grpc)
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-arc")
    
    // Proto definitions from shared project
    implementation("com.rokkon.pipeline:proto-definitions:1.0.0-SNAPSHOT")
    
    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.assertj)
    testImplementation("io.rest-assured:rest-assured")
}

// Configure Quarkus to use Mutiny for gRPC code generation
quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}

// CRITICAL: Extract proto files from jar for local stub generation
val extractProtos = tasks.register<Copy>("extractProtos") {
    from(zipTree(configurations.runtimeClasspath.get().filter { it.name.contains("proto-definitions") }.singleFile))
    include("**/*.proto")
    into("src/main/proto")
    includeEmptyDirs = false
}

tasks.named("quarkusGenerateCode") {
    dependsOn(extractProtos)
}

// Exclude integration tests from regular test task (like reference implementation)
tasks.test {
    exclude("**/*IT.class")
}
```

#### 3. Configure application.yml
```yaml
quarkus:
  application:
    name: [module-name]
  grpc:
    server:
      port: 9090
      host: 0.0.0.0
      enable-reflection-service: true
  log:
    level: INFO
    category:
      "com.rokkon":
        level: DEBUG
  container-image:
    build: false
    push: false
    registry: "registry.rokkon.com:8443"
    image: "rokkon/[module-name]:${quarkus.application.version}"
    dockerfile-jvm-path: src/main/docker/Dockerfile.jvm
    group: rokkon

# Test profile configuration
"%test":
  quarkus:
    grpc:
      server:
        port: 0  # Use random port for tests
```

#### 4. Implement Service
Create service implementation in `src/main/java/com/rokkon/[module]/[Module]ServiceImpl.java`:

```java
@GrpcService
@Singleton
public class [Module]ServiceImpl implements PipeStepProcessor {
    
    private static final Logger LOG = Logger.getLogger([Module]ServiceImpl.class);
    
    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        LOG.debugf("[Module] service received document: %s", 
                 request.hasDocument() ? request.getDocument().getId() : "no document");
        
        ProcessResponse.Builder responseBuilder = ProcessResponse.newBuilder()
                .setSuccess(true)
                .addProcessorLogs("[Module] service successfully processed document");
        
        if (request.hasDocument()) {
            // Process the document according to module logic
            responseBuilder.setOutputDoc(request.getDocument());
        }
        
        LOG.debugf("[Module] service returning success: %s", responseBuilder.getSuccess());
        
        return Uni.createFrom().item(responseBuilder.build());
    }

    @Override
    public Uni<ServiceRegistrationData> getServiceRegistration(Empty request) {
        LOG.debug("[Module] service registration requested");
        
        ServiceRegistrationData response = ServiceRegistrationData.newBuilder()
                .setModuleName("[module-name]")
                .build();
        
        return Uni.createFrom().item(response);
    }
}
```

#### 5. Create Test Structure
Create abstract test base in `src/test/java/com/rokkon/[module]/[Module]ServiceTestBase.java`:

```java
public abstract class [Module]ServiceTestBase {
    
    protected abstract PipeStepProcessor get[Module]Service();
    
    @Test
    void testProcessData() {
        // Create test document and request
        // Verify response using UniAssertSubscriber
        // Check processor logs contain "successfully processed"
    }
    
    @Test
    void testProcessDataWithoutDocument() {
        // Test edge case without document
    }
    
    @Test 
    void testGetServiceRegistration() {
        // Test service registration
    }
}
```

Create unit test in `src/test/java/com/rokkon/[module]/[Module]ServiceTest.java`:

```java
@QuarkusTest
class [Module]ServiceTest extends [Module]ServiceTestBase {

    @GrpcClient
    PipeStepProcessor pipeStepProcessor;

    @Override
    protected PipeStepProcessor get[Module]Service() {
        return pipeStepProcessor;
    }
}
```

Create integration test in `src/integrationTest/java/com/rokkon/[module]/[Module]ServiceIT.java`:

```java
@QuarkusIntegrationTest
public class [Module]ServiceIT extends [Module]ServiceTestBase {

    private ManagedChannel channel;
    private PipeStepProcessor pipeStepProcessor;

    @BeforeEach
    void setup() {
        int port = 9090;
        channel = ManagedChannelBuilder
                .forAddress("localhost", port)
                .usePlaintext()
                .build();
        pipeStepProcessor = new PipeStepProcessorClient("pipeStepProcessor", channel, (name, stub) -> stub);
    }

    @AfterEach
    void cleanup() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Override
    protected PipeStepProcessor get[Module]Service() {
        return pipeStepProcessor;
    }
}
```

#### 6. Build and Test

```bash
# Extract proto files and build
./gradlew clean extractProtos build

# Run unit tests (excludes integration tests)
./gradlew test

# Run integration tests separately  
./gradlew quarkusIntTest

# Run all tests
./gradlew clean build quarkusIntTest
```

### Expected Results
- Unit tests: 100% pass rate in dev mode with gRPC server working
- Integration tests: 100% pass rate in prod mode with gRPC server working
- Proto stubs generated locally from extracted jar
- No "heroic actions" or complex workarounds needed

### Troubleshooting

#### Common Issues:
1. **gRPC injection errors**: Ensure proto files are extracted to `src/main/proto`
2. **Integration tests fail**: Check that gRPC server features are included in prod build
3. **Proto not found**: Verify proto-definitions jar is published to Maven local
4. **Test failures**: Ensure processor logs match test expectations ("successfully processed")

#### Key Success Indicators:
- Unit test logs show: "Installed features: [cdi, config-yaml, grpc-client, grpc-server, ...]"
- Integration test logs show: "Started gRPC server on 0.0.0.0:9090"
- Both test modes show: "Using legacy gRPC support" (this is normal)

### Module Pattern Summary
1. Extract proto files from jar at build time (one source of truth)
2. Use @GrpcService + @Singleton for service implementation
3. Use @GrpcClient injection for unit tests
4. Use external gRPC client for integration tests
5. Follow exact reference implementation patterns
6. Test both unit and integration modes

This procedure ensures consistent, working modules that integrate properly with the Rokkon Engine architecture.

## Container Health Check Testing Knowledge

### Key Findings from Container Testing

When testing Quarkus modules running in Docker containers:

1. **Port Configuration**:
   - Internal gRPC port: 9090 (what the container exposes internally)
   - Internal HTTP port: 8080 (for health endpoints)
   - External ports: Random mapped ports assigned by Docker

2. **Consul Registration Pattern**:
   ```json
   {
     "Name": "module-name",
     "Address": "<container-hostname>",
     "Port": 9090,
     "Check": {
       "GRPC": "<container-hostname>:9090",
       "GRPCUseTLS": false,
       "Interval": "10s"
     }
   }
   ```
   - Always use INTERNAL ports for Consul registration
   - Consul will connect to the container using Docker networking

3. **Testing from Outside Container**:
   - Use mapped external ports (e.g., localhost:32933)
   - These are different from internal ports
   - Obtained via `container.getMappedPort(INTERNAL_PORT)`

4. **Quarkus Container Testing Pattern**:
   ```java
   @QuarkusTestResource(TestModuleContainerResource.class)
   class ContainerTest {
       @ConfigProperty(name = "test.module.container.grpc.port")
       int externalGrpcPort;
   }
   ```

5. **Environment Variables for Consistent Ports**:
   - `QUARKUS_HTTP_PORT=8080`
   - `QUARKUS_GRPC_SERVER_PORT=9090`

6. **Health Check Behavior**:
   - Overall service health: Returns SERVING
   - Specific service health: Returns UNKNOWN (acceptable for Consul)
   - Both HTTP (`/q/health`) and gRPC health checks work

### Critical Insight
When registering a containerized module with Consul, the health check URL must use the container's internal port, not the external mapped port. This is because Consul will access the container from within the Docker network where internal ports are used.

## Critical Design Decisions

### Kafka Transport Configuration

#### Topic Naming Convention
- **Pattern**: `{pipeline-name}.{step-name}.input`
- **DLQ Pattern**: Always `{topic}.dlq` (not configurable)
- **Consumer Groups**: `{pipeline-name}.consumer-group`
- **Validation**: No dots in custom topic names (dots are delimiters)

#### Partitioning Strategy
- **Default Key**: Always `pipedocId` (NOT `streamId`)
- **Rationale**: Ensures CRUD operations maintain order
- **Compaction**: Enabled on topics to retain only most recent ID

#### Producer Configuration
- **Compression**: Default `snappy` (prefer `snappy` or `lz4`)
- **Batch Size**: Default 16KB
- **Linger MS**: Default 10ms
- **Acks**: App-controlled at engine level

#### Producer Instance ID
- **Format**: `{hostname}-{consul-lease-id}-{uuid}`
- **Tied to**: Consul lease for debugging/monitoring
- **Dynamic**: Generated at runtime

### gRPC Transport Configuration

#### Service Discovery
- **100% through Consul**: This is why we use Consul!
- **Host/Port**: Resolved dynamically via service registry
- **TLS**: Configured at global level, not per-transport
- **Load Balancing**: Handled by Consul

### Entity Operations
- **Location**: PipeStream in protobuf (NOT Document or ProcessRequest)
- **Operations**: CREATE, UPDATE, DELETE
- **Rationale**: Operations are stream-level, not document-level

### Schema Registry
- **Current**: JSON Schema V7 validation
- **Future**: Interface for Glue/Apicurio
- **Per-Module**: Schema is global to the module

### Consul Access Pattern
- **Writer**: config-orchestrator uses REST client (with CAS operations)
- **Reader**: Engine uses config-consul extension (read-only)
- **Critical**: Engine should NEVER write to Consul configuration