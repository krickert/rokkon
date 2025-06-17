# Comprehensive Quarkus Testing Strategy

## Overview
Detailed testing strategy for Quarkus-based modules and engine, addressing the brittleness issues from Micronaut while ensuring comprehensive coverage and reliability.

## Testing Philosophy

### Core Principles
1. **Real Services Over Mocks**: Use actual Consul, gRPC services, not mocks
2. **Fast Feedback**: Quick test execution with reliable results
3. **Comprehensive Coverage**: Unit, integration, and end-to-end testing
4. **Operational Reality**: Test scenarios that match production usage
5. **Developer Productivity**: Tests that are easy to write, run, and debug

### Testing Levels
- **Unit Tests**: Fast, isolated component testing
- **Integration Tests**: Component interaction testing with real services
- **Module Integration Tests**: Engine-to-module communication testing
- **End-to-End Tests**: Complete pipeline testing

## Module Testing Pattern

### Abstract Test Base Pattern

Each module implements a common testing pattern using an abstract base class:

```java
public abstract class TikaParserServiceTestBase {
    
    protected abstract PipeStepProcessor getTikaParserService();
    
    @Test
    void testProcessDataWithValidDocument() {
        // Create test document
        Document testDoc = Document.newBuilder()
            .setId("test-doc-1")
            .setContent(ByteString.copyFromUtf8("Sample document content"))
            .setMimeType("text/plain")
            .build();
        
        ProcessRequest request = ProcessRequest.newBuilder()
            .setDocument(testDoc)
            .build();
        
        // Execute service call
        ProcessResponse response = getTikaParserService()
            .processData(request)
            .await()
            .atMost(Duration.ofSeconds(10));
        
        // Verify response
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getProcessorLogsList())
            .anyMatch(log -> log.contains("successfully processed"));
        assertThat(response.hasOutputDoc()).isTrue();
    }
    
    @Test
    void testProcessDataWithoutDocument() {
        ProcessRequest request = ProcessRequest.newBuilder().build();
        
        ProcessResponse response = getTikaParserService()
            .processData(request)
            .await()
            .atMost(Duration.ofSeconds(10));
        
        // Should handle gracefully
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getProcessorLogsList())
            .anyMatch(log -> log.contains("no document"));
    }
    
    @Test
    void testGetServiceRegistration() {
        Empty request = Empty.newBuilder().build();
        
        ServiceRegistrationData response = getTikaParserService()
            .getServiceRegistration(request)
            .await()
            .atMost(Duration.ofSeconds(5));
        
        assertThat(response.getModuleName()).isEqualTo("tika-parser");
        assertThat(response.hasModuleConfig()).isTrue();
    }
    
    @Test
    void testErrorHandling() {
        // Test with malformed/corrupted document
        Document corruptDoc = Document.newBuilder()
            .setId("corrupt-doc")
            .setContent(ByteString.copyFromUtf8("corrupted data"))
            .setMimeType("application/pdf") // Mismatch with actual content
            .build();
        
        ProcessRequest request = ProcessRequest.newBuilder()
            .setDocument(corruptDoc)
            .build();
        
        ProcessResponse response = getTikaParserService()
            .processData(request)
            .await()
            .atMost(Duration.ofSeconds(10));
        
        // Should handle error gracefully
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getProcessorLogsList())
            .anyMatch(log -> log.contains("error") || log.contains("failed"));
    }
}
```

### Unit Tests with @QuarkusTest
Tests that run in the same JVM process as the application:

```java
@QuarkusTest
class TikaParserServiceTest extends TikaParserServiceTestBase {
    
    @GrpcClient
    PipeStepProcessor pipeStepProcessor;
    
    @Override
    protected PipeStepProcessor getTikaParserService() {
        return pipeStepProcessor;
    }
    
    @Test
    void testQuarkusSpecificFeatures() {
        // Test Quarkus-specific features like:
        // - CDI injection
        // - Configuration injection
        // - Health checks
        // - Metrics
    }
}
```

**Key Features:**
- **Fast Startup**: Quarkus optimized for test speed
- **gRPC Injection**: `@GrpcClient` provides type-safe client injection
- **Dev Services**: Automatic infrastructure containers
- **Hot Reload**: Changes reflected without restart (in dev mode)

### Integration Tests with @QuarkusIntegrationTest
Tests that run against the built application in production mode:

```java
@QuarkusIntegrationTest
public class TikaParserServiceIT extends TikaParserServiceTestBase {
    
    private ManagedChannel channel;
    private PipeStepProcessor pipeStepProcessor;
    
    @BeforeEach
    void setup() {
        // Get the actual port the application is running on
        int port = Integer.parseInt(System.getProperty("quarkus.grpc.server.port", "9090"));
        
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
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
            }
        }
    }
    
    @Override
    protected PipeStepProcessor getTikaParserService() {
        return pipeStepProcessor;
    }
    
    @Test
    void testProductionModeFeatures() {
        // Test features specific to production mode:
        // - Native compilation compatibility (future)
        // - Production configuration
        // - Resource usage
        // - Performance characteristics
    }
}
```

**Key Features:**
- **Production Mode**: Tests the actual built artifact
- **External Client**: Uses real gRPC client connections
- **Real Networking**: Tests actual network communication
- **Resource Testing**: Validates production resource usage

### Test Configuration

#### Application Configuration for Tests
```yaml
# src/test/resources/application.yml
quarkus:
  application:
    name: tika-parser-test
  grpc:
    server:
      port: 0  # Random port for parallel testing
      host: 0.0.0.0
      enable-reflection-service: true
  log:
    level: INFO
    category:
      "com.rokkon":
        level: DEBUG
  
# Test-specific configuration
"%test":
  quarkus:
    devservices:
      enabled: true
    consul-config:
      devservices:
        enabled: true
        port: 0  # Random port
```

#### Test Profiles for Different Scenarios
```java
public class ModuleIntegrationTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "module.test-mode", "true",
            "consul.test-data-setup", "true",
            "quarkus.log.category.\"com.rokkon\".level", "DEBUG"
        );
    }
    
    @Override
    public String getConfigProfile() {
        return "module-integration";
    }
}
```

## Engine Testing Strategy

### Configuration Management Testing
Test the configuration system with real Consul integration:

```java
@QuarkusTest
@TestProfile(ConsulIntegrationTestProfile.class)
class PipelineConfigServiceTest {
    
    @Inject
    PipelineConfigService configService;
    
    @Test
    void testConfigurationRoundTrip() {
        // Create test configuration
        PipelineConfig testConfig = createTestPipelineConfig();
        
        // Save configuration
        configService.savePipelineConfig("test-cluster", testConfig)
            .await()
            .atMost(Duration.ofSeconds(10));
        
        // Load configuration
        PipelineConfig loadedConfig = configService
            .loadPipelineConfig("test-cluster", testConfig.pipelineId())
            .await()
            .atMost(Duration.ofSeconds(10));
        
        // Verify roundtrip
        assertThat(loadedConfig).isEqualTo(testConfig);
    }
    
    @Test
    void testConfigurationValidation() {
        // Create invalid configuration
        PipelineConfig invalidConfig = createInvalidPipelineConfig();
        
        // Attempt to save should fail with validation errors
        assertThatThrownBy(() -> 
            configService.savePipelineConfig("test-cluster", invalidConfig)
                .await()
                .atMost(Duration.ofSeconds(10))
        ).isInstanceOf(ValidationException.class);
    }
    
    @Test
    void testDynamicConfigurationUpdate() {
        // Test configuration change notification system
        // Verify CDI events are fired
        // Test configuration watching
    }
}
```

### Module Registration Testing
Test the module registration and discovery system:

```java
@QuarkusTest
@TestProfile(ModuleRegistrationTestProfile.class)
class ModuleRegistrationServiceTest {
    
    @Inject
    ModuleRegistrationService registrationService;
    
    @Container
    static GenericContainer<?> tikaParserModule = new GenericContainer<>("rokkon/tika-parser:test")
        .withExposedPorts(9090)
        .waitingFor(Wait.forHealthcheck());
    
    @Test
    void testModuleRegistration() {
        String moduleHost = tikaParserModule.getHost();
        Integer modulePort = tikaParserModule.getMappedPort(9090);
        
        ModuleRegistrationRequest request = ModuleRegistrationRequest.builder()
            .moduleName("tika-parser")
            .host(moduleHost)
            .port(modulePort)
            .build();
        
        ModuleRegistrationResponse response = registrationService
            .registerModule(request)
            .await()
            .atMost(Duration.ofSeconds(30));
        
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getValidationResults()).isEmpty();
    }
    
    @Test
    void testModuleHealthMonitoring() {
        // Test ongoing health monitoring
        // Test failure detection
        // Test automatic deregistration
    }
}
```

### Pipeline Orchestration Testing
Test the core pipeline execution logic:

```java
@QuarkusTest
@TestProfile(PipelineOrchestrationTestProfile.class)
class PipelineOrchestratorTest {
    
    @Inject
    PipelineOrchestrator orchestrator;
    
    @Container
    static GenericContainer<?> tikaParser = createTikaParserContainer();
    
    @Container
    static GenericContainer<?> chunker = createChunkerContainer();
    
    @Container
    static GenericContainer<?> embedder = createEmbedderContainer();
    
    @Test
    void testCompletePipelineExecution() {
        // Create test document
        Document testDoc = createTestDocument();
        
        ProcessRequest request = ProcessRequest.newBuilder()
            .setDocument(testDoc)
            .setPipelineName("test-pipeline")
            .build();
        
        // Execute pipeline
        ProcessResponse response = orchestrator
            .processDocument(request)
            .await()
            .atMost(Duration.ofMinutes(2));
        
        // Verify complete processing
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        
        // Verify processing steps were executed
        assertThat(response.getProcessorLogsList())
            .anyMatch(log -> log.contains("tika-parser"))
            .anyMatch(log -> log.contains("chunker"))
            .anyMatch(log -> log.contains("embedder"));
    }
    
    @Test
    void testPipelineErrorHandling() {
        // Test error scenarios:
        // - Module unavailable
        // - Processing timeout
        // - Invalid configuration
        // - Network failures
    }
    
    @Test
    void testConfigurationDrivenRouting() {
        // Test different pipeline configurations
        // Verify routing follows configuration
        // Test dynamic configuration changes
    }
}
```

## Test Infrastructure and Utilities

### Test Data Management
```java
@ApplicationScoped
public class TestDataManager {
    
    public void setupTestPipelineConfiguration() {
        // Create consistent test pipeline configurations
        // Set up test modules in Consul
        // Prepare test documents
    }
    
    public void cleanupTestData() {
        // Clean up test configurations
        // Remove test service registrations
        // Clean up test documents
    }
    
    public Document createTestDocument(String type) {
        return switch (type) {
            case "pdf" -> createPdfTestDocument();
            case "text" -> createTextTestDocument();
            case "docx" -> createDocxTestDocument();
            default -> createDefaultTestDocument();
        };
    }
}
```

### Test Containers Setup
```java
public class TestContainers {
    
    public static GenericContainer<?> createTikaParserContainer() {
        return new GenericContainer<>("rokkon/tika-parser:test")
            .withExposedPorts(9090)
            .withEnv("QUARKUS_PROFILE", "test")
            .waitingFor(Wait.forHealthcheck())
            .withStartupTimeout(Duration.ofMinutes(2));
    }
    
    public static GenericContainer<?> createConsulContainer() {
        return new ConsulContainer(DockerImageName.parse("consul:1.15.3"))
            .withConsulCommand("consul agent -dev -server -bootstrap -ui -client 0.0.0.0")
            .waitingFor(Wait.forHttp("/v1/status/leader").forStatusCode(200));
    }
}
```

### Quarkus Dev Services Configuration
```properties
# Enable dev services for testing
quarkus.devservices.enabled=true

# Consul dev service
quarkus.consul-config.devservices.enabled=true
quarkus.consul-config.devservices.port=0

# Kafka dev service (if needed later)
%test.kafka.devservices.enabled=true
%test.kafka.devservices.port=0

# Test-specific configurations
%test.quarkus.grpc.server.port=0
%test.quarkus.log.category."com.rokkon".level=DEBUG
```

## Performance and Load Testing

### Module Performance Testing
```java
@QuarkusTest
@TestProfile(PerformanceTestProfile.class)
class TikaParserPerformanceTest {
    
    @GrpcClient
    PipeStepProcessor pipeStepProcessor;
    
    @Test
    void testProcessingThroughput() {
        // Test processing rate with multiple documents
        // Measure response times
        // Verify resource usage stays within bounds
        
        List<CompletableFuture<ProcessResponse>> futures = IntStream.range(0, 100)
            .mapToObj(i -> createTestDocument())
            .map(doc -> ProcessRequest.newBuilder().setDocument(doc).build())
            .map(request -> pipeStepProcessor.processData(request).convert().toCompletableFuture())
            .toList();
        
        CompletableFuture<List<ProcessResponse>> allResponses = 
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                    .map(CompletableFuture::join)
                    .toList());
        
        List<ProcessResponse> responses = allResponses.join();
        
        // Verify all successful
        assertThat(responses).allMatch(ProcessResponse::getSuccess);
        
        // Verify performance characteristics
        // - All responses within reasonable time
        // - No resource leaks
        // - Consistent performance
    }
}
```

### Memory and Resource Testing
```java
@QuarkusTest
@TestProfile(ResourceTestProfile.class)
class ResourceUsageTest {
    
    @Test
    void testMemoryUsage() {
        // Monitor memory usage during processing
        // Verify no memory leaks
        // Test garbage collection behavior
    }
    
    @Test
    void testConnectionPooling() {
        // Test gRPC connection reuse
        // Verify connection pool limits
        // Test connection cleanup
    }
}
```

## Test Execution Strategy

### Local Development
```bash
# Run unit tests only
./gradlew test

# Run integration tests
./gradlew quarkusIntTest

# Run all tests
./gradlew check

# Run specific module tests
./gradlew :modules:tika-parser:test
./gradlew :modules:tika-parser:quarkusIntTest
```

### CI/CD Pipeline
```yaml
# GitHub Actions example
name: Test Pipeline

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - run: ./gradlew test

  integration-tests:
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - run: ./gradlew quarkusIntTest

  e2e-tests:
    runs-on: ubuntu-latest
    needs: integration-tests
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - run: ./gradlew e2eTest
```

## Success Metrics

### Test Reliability
- **Flaky Test Rate**: < 1% of test executions
- **Test Execution Time**: Unit tests < 2 minutes, Integration tests < 10 minutes
- **Setup Time**: Test infrastructure startup < 30 seconds

### Developer Experience
- **Clear Failures**: 90% of failures immediately actionable
- **Debug Time**: Issue identification < 5 minutes
- **Test Coverage**: > 80% line coverage, > 90% branch coverage for critical paths

### Operational Confidence
- **Production Similarity**: Test environment matches production behavior
- **Failure Detection**: Critical failures caught by test suite
- **Performance Validation**: Performance characteristics validated

This comprehensive testing strategy ensures reliable, maintainable, and effective testing for the Quarkus-based Rokkon Engine while addressing the brittleness issues experienced with Micronaut.