# YAPPY Test Containers and Micronaut Test Resources Guide

## Summary

YAPPY uses **Micronaut Test Resources** with real containerized services for all integration testing. Tests NEVER use localhost hardcoded references and NEVER mock core services. All infrastructure (Consul, Kafka, OpenSearch, etc.) runs in Docker containers managed by the Micronaut Test Resources framework.

**Key Principle: 100% Real Integration Tests** - No mocks for infrastructure, no fake implementations, no hardcoded localhost references.

## Quick Reference

### ✅ Correct Test Setup
```java
@MicronautTest(environments = {"test"})
class MyIntegrationTest {
    // These @Property annotations trigger test resources to start containers
    @Property(name = "consul.client.host")
    String consulHost;
    
    @Property(name = "consul.client.port") 
    int consulPort;
    
    @Property(name = "kafka.bootstrap.servers")
    String kafkaBootstrapServers;
    
    @Test
    void testWithRealInfrastructure() {
        // Test uses real Consul and Kafka containers
        // Properties are injected with actual container host/port
    }
}
```

### ❌ Never Do This
```java
// WRONG - hardcoded localhost references
@Property(name = "consul.client.host", value = "localhost")
@Property(name = "consul.client.port", value = "8500")

// WRONG - mocking core services being tested  
@MockBean(PipelineKafkaTopicService.class)
PipelineKafkaTopicService mockTopicService() {
    return mock(PipelineKafkaTopicService.class);
}
```

### Test Resources Configuration
```properties
# test-resources.properties - Enable specific providers
hashicorp-consul.enabled=true
apache-kafka.enabled=true
apicurio.enabled=true
moto.enabled=true
opensearch3.enabled=true

# Module containers
yappy-chunker.enabled=true
yappy-tika.enabled=true
yappy-embedder.enabled=true
```

## How Micronaut Test Resources Work

### 1. Property Request Triggers Container Startup
When a test includes `@Property(name = "consul.client.host")`, Micronaut Test Resources:

1. **Property Resolution**: Test Resources service receives request for `consul.client.host`
2. **Provider Lookup**: Finds `ConsulTestResourceProvider` that can resolve consul properties
3. **Container Startup**: Starts `consul:1.17` container with random port
4. **Property Injection**: Returns actual container host/port to test
5. **Caching**: Container shared across tests for performance

### 2. Test Resource Providers
Each provider implements `TestResourcesResolver`:

```java
public class ConsulTestResourceProvider implements TestResourcesResolver {
    @Override
    public String getName() {
        return "hashicorp-consul"; // Used in test-resources.properties
    }
    
    @Override
    public List<String> getResolvableProperties() {
        return List.of(
            "consul.client.host",
            "consul.client.port"
        );
    }
    
    @Override
    public Optional<String> resolve(String propertyName, PropertyContext context) {
        if (!container.isRunning()) {
            container.start(); // Start Consul container
        }
        
        if ("consul.client.host".equals(propertyName)) {
            return Optional.of(container.getHost());
        }
        if ("consul.client.port".equals(propertyName)) {
            return Optional.of(String.valueOf(container.getMappedPort(8500)));
        }
        return Optional.empty();
    }
}
```

### 3. Client-Server Architecture
Micronaut Test Resources runs as a separate service:
- **Test Resources Server**: Manages containers, resolves properties
- **Test Client**: Requests properties, receives actual values
- **Shared State**: Containers reused across test runs

### 4. Container Lifecycle
```
Test Startup → Property Request → Provider Check → Container Start → Property Return
     ↓
Test Execution with real container host/port
     ↓  
Test Completion → Container Kept Running (shared)
     ↓
Next Test → Same Container Reused
```

## YAPPY Test Resource Providers

### Infrastructure Providers
- **`ConsulTestResourceProvider`**: Consul service discovery and KV store
- **`KafkaTestResourceProvider`**: Apache Kafka messaging
- **`ApicurioTestResourceProvider`**: Schema registry
- **`MotoTestResourceProvider`**: AWS services (S3, Glue, etc.)
- **`OpenSearchTestResourceProvider`**: Search and analytics

### Module Providers  
- **`ChunkerTestResourceProvider`**: Text chunking module
- **`TikaTestResourceProvider`**: Document parsing module
- **`EmbedderTestResourceProvider`**: Embeddings generation module
- **`EchoTestResourceProvider`**: Test echo module

## Configuration Patterns

### Global Configuration
```properties
# gradle.properties - Shared across all modules
micronaut.test.resources.enabled=true
micronaut.test.resources.shared=true
micronaut.test.resources.inference.enabled=true
```

### Module-Specific Configuration
```yaml
# application-test.yml
micronaut:
  test-resources:
    enabled: true
    shared-server:
      enabled: true

# Kafka configuration with test resource properties
kafka:
  enabled: true
  topics:
    config:
      partitions: 5
      replication-factor: 1
      max-message-bytes: 8388608  # 8MB
      compact-enabled: true

# Application configuration
app:
  config:
    cluster-name: test-cluster  # Consistent across tests
```

## Common Anti-Patterns and Solutions

### ❌ Problem: Hardcoded Localhost References
```java
// WRONG - bypasses test resources
@Property(name = "consul.client.host", value = "localhost")
@Property(name = "consul.client.port", value = "8500")
```

**Why this breaks:**
- Assumes local Consul is running on port 8500
- No test isolation - tests interfere with each other
- Doesn't work in CI environments
- Violates containerized testing principle

### ✅ Solution: Dynamic Property Resolution
```java
// CORRECT - triggers test resource startup
@Property(name = "consul.client.host")
String consulHost;

@Property(name = "consul.client.port") 
int consulPort;
```

### ❌ Problem: Mock Abuse for Core Services
```java
// WRONG - mocking the service being tested
@MockBean(PipelineKafkaTopicService.class)
PipelineKafkaTopicService mockTopicService() {
    return mock(PipelineKafkaTopicService.class);
}

@Test 
void testTopicCreation() {
    // Fake test - verifies mock interactions, not real behavior
    verify(mockTopicService).createTopic(any(), any());
}
```

**Why this is problematic:**
- Creates "fake passing tests" that don't test real behavior
- Masks integration issues that only surface in production
- Leads to false confidence in test coverage

### ✅ Solution: Real Integration Testing
```java
// CORRECT - test real service with real Kafka
@Inject
PipelineKafkaTopicService topicService; // Real implementation

@Property(name = "kafka.bootstrap.servers")
String kafkaBootstrapServers; // Triggers Kafka container

@Test
void testTopicCreation() {
    // Real test - creates actual Kafka topics
    topicService.createAllTopics("test-pipeline", "test-step");
    
    // Verify real topics exist in real Kafka
    List<String> topics = topicService.listTopicsForStep("test-pipeline", "test-step");
    assertTrue(topics.contains("pipeline.test-pipeline.step.test-step.input"));
}
```

### ❌ Problem: Incorrect Test Resource Provider Names
```properties
# WRONG - using incorrect provider names
testcontainers.enabled=true
testcontainers.consul=true
```

**Why this doesn't work:**
- Provider names must match `getName()` method in provider class
- Testcontainers generic properties don't enable specific providers

### ✅ Solution: Correct Provider Names
```properties
# CORRECT - matches actual provider getName() methods
hashicorp-consul.enabled=true
apache-kafka.enabled=true
apicurio.enabled=true
```

## Test Resource Development

### Creating a New Test Resource Provider

1. **Implement TestResourcesResolver**:
```java
@Singleton
public class MyServiceTestResourceProvider implements TestResourcesResolver {
    @Override
    public String getName() {
        return "my-service"; // Used in test-resources.properties
    }
    
    @Override 
    public List<String> getResolvableProperties() {
        return List.of("my.service.host", "my.service.port");
    }
    
    @Override
    public Optional<String> resolve(String propertyName, PropertyContext context) {
        // Container startup and property resolution logic
    }
}
```

2. **Register Provider**:
```
# src/main/resources/META-INF/services/io.micronaut.testresources.core.TestResourcesResolver
com.krickert.testcontainers.myservice.MyServiceTestResourceProvider
```

3. **Add to Dependencies**:
```kotlin
// build.gradle.kts
testResourcesImplementation(project(":yappy-test-resources:my-service-test-resource"))
```

### Testing Test Resources
```java
@Test
void testResourceProviderWorks() {
    // Property injection should trigger container startup
    assertNotNull(myServiceHost);
    assertNotEquals(0, myServicePort);
    
    // Verify container is reachable
    assertTrue(isServiceReachable(myServiceHost, myServicePort));
}
```

## Debugging Test Resource Issues

### 1. Check Test Resources Server Status
```bash
# Check if test resources server is running
ps aux | grep test-resources

# View test resources logs  
tail -f build/test-resources/test-resources-service.log
```

### 2. Verify Provider Registration
```bash
# Check if provider is registered
grep -r "TestResourcesResolver" src/main/resources/META-INF/services/
```

### 3. Test Property Resolution
```java
@Test
void debugPropertyResolution() {
    System.out.println("Consul host: " + consulHost);
    System.out.println("Consul port: " + consulPort);
    
    // Verify container is actually running
    assertTrue(isPortOpen(consulHost, consulPort));
}
```

### 4. Container Logs
```java
@Test  
void debugContainerIssues() {
    // Print container logs on test failure
    if (testFailed) {
        System.out.println("Container logs: " + container.getLogs());
    }
}
```

## Best Practices

### 1. Property Naming
- Use consistent property naming: `service.client.host`, `service.client.port`
- Match actual Micronaut configuration property names
- Avoid generic names like `host` or `port`

### 2. Test Isolation
```java
@BeforeEach
void cleanupBetweenTests() {
    // Clean up test data, not containers
    consulKvService.deleteAllTestKeys();
    kafkaAdminService.deleteTestTopics();
}
```

### 3. Async Testing
```java
@Test
void testAsyncBehavior() {
    // Use Awaitility for async assertions
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
            assertEquals(1, listener.getCreatedTopicsCount());
        });
}
```

### 4. Error Scenarios
```java
@Test
void testErrorHandling() {
    // Test with real infrastructure, not mocked failures
    // Create actual error conditions (invalid data, network issues)
    
    // Real Kafka broker shutdown for testing resilience
    kafkaContainer.stop();
    
    assertThrows(KafkaException.class, () -> {
        producer.send("test-topic", "test-message").get();
    });
}
```

## Architecture Decision Records

### Why Micronaut Test Resources Over Direct Testcontainers
1. **Automatic Lifecycle Management**: Containers started/stopped automatically
2. **Property Injection**: Seamless integration with Micronaut configuration
3. **Resource Sharing**: Containers reused across tests for performance
4. **Standardized Pattern**: Consistent approach across all modules

### Why Real Integration Tests Over Mocks
1. **Production Parity**: Tests mirror actual runtime behavior
2. **Early Issue Detection**: Catches integration problems during development
3. **Confidence**: Tests verify actual functionality, not mock interactions
4. **Documentation**: Tests serve as living documentation of system behavior

### Why No Localhost Hardcoding
1. **Test Isolation**: Each test gets fresh containers
2. **CI Compatibility**: Works in containerized CI environments
3. **Port Conflicts**: Dynamic ports prevent conflicts
4. **Developer Environment**: No dependencies on local services

## Migration Guide

### From Hardcoded Localhost
```java
// OLD
@Property(name = "consul.client.host", value = "localhost")
@Property(name = "consul.client.port", value = "8500")

// NEW  
@Property(name = "consul.client.host")
String consulHost;

@Property(name = "consul.client.port")
int consulPort;
```

### From Mock-Based Testing
```java
// OLD - Mock abuse
@MockBean(MyService.class)
MyService mockService() {
    return mock(MyService.class);
}

// NEW - Real integration
@Inject  
MyService realService; // Uses real implementation with test containers
```

### From Manual Container Management
```java
// OLD - Manual testcontainers
@Container
static GenericContainer<?> consul = new GenericContainer<>("consul:1.17")
    .withExposedPorts(8500);

// NEW - Test resources
@Property(name = "consul.client.host") // Automatic container management
String consulHost;
```

## Troubleshooting Common Issues

### "Connection refused" Errors
**Cause**: Test trying to connect before container is ready
**Solution**: Ensure `@Property` annotations trigger container startup

### "Port already in use" Errors  
**Cause**: Hardcoded ports conflicting with dynamic allocation
**Solution**: Remove all hardcoded port references, use test resource properties

### "Test resource not found" Errors
**Cause**: Provider not registered or wrong name
**Solution**: Check `META-INF/services` registration and provider name

### Tests Pass Locally But Fail in CI
**Cause**: CI environment differences, localhost assumptions
**Solution**: Ensure all tests use containerized infrastructure

## Summary

YAPPY's test strategy prioritizes real integration testing with containerized infrastructure over mocked implementations. This approach provides:

- **Higher Confidence**: Tests verify actual system behavior
- **Early Problem Detection**: Integration issues caught during development  
- **Production Parity**: Test environment mirrors production
- **Consistent Behavior**: Same containers across development and CI

The Micronaut Test Resources framework makes this approach practical by handling container lifecycle management and property injection automatically.