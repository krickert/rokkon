# Testing Strategy Requirements

## Overview
Define a comprehensive testing strategy that addresses the brittleness and complexity issues experienced with Micronaut, focusing on operational reliability and developer productivity.

## Core Testing Principles

### 1. Operational Effectiveness First
- **Live System Testing**: Always have the engine running for testing
- **Real Integration**: Use actual services (Consul, Kafka) not mocks
- **Production-Like**: Test environment mirrors production setup
- **Failure Scenarios**: Test operational failure modes and recovery

### 2. Developer Productivity
- **Fast Feedback**: Tests should start quickly and provide rapid feedback
- **Reliable Execution**: No more 2-3 hour debugging sessions
- **Simple Setup**: Tests "just work" without complex configuration
- **Clear Failures**: When tests fail, the reason should be immediately obvious

### 3. Scalability Validation
- **Load Testing**: Validate system behavior under load
- **Resource Monitoring**: Track memory, CPU, and connection usage
- **Throughput Testing**: Measure processing rates and bottlenecks
- **Scalability Patterns**: Test horizontal scaling scenarios

## Testing Levels

### 1. Unit Tests
**Purpose**: Fast feedback on individual components
**Scope**: Pure business logic, utilities, data transformations
**Tools**: JUnit 5, Mockito (minimal usage)

```java
@QuarkusTest
class PipelineConfigurationTest {
    
    @Test
    void shouldValidateValidPipelineConfig() {
        // Test configuration validation logic
        // No external dependencies
    }
}
```

### 2. Integration Tests
**Purpose**: Verify component interactions within the engine
**Scope**: Service interactions, configuration loading, gRPC communication
**Tools**: Quarkus Test, Dev Services

```java
@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
class EngineIntegrationTest {
    
    @Test
    void shouldProcessRequestThroughPipeline() {
        // Test with real Consul and gRPC services
        // Quarkus Dev Services provide infrastructure
    }
}
```

### 3. Module Integration Tests
**Purpose**: Validate engine-to-module communication
**Scope**: gRPC communication, module registration, health checks
**Tools**: Testcontainers for module services

```java
@QuarkusTest
class ModuleCommunicationTest {
    
    @Container
    static GenericContainer<?> chunkerModule = new GenericContainer<>("rokkon/chunker:test")
            .withExposedPorts(9090);
    
    @Test
    void shouldCommunicateWithChunkerModule() {
        // Test actual gRPC communication with chunker
    }
}
```

### 4. End-to-End Tests
**Purpose**: Validate complete data flows
**Scope**: Full pipeline execution from input to output
**Tools**: Docker Compose, real data sets

```java
@QuarkusTest
@TestProfile(E2ETestProfile.class)
class PipelineE2ETest {
    
    @Test
    void shouldProcessDocumentThroughCompletePipeline() {
        // Test complete data flow
        // Document → Chunker → Embedder → Sink
    }
}
```

## Test Infrastructure Requirements

### 1. Quarkus Dev Services Integration
**Consul Dev Service**:
```properties
# Automatic Consul container for testing
quarkus.devservices.enabled=true
quarkus.consul-config.devservices.enabled=true
quarkus.consul-config.devservices.port=0  # Random port
```

**Kafka Dev Service**:
```properties
# Automatic Kafka container for testing
%test.kafka.devservices.enabled=true
%test.kafka.devservices.port=0  # Random port
```

### 2. Test Profiles
```java
public class IntegrationTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.log.level", "INFO",
            "quarkus.log.category.\"com.rokkon\".level", "DEBUG",
            "engine.test-mode", "true"
        );
    }
    
    @Override
    public String getConfigProfile() {
        return "integration-test";
    }
}
```

### 3. Test Data Management
**Configuration Test Data**:
```java
@ApplicationScoped
public class TestDataManager {
    
    public void setupTestPipelineConfig() {
        // Create test pipeline configurations in Consul
        // Ensure consistent test data across test runs
    }
    
    public void cleanupTestData() {
        // Clean up test data after tests
    }
}
```

**Sample Documents**:
```java
public class TestDocuments {
    public static final String SAMPLE_PDF = "test-documents/sample.pdf";
    public static final String SAMPLE_TEXT = "test-documents/sample.txt";
    
    // Consistent test data for pipeline processing
}
```

## Operational Testing Requirements

### 1. Health Check Validation
```java
@Test
void shouldReportHealthyWhenAllServicesUp() {
    // Verify health check aggregation
    // Test Consul health check registration
    // Validate module health monitoring
}

@Test
void shouldReportUnhealthyWhenModuleDown() {
    // Test failure detection
    // Verify health check failure propagation
    // Test recovery after module restart
}
```

### 2. Configuration Management Testing
```java
@Test
void shouldUpdatePipelineConfigurationDynamically() {
    // Test live configuration updates via Consul
    // Verify configuration propagation
    // Test configuration validation
}

@Test
void shouldHandleConfigurationErrors() {
    // Test invalid configuration handling
    // Verify rollback mechanisms
    // Test configuration conflict resolution
}
```

### 3. Service Discovery Testing
```java
@Test
void shouldDiscoverModulesAutomatically() {
    // Test module registration process
    // Verify service discovery updates
    // Test module deregistration
}

@Test
void shouldHandleServiceFailures() {
    // Test circuit breaker behavior
    // Verify failover mechanisms
    // Test service recovery
}
```

## Scalability Testing Requirements

### 1. Load Testing
```java
@Test
void shouldHandleHighThroughput() {
    // Test with high message volume
    // Monitor resource usage
    // Verify no memory leaks
}

@Test
void shouldMaintainPerformanceUnderLoad() {
    // Test response times under load
    // Verify graceful degradation
    // Test backpressure handling
}
```

### 2. Resource Testing
```java
@Test
void shouldManageResourcesEfficiently() {
    // Monitor connection pools
    // Test thread pool utilization
    // Verify garbage collection behavior
}
```

### 3. Scaling Scenarios
```java
@Test
void shouldScaleHorizontally() {
    // Test multiple engine instances
    // Verify load distribution
    // Test cluster coordination
}
```

## Test Environment Setup

### 1. Local Development
```bash
# Simple test execution
./mvnw test

# Integration tests with real services  
./mvnw test -Dtest=**/*IntegrationTest

# Full end-to-end testing
./mvnw test -Dtest=**/*E2ETest
```

### 2. CI/CD Pipeline Testing
```yaml
# GitHub Actions / Jenkins
- name: Unit Tests
  run: ./mvnw test -Dtest.profile=unit

- name: Integration Tests  
  run: ./mvnw test -Dtest.profile=integration
  
- name: Module Tests
  run: ./mvnw test -Dtest.profile=module-integration
```

### 3. Performance Testing Environment
- **Dedicated Infrastructure**: Separate environment for performance tests
- **Monitoring**: Comprehensive metrics collection during tests
- **Baseline Comparison**: Compare performance against baseline metrics

## Test Reliability Requirements

### 1. Consistent Test Execution
- **Deterministic Results**: Tests should produce consistent results
- **Isolated Execution**: Tests should not interfere with each other
- **Clean State**: Each test starts with a clean state

### 2. Fast Test Execution
- **Parallel Execution**: Tests should run in parallel where possible
- **Resource Sharing**: Efficient use of test infrastructure
- **Quick Startup**: Test infrastructure should start quickly

### 3. Clear Test Reporting
- **Detailed Failures**: Clear error messages and stack traces
- **Test Metrics**: Execution time, resource usage metrics
- **Coverage Reports**: Code coverage and test coverage reports

## Data Scientist Module Testing

### 1. Module Development Kit
```java
// Provide testing utilities for module developers
public class ModuleTestKit {
    
    public static void validateModuleImplementation(ModuleService module) {
        // Validate required gRPC methods
        // Test service registration
        // Verify health check implementation
    }
}
```

### 2. Module Testing Templates
```java
// Template test class for new modules
@QuarkusTest
abstract class ModuleTestTemplate {
    
    @Test
    void shouldImplementRequiredGrpcMethods() {
        // Standard tests all modules must pass
    }
    
    @Test
    void shouldRegisterCorrectly() {
        // Test module registration
    }
}
```

### 3. Quick Development Validation
- **Local Testing**: Quick local validation of new modules
- **Engine Integration**: Test module integration with engine
- **Pipeline Testing**: Validate module in complete pipeline

## Success Metrics

### 1. Test Reliability
- **Flaky Test Rate**: < 1% of test executions should be flaky
- **Setup Time**: Test infrastructure setup < 30 seconds
- **Debug Time**: Issue identification < 5 minutes

### 2. Developer Productivity  
- **Test Feedback**: Results available within 2 minutes for unit tests
- **Integration Feedback**: Integration test results within 10 minutes
- **Clear Failures**: 90% of test failures should be immediately actionable

### 3. Operational Confidence
- **Production Similarity**: Test environment matches production behavior
- **Failure Detection**: Critical failures detected within test suite
- **Scalability Validation**: Performance characteristics validated through testing