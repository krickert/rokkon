## Development Reminders
- Read through TESTING_STRATEGY.md after every compaction /compact command happens
- DO NOT BUILD DOCKER IMAGES WITH THE DOCKER COMMAND.  Use the gradle build or else it will not work right.
- Use the application.yml instead of application.properties
- Use yml over property files.
- Use the `./dev.sh` script for local development - it handles localhost networking issues properly:
  - `./dev.sh` - Run engine in dev mode with Consul
  - `./dev.sh --with-modules` - Run engine + all modules
  - `./dev.sh --module echo --module chunker` - Run engine + specific modules
  - `./dev.sh --stop` - Stop all containers
  - The script uses `--network host` to avoid Docker networking issues
- According to the Quarkus standard, integration tests compile against the jar, and this is a normal place to put it. Quarkus does this out of the box.
- Create integration tests in the `src/integrationTest` directory without needing to modify the build configuration. Refer to `DEVELOPER_NOTES/quarkus_documentation/getting_started_testing_quarkus.adoc` for more information on testing.
- Run integration tests with the quarkusIntTest.. for example `./gradlew :engine:dynamic-grpc:quarkusIntTest`
- CRITICAL: Integration tests MUST use `@QuarkusIntegrationTest` annotation and run with `quarkusIntTest` task, NOT `integrationTest`

## BOM Structure and Usage
- **bom:base** - Contains only Quarkus BOM, parent for all other BOMs
- **bom:cli** - For CLI applications
  - NO `quarkus-grpc` to avoid server components
  - CLI apps use `compileOnly("io.quarkus:quarkus-grpc")` for code generation only
- **bom:library** - For libraries (has `quarkus-grpc` for code generation)
- **bom:module** - For modules/services 
  - Includes `quarkus-grpc`, REST, health, metrics, container image support
  - All modules automatically get Docker build capability
- **bom:server** - For servers like engine (full server stack)

## gRPC Code Generation
- **All projects generate their own protobuf code** - no pre-generated stubs
- Proto files live in `commons:protobuf` as a proto-only JAR
- Projects scan and generate code via Quarkus gRPC plugin
- Required configuration in application.yml:
```yaml
quarkus:
  generate-code:
    grpc:
      scan-for-proto: com.rokkon.pipeline:protobuf
      scan-for-imports: com.google.protobuf:protobuf-java,com.google.api.grpc:proto-google-common-protos
```

## Port Conventions
- HTTP ports: 3XXXX (e.g., 38080, 39092)
- gRPC ports: 4XXXX (e.g., 48080, 49092)
- The XXXX portion matches between HTTP and gRPC for the same service
- Examples:
  - Engine: HTTP 38082, gRPC 48082
  - Chunker: HTTP 39092, gRPC 49092
  - Parser: HTTP 39093, gRPC 49093
  - Embedder: HTTP 39094, gRPC 49094

## Module Deployment Architecture
- Modules run with Consul sidecars (service mesh pattern)
- Modules do NOT connect directly to Consul
- Registration flow:
  1. Module starts and binds to its ports
  2. register-module CLI attempts to register with Engine
  3. If registration fails, module continues running (resilient startup)
  4. Consul sidecar handles service mesh registration separately
- This allows testing modules in isolation without full infrastructure

## Pipeline Validation Architecture
- **Critical**: Pipeline configurations are HEAVILY validated before deployment
- Pipeline deployment endpoint (`POST /api/v1/clusters/{clusterName}/pipelines/{pipelineId}`) validates using 10 validators:
  - RequiredFieldsValidator - ensures all required fields present
  - ProcessorInfoValidator - validates module references and gRPC service names
  - OutputRoutingValidator - validates output routing between steps
  - StepTypeValidator - validates step types (INITIAL_PIPELINE, PIPELINE, SINK)
  - IntraPipelineLoopValidator - prevents infinite loops
  - Plus 5 other validators for naming, retry config, transport config, etc.
- Validation happens in `PipelineConfigServiceImpl.createPipeline()` before storing in Consul
- Engine's `PipelineExecutorService` looks for pipelines in cluster config, NOT pipeline definitions
- Pipeline definitions are templates; cluster pipelines are deployed instances

## Dynamic gRPC Service Discovery
- **New Module**: `engine:dynamic-grpc` provides dynamic service discovery without Stork pre-configuration
- Key Components:
  - `DynamicGrpcClientFactory` - Main factory supporting both traditional and Mutiny stubs
  - `DynamicConsulServiceDiscovery` - Direct Consul integration with random load balancing
  - `GrpcClientProvider` - Generic provider for any Mutiny stub type
- Features:
  - Dynamic service discovery from Consul without pre-configuration
  - Support for both `PipeStepProcessor` and `MutinyPipeStepProcessorStub` clients
  - Built-in channel caching with configurable TTL
  - Random load balancing across service instances
  - Graceful shutdown with channel cleanup
- Usage in Engine:
  - `GrpcTransportHandler` uses `DynamicGrpcClientFactory.getMutinyClientForService()`
  - Service names come from pipeline configuration (e.g., "echo", "test")
  - No need to pre-configure services in Stork configuration

## CRITICAL: Dynamic gRPC Testing Pattern (Abstract Getter Pattern) ✅ IMPLEMENTED

The dynamic-grpc module previously COULD NOT build standalone due to hard CDI dependency on ConsulConnectionManager from engine:consul. This was a PRODUCTION ISSUE - modules must be independent.

### ✅ SOLUTION IMPLEMENTED - Abstract Getter Pattern:

This pattern enables modules to be tested in complete isolation while still being integratable into larger systems. It's a KEY ARCHITECTURAL PATTERN for the entire Rokkon project.

#### 1. **Abstract Base Test Class** (`AbstractDynamicGrpcTestBase.java`):
```java
public abstract class AbstractDynamicGrpcTestBase {
    protected DynamicGrpcClientFactory clientFactory;
    protected ServiceDiscovery serviceDiscovery;
    
    // Abstract getter - concrete tests provide implementation
    protected abstract ServiceDiscovery getServiceDiscovery();
    
    @BeforeEach
    void setupBase() {
        // Allow concrete classes to setup dependencies first
        additionalSetup();
        
        // Get dependencies from concrete implementations
        serviceDiscovery = getServiceDiscovery();
        
        // Create factory if not already created
        if (clientFactory == null) {
            clientFactory = new DynamicGrpcClientFactory();
            clientFactory.setServiceDiscovery(serviceDiscovery);
        }
    }
    
    // Hook for concrete classes
    protected void additionalSetup() {}
    
    // Common test methods that work for both unit and integration tests
    @Test
    void testClientFactoryInitialization() {
        assertThat(clientFactory).isNotNull();
        assertThat(serviceDiscovery).isNotNull();
    }
}
```

#### 2. **Unit Tests Use Mocks** (NO CDI):
```java
class DynamicGrpcClientFactoryUnitTest extends DynamicGrpcClientFactoryTestBase {
    private MockServiceDiscovery mockServiceDiscovery;
    private DynamicGrpcClientFactory factory;
    
    @BeforeEach
    void setupTest() {
        // Create mock - no CDI needed!
        mockServiceDiscovery = new MockServiceDiscovery();
        
        // Create factory and inject mock manually
        factory = new DynamicGrpcClientFactory();
        factory.setServiceDiscovery(mockServiceDiscovery);
        
        // Add test data
        mockServiceDiscovery.addService("echo-test", "localhost", testGrpcPort);
    }
    
    @Override
    protected DynamicGrpcClientFactory getFactory() {
        return factory;
    }
}
```

#### 3. **Integration Tests Use Real Instances**:
```java
@Testcontainers
class DynamicGrpcIntegrationIT {
    @Container
    static ConsulContainer consul = new ConsulContainer(DockerImageName.parse("hashicorp/consul:latest"));
    
    // Real instances - NOT injected
    private Vertx vertx;
    private ConsulClient consulClient;
    private ServiceDiscovery serviceDiscovery;
    
    @BeforeEach
    void setup() {
        // Create real instances
        vertx = Vertx.vertx();
        
        // CRITICAL: Use random port from container!
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consul.getHost())
            .setPort(consul.getFirstMappedPort()); // NEVER use 8500!
        
        consulClient = ConsulClient.create(vertx, options);
        serviceDiscovery = new TestConsulServiceDiscovery(consulClient);
        
        // Configure factory
        clientFactory = new DynamicGrpcClientFactory();
        clientFactory.setServiceDiscovery(serviceDiscovery);
    }
}
```

#### 4. **Module Independence via Optional Injection**:
```java
@ApplicationScoped
public class DynamicConsulServiceDiscovery implements ServiceDiscovery {
    
    @Inject
    Instance<ConsulConnectionManager> connectionManagerInstance; // Optional!
    
    @Inject
    Instance<Vertx> vertxInstance; // Optional!
    
    @ConfigProperty(name = "quarkus.consul.host", defaultValue = "localhost")
    String consulHost;
    
    @ConfigProperty(name = "quarkus.consul.port", defaultValue = "8500")
    int consulPort;
    
    private ConsulClient consulClient;
    private boolean standaloneClient = false;
    
    @PostConstruct
    void init() {
        if (connectionManagerInstance.isResolvable()) {
            // Use engine's connection manager when available
            LOG.info("Using injected ConsulConnectionManager");
            ConsulConnectionManager mgr = connectionManagerInstance.get();
            mgr.getClient().ifPresent(client -> this.consulClient = client);
        } else {
            // Create standalone client for independent operation
            LOG.info("Creating standalone ConsulClient on {}:{}", consulHost, consulPort);
            createStandaloneConsulClient();
        }
    }
    
    private void createStandaloneConsulClient() {
        if (!vertxInstance.isResolvable()) {
            throw new IllegalStateException("Neither ConsulConnectionManager nor Vertx available");
        }
        
        Vertx vertx = vertxInstance.get();
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consulHost)
            .setPort(consulPort);
        
        this.consulClient = ConsulClient.create(vertx, options);
        this.standaloneClient = true;
    }
    
    @PreDestroy
    void cleanup() {
        if (standaloneClient && consulClient != null) {
            LOG.info("Closing standalone ConsulClient");
            consulClient.close();
        }
    }
}
```

#### 5. **@DefaultBean Producers for Standalone Operation**:
```java
@ApplicationScoped
public class StandaloneServiceDiscoveryProducer {
    @Inject
    DynamicConsulServiceDiscovery dynamicConsulServiceDiscovery;
    
    @Produces
    @DefaultBean  // Only used when no other ServiceDiscovery exists
    @ApplicationScoped
    public ServiceDiscovery produceServiceDiscovery() {
        LOG.info("Producing default ServiceDiscovery for standalone");
        return dynamicConsulServiceDiscovery;
    }
}
```

### 🎯 KEY BENEFITS ACHIEVED:

1. **Module Independence**: dynamic-grpc now builds, tests, and runs completely standalone
2. **Zero CDI Issues**: Unit tests use manual dependency injection - no bean conflicts
3. **Fast Tests**: Unit tests run in < 1 second without Quarkus context
4. **Flexible Integration**: Works standalone OR integrated with engine
5. **Same Test Logic**: Abstract base class ensures consistent testing
6. **Production Ready**: Optional injection pattern works in all environments

### 📊 TEST RESULTS:
- ✅ **11 Unit Tests**: All passing with mocks (< 1 second total)
- ✅ **4 Integration Tests**: All passing with real Consul/gRPC
- ✅ **Module Builds**: Standalone JAR creation successful
- ✅ **No CDI Dependencies**: Removed all @QuarkusTest from unit tests

### 🚀 NEXT STEPS FOR ENGINE:

1. **Test dynamic-grpc integration in engine** - The module is now stable and ready!
2. **Apply Abstract Getter Pattern to engine tests** - Same CDI issues exist there
3. **Document pattern in TESTING_STRATEGY.md** - This is now a core architectural pattern

### ⚠️ CRITICAL REMINDERS:

- **NEVER use default port 8500** in tests - always use `consulContainer.getFirstMappedPort()`
- **DO NOT use @QuarkusIntegrationTest** in modules that need standalone capability
- **ALWAYS use optional injection** (`Instance<T>`) for cross-module dependencies
- **Unit tests should NEVER require CDI** - use manual dependency injection

This pattern is a GAME CHANGER for the entire project architecture!

## CRITICAL: Quarkus Integration Test Limitations

### Key Findings:
1. **@QuarkusIntegrationTest does NOT support CDI injection** - Tests run against a built JAR, not within CDI context
2. **Use REST API calls instead of @Inject** - Access services through HTTP endpoints
3. **Integration tests must be run with quarkusIntTest** - Not with regular test task
4. **Test resources can inject configuration** - Use QuarkusTestResourceLifecycleManager
5. **@QuarkusIntegrationTest runs the prod profile** - Unless overridden with quarkus.test.integration-test-profile

### Integration Test Pattern:
```java
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResourceWithConfig.class)
class DynamicGrpcDiscoveryIT {
    // NO @Inject fields - won't work!
    
    @Test
    void testServiceDiscovery() {
        // Use REST API calls instead
        given()
            .when()
            .get("/api/v1/modules/dashboard")
            .then()
            .statusCode(200);
    }
}
```

### Key Takeaways:
- Integration tests verify the built application works correctly
- They test "black box" - no internal access to CDI beans
- Perfect for end-to-end testing with real services
- Use unit tests (with mocks) for testing internal logic

## CRITICAL: Engine Consul Configuration Testing Challenges

The engine:pipestream module has Consul configuration enabled, which creates complex testing scenarios:

### The Problem:
- Engine tries to fetch configuration from Consul at startup (`quarkus.consul-config.enabled=true`)
- If Consul is unavailable or missing required config keys, the application fails to start
- This happens BEFORE CDI context initialization, making it impossible to mock

### Required Config Keys in Consul:
```yaml
# In Consul KV at: config/rokkon-engine/application
rokkon:
  cluster:
    name: test-cluster
  engine:
    name: test-engine
  modules:
    whitelist:
      - echo
      - test-module
```

### Testing Solutions:

#### 1. Unit Tests - Disable Consul Config:
```java
@QuarkusTest
@TestProfile(NoConsulConfigTestProfile.class)
class EngineUnitTest {
    // Tests run without Consul
}

public class NoConsulConfigTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.consul-config.enabled", "false",
            "quarkus.consul.enabled", "false",
            "rokkon.cluster.name", "test-cluster",
            "rokkon.engine.name", "test-engine"
            // Add all required config here
        );
    }
}
```

#### 2. Integration Tests - Seed Consul Before Start:
```java
public class ConsulTestResourceWithConfig implements QuarkusTestResourceLifecycleManager {
    @Override
    public Map<String, String> start() {
        consul = new ConsulContainer("consul:latest");
        consul.start();
        
        // CRITICAL: Seed config BEFORE engine starts!
        seedConsulConfiguration(consul);
        
        return Map.of(
            "quarkus.consul.host", consul.getHost(),
            "quarkus.consul.port", String.valueOf(consul.getMappedPort(8500))
        );
    }
    
    private void seedConsulConfiguration(ConsulContainer consul) {
        ConsulClient client = new ConsulClient(consul.getHost(), consul.getMappedPort(8500));
        
        // Seed required configuration
        client.setKVValue("config/rokkon-engine/application", """
            rokkon:
              cluster:
                name: test-cluster
              engine:
                name: test-engine
            """);
    }
}
```

### Key Insights:
1. **Bootstrap Order**: Consul config is fetched BEFORE CDI beans are created
2. **Complete Seeding**: Missing even one required config key causes startup failure
3. **Config Format**: Engine expects YAML format in Consul KV store
4. **Test Profiles Critical**: Without proper profiles, tests try to connect to Consul and fail

See `ENGINE_CONSUL_CONFIG_TESTING_NOTES.md` for comprehensive details.

## CRITICAL: Quarkus CDI Testing with @InjectMock
- **NEVER use direct Mockito.mock() for CDI beans** in @QuarkusTest
- **ALWAYS use @InjectMock** for mocking CDI beans in Quarkus tests
- Quarkus has its own `io.quarkus.test.InjectMock` annotation that properly integrates with CDI
- Example:
```java
@QuarkusTest
class MyTest {
    @InjectMock
    MyService mockService;  // Quarkus creates the mock and injects it
    
    @Test
    void test() {
        when(mockService.method()).thenReturn(value);  // Use the mock normally
    }
}
```
- Direct Mockito.mock() calls will NOT work with CDI injection in Quarkus tests
- When using @InjectMock, Quarkus:
  - Creates the mock instance for you
  - Properly injects it into the CDI context
  - Replaces the real bean with the mock for that test class
  - Handles lifecycle and cleanup automatically

# important-instruction-reminders
Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.