c
# Rokkon Engine: Multi-Layered Testing Strategy

Based on the comprehensive architecture documents and our detailed discussion, here is a full summary of the multi-layered testing strategy for the Rokkon Engine. This framework is designed to address the challenges of a complex, distributed system by providing fast feedback during development while ensuring robust, comprehensive validation for releases.

### Guiding Principles

The core of this strategy is to separate tests into distinct layers, each with a specific purpose, scope, and set of tools. This addresses the previous issues of conflated, slow, and flaky tests by enforcing isolation where appropriate and using dedicated environments for true integration.

* **Fast Feedback:** Developers should spend most of their time running fast, isolated tests that don't rely on external infrastructure. This is the purpose of Unit and Component tests, which are optimized for Quarkus Dev Mode.
* **Leverage Quarkus Extensions:** Utilize Quarkus extensions for seamless integration with external services (e.g., Consul, Kafka) and for simplifying testing with built-in test utilities.
* **Reliable Integration:** Interactions with external systems like Consul and Kafka are critical and prone to error. Integration tests use real, ephemeral instances of these services (via Testcontainers) to validate this "glue code" reliably.
* **Full System Confidence:** End-to-end tests provide the ultimate confidence by verifying complete user workflows across the entire deployed system, just as it would run in production.

-----

### 1. Unit Tests (Isolate and Verify Logic)

* **Goal:** To verify the correctness of a single class or method (a "unit") in complete isolation from the framework and external systems.
* **Scope:** Focus on business logic, algorithms, data transformations, and state changes within a class.
* **What to Test:** Utility classes, validator logic (`engine-validators`), specific data model transformations, and individual service methods with their dependencies mocked.
* **Key Guidelines:**
    * **Strict Isolation:** These tests **must not** start the Quarkus application context. They must not access the network, filesystem, or any external processes.
    * **Mock Everything:** All external dependencies are replaced with "mocks" or "stubs." This includes `ConsulClient`, `KafkaProducer`, gRPC clients, and any other injected CDI beans.
    * **Speed is Paramount:** Unit tests should execute in milliseconds.
* **Tools:** JUnit 5, Mockito (for mocking Java dependencies).

**Example: Testing a Simple Validator**

```java
// In engine-validators/src/test/java/com/rokkon/validators/PipelineConfigValidatorTest.java

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.rokkon.models.PipelineConfig; // Assuming this model exists

class PipelineConfigValidatorTest {

    private final PipelineConfigValidator validator = new PipelineConfigValidator();

    @rokkon-engine/src/test/resources/application-test.properties
    void whenPipelineNameIsMissing_validationShouldFail() {
        // Arrange
        PipelineConfig config = new PipelineConfig();
        config.setPipelineId(null); // Invalid state
        config.setSteps(...);

        // Act & Assert
        assertFalse(validator.isValid(config), "Validation should fail for null pipeline ID");
    }

    @rokkon-engine/src/test/resources/application-test.properties
    void whenPipelineHasNoSteps_validationShouldFail() {
        // Arrange
        PipelineConfig config = new PipelineConfig();
        config.setPipelineId("my-pipeline");
        config.setSteps(Collections.emptyList()); // Invalid state

        // Act & Assert
        assertFalse(validator.isValid(config), "Validation should fail for pipeline with no steps");
    }
}
```

-----

### 2. Component Tests (Verify Service in a Mocked Context)

* **Goal:** To test a component (e.g., a JAX-RS resource, a gRPC service, or a CDI bean) within a running Quarkus application context, but with its external dependencies (like Consul or other modules) mocked.
* **Scope:** This tests the component's interaction with the Quarkus framework (CDI, REST endpoints) without the slowness and flakiness of real network calls to external systems.
* **What to Test:** REST API endpoints, gRPC service implementations, and any CDI beans that orchestrate logic while depending on external clients.
* **Key Guidelines:**
    * Use ` @QuarkusTest` to start a fully-functional, in-memory version of your application.
    * Use ` @InjectMock` to replace real external clients with mocks. You can then define the mock's behavior (e.g., `when(consulClient.register(any())).thenReturn(true);`).
    * These are considered "fast tests" and should run as part of the `quarkusDev` continuous testing feedback loop.
* **Tools:** ` @QuarkusTest`, ` @InjectMock`, Mockito, REST Assured (for testing REST endpoints).

**Example: Testing the PipelineControlService REST Endpoint**

```java
// In rokkon-engine/src/test/java/com/rokkon/engine/api/PipelineControlResourceTest.java

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import com.rokkon.engine.PipelineManager; // The service our REST resource uses

 @QuarkusTest
public class PipelineControlResourceTest {

    @InjectMock
    PipelineManager pipelineManager; // Inject a mock of the underlying service

    @rokkon-engine/src/test/resources/application-test.properties
    public void testDeployPipelineEndpoint() {
        // Arrange: Define the mock's behavior
        when(pipelineManager.deploy(anyString())).thenReturn(true);

        // Act & Assert: Use REST Assured to call the real endpoint
        given()
          .pathParam("pipelineId", "my-test-pipeline")
          .when().post("/api/v1/pipelines/{pipelineId}/deploy")
          .then()
             .statusCode(200); // Verify the HTTP response
    }

    @rokkon-engine/src/test/resources/application-test.properties
    public void testDeployNonExistentPipelineEndpoint() {
        // Arrange
        when(pipelineManager.deploy("unknown-pipeline")).thenReturn(false);

        // Act & Assert
        given()
          .pathParam("pipelineId", "unknown-pipeline")
          .when().post("/api/v1/pipelines/{pipelineId}/deploy")
          .then()
             .statusCode(404); // Or whatever failure status is appropriate
    }
}
```

-----

### 3. Testing in Dev Mode (`quarkusDev`)

* **Goal:** To provide a highly productive, rapid-iteration development environment for developers working on the Rokkon Engine and its modules.
* **Scope:** Focus on live coding of the application logic, with immediate feedback from hot-reloading and the automatic execution of fast Unit and Component tests.
* **Workflow:**
    1.  **Start Dependencies:** Run essential external services like Consul and Kafka in the background using Docker Compose: `docker-compose up -d consul kafka`. These services are now available on `localhost`.
    2.  **Start Dev Mode:** Run the Rokkon Engine in dev mode: `./gradlew :rokkon-engine:quarkusDev`.
    3.  **Code and Test:** As you make changes to the engine's source code, Quarkus automatically hot-reloads the application. In parallel, it re-runs all associated Unit and Component tests, giving you instant feedback on whether your changes broke existing functionality.
* **Key Guidelines:**
    * This setup is for developing the engine's *internal logic*, not for testing full data flow through external modules.
    * Communication from the host (running `quarkusDev`) to Docker (Consul/Kafka) is straightforward via `localhost` port mappings.
    * Communication from Docker (e.g., a running module trying to register) to the host requires using the special DNS name `host.docker.internal` (for Docker Desktop) or the host's bridge IP as the `ENGINE_HOST`.
    * This is a developer productivity loop, not a full system integration test.

-----

### 4. Integration Test of a Component

* **Goal:** To verify a component's interaction with *real, live dependencies* (like Consul or Kafka) in an isolated, controlled, and reproducible environment.
* **Scope:** This is where you test the "glue code" that was mocked in component tests. For example, does your module registration code write the correct key-value structure into a *real* Consul instance?
* **What to Test:** Service registration, dynamic configuration loading from Consul, producing/consuming messages with Kafka, and sink modules writing to a database.
* **Key Guidelines:**
    * Use ` @QuarkusIntegrationTest` which packages the application and runs it as a JAR, closely mimicking production.
    * Use **Testcontainers** to programmatically start and stop Docker containers for your dependencies (e.g., Consul, Kafk.a, OpenSearch) for each test or test suite. This guarantees a clean, isolated environment for every run.
    * Place these tests in a separate Gradle source set, typically `src/integrationTest/java`, to keep them separate from fast unit/component tests.
    * Because these tests involve container startup and real network I/O, they are slower and are typically run by CI/CD pipelines or manually before committing code.
* **Tools:** ` @QuarkusIntegrationTest`, Testcontainers, Awaitility (for handling asynchronous operations).

**Example: Testing Real Module Registration with Consul**

```java
// In engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/service/ClusterServiceIT.java
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.common.QuarkusTestResource;
import com.rokkon.pipeline.consul.test.ConsulTestResource;
import com.rokkon.pipeline.config.service.ClusterService;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)  // Automatically starts Consul container
public class ClusterServiceIT extends ClusterServiceTestBase {
    
    @Inject
    ClusterService clusterService;
    
    @Test
    void testCreateClusterInRealConsul() {
        // This test runs against a real Consul instance started by ConsulTestResource
        var result = clusterService.createCluster("test-cluster")
            .await().indefinitely();
            
        assertThat(result.valid()).isTrue();
        
        // Verify the cluster was actually created in Consul
        var cluster = clusterService.getCluster("test-cluster")
            .await().indefinitely();
            
        assertThat(cluster).isPresent();
        assertThat(cluster.get().name()).isEqualTo("test-cluster");
    }
}
```

-----

### 5. Full End-to-End (E2E) Testing

* **Goal:** To validate an entire business workflow across the complete, deployed system, from user input to final output.
* **Scope:** Simulates real-world usage. For Rokkon, a typical E2E test would involve deploying a full pipeline, pushing data into a connector, and verifying that the correctly transformed data appears in a sink.
* **What to Test:** The "A/B Testing" scenario from the architecture docs is a perfect E2E test case. Other examples include testing the Claim Check pattern for large messages or verifying role-based access control from the UI.
* **Workflow:**
    1.  **Environment Setup:** Deploy all required services (Rokkon Engine, multiple modules, Consul, Kafka, OpenSearch sinks, etc.) using Docker Compose or a Kubernetes manifest. This creates a fully networked environment.
    2.  **Test Execution:** Use an external test driver (e.g., a separate Java/Python test script) to interact with the system purely through its public APIs (REST, gRPC).
    3.  **Example Steps:**
        * Use a REST client to send a `PipelineConfig` to the Rokkon Engine's API.
        * Call the `deploy` endpoint.
        * Push a sample document to the pipeline's connector module via its gRPC endpoint.
        * Poll the final OpenSearch sink until the processed document appears.
        * Query the OpenSearch index and assert that the document was chunked, embedded, and indexed as expected.
* **Key Guidelines:**
    * These tests are "black-box"—they know nothing about the internal implementation, only the public interfaces.
    * E2E tests are the most valuable for ensuring all parts of the system work together correctly.
    * They are the slowest and most complex to maintain. They should be run as part of a CI/CD pipeline before a release to production.
    * This layer is where you can later add performance, load, and stress testing.

-----

## Updated Testing Patterns and Lessons Learned

### UnifiedTestProfile - Solving Quarkus Test Profile Conflicts

One of the major challenges we encountered was Quarkus test profile conflicts. When different tests use different `@TestProfile` annotations, Quarkus must restart the application context between tests, leading to slow test execution and potential port conflicts. To solve this, we created the **UnifiedTestProfile**.

#### The UnifiedTestProfile Pattern

The UnifiedTestProfile acts as a single profile that adapts its behavior based on the test being run:

```java
@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
class MyUnitTest {
    // Test implementation
}
```

Key features:
- **Single Profile**: All tests use the same profile, preventing Quarkus restarts
- **Dynamic Configuration**: Determines configuration based on test class name and annotations
- **Automatic Consul Handling**: Unit tests get Consul disabled, integration tests get it enabled
- **Test Isolation**: Each test gets its own KV prefix in Consul to prevent conflicts

The profile automatically configures:
- Consul enabled/disabled based on test type (unit vs integration)
- Test-specific KV prefixes for isolation
- Mock vs real validators
- Health check settings
- Scheduler settings

### Test Organization Pattern: Base/Unit/Integration

Based on our refactoring experience, we've established a pattern for organizing tests that promotes code reuse while maintaining clear separation between test types:

#### 1. Base Test Classes
- **Location**: `src/test/java` (main test directory for sharing)
- **Purpose**: Define common test logic that can be reused by both unit and integration tests
- **Pattern**: Abstract class with abstract methods for dependency injection

```java
// MethodicalBuildUpTestBase.java
public abstract class MethodicalBuildUpTestBase {
    private static final Logger LOG = Logger.getLogger(MethodicalBuildUpTestBase.class);
    
    // Abstract methods for dependency injection
    protected abstract ClusterService getClusterService();
    protected abstract ModuleWhitelistService getModuleWhitelistService();
    
    // Common test logic
    @Test
    void testCreateCluster() {
        ValidationResult result = getClusterService().createCluster("test")
            .await().indefinitely();
        assertThat(result.valid()).isTrue();
    }
}
```

#### 2. Unit Test Implementation
- **Location**: `src/test/java`
- **Framework**: `@QuarkusTest` with `@InjectMock`
- **Purpose**: Test service interactions with mocked dependencies

```java
@QuarkusTest
class MethodicalBuildUpUnitTest extends MethodicalBuildUpTestBase {
    @InjectMock
    ClusterService clusterService;
    
    @BeforeEach
    void setupMocks() {
        when(clusterService.createCluster(anyString()))
            .thenReturn(Uni.createFrom().item(ValidationResult.success()));
    }
    
    @Override
    protected ClusterService getClusterService() {
        return clusterService;
    }
}
```

#### 3. Integration Test Implementation
- **Location**: `src/integrationTest/java`
- **Framework**: `@QuarkusIntegrationTest` (or `@QuarkusTest` with real resources)
- **Purpose**: Test with real infrastructure (Consul, containers)

```java
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
class MethodicalBuildUpIntegrationTest extends MethodicalBuildUpTestBase {
    @Inject
    ClusterService clusterService;
    
    @Override
    protected ClusterService getClusterService() {
        return clusterService;
    }
}
```

### Key Learnings from Test Refactoring

#### 1. Context Propagation Issues

**Problem**: Tests failing with `SmallRyeContextManager` errors:
```
Cannot invoke "io.smallrye.context.SmallRyeContextManager.defaultThreadContext()" 
because the return value of "io.smallrye.context.SmallRyeContextManagerProvider.getManager()" is null
```

**Root Cause**: These errors typically indicate that a test requires full Quarkus context but is trying to run in a limited test environment.

**Solutions**:
- Move tests requiring full context to integration tests
- Ensure `quarkus-smallrye-context-propagation` dependency is present
- For unit tests, mock the services that require context propagation
- Create wrapper interfaces for hard-to-mock components

#### 2. Logging Best Practices

**Always use Logger instead of System.out**:
```java
// Bad
System.out.println("Test passed");

// Good
private static final Logger LOG = Logger.getLogger(MyTest.class);
LOG.info("Test passed");
```

**Benefits**:
- Consistent with production code
- Configurable log levels
- Better IDE and CI/CD integration
- Structured logging support

#### 3. Mocking Strategies

**Create Mock Implementations for Complex Dependencies**:

When Mockito becomes cumbersome (e.g., for Vertx, ConsulClient), create mock implementations:

```java
// Interface
public interface ConsulService {
    Uni<Boolean> registerService(String name, String id);
    Uni<List<Service>> getServices();
}

// Real implementation
@ApplicationScoped
public class ConsulServiceImpl implements ConsulService {
    @Inject Vertx vertx;
    private ConsulClient client;
    // Real Consul operations
}

// Mock implementation for tests
public class MockConsulService implements ConsulService {
    private Map<String, Service> services = new HashMap<>();
    
    @Override
    public Uni<Boolean> registerService(String name, String id) {
        services.put(id, new Service(name, id));
        return Uni.createFrom().item(true);
    }
}
```

#### 4. Test Resource Management

**Keep Test Resources Simple**:
- Test resources should only manage lifecycle (start/stop)
- Don't put data seeding logic in test resources
- Use separate seeding services that can be injected and controlled by tests

```java
// Good: Simple lifecycle management
public class ConsulTestResource implements QuarkusTestResourceLifecycleManager {
    private ConsulContainer consul;
    
    @Override
    public Map<String, String> start() {
        consul = new ConsulContainer();
        consul.start();
        return Map.of(
            "consul.host", consul.getHost(),
            "consul.port", String.valueOf(consul.getMappedPort(8500))
        );
    }
    
    @Override
    public void stop() {
        if (consul != null) {
            consul.stop();
        }
    }
}
```

#### 5. Test Profiles and No-Consul Testing

One of our major achievements was creating test profiles that allow unit tests to run without external dependencies like Consul. This dramatically improves test speed and reliability.

**NoConsulTestProfile - For True Unit Tests**:
```java
public class NoConsulTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Disable Consul completely
            "quarkus.consul-config.enabled", "false",
            "quarkus.consul.enabled", "false",
            
            // Set a default cluster name for tests
            "rokkon.cluster.name", "unit-test-cluster",
            
            // Disable health checks that might connect to Consul
            "quarkus.health.extensions.enabled", "false",
            
            // Configure test validators
            "test.validators.mode", "empty",
            "test.validators.use-real", "false"
        );
    }
    
    @Override
    public String getConfigProfile() {
        return "test-no-consul";
    }
}
```

**WithConsulTestProfile - For Integration Tests**:
```java
public class WithConsulTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Enable Consul for integration tests
            "quarkus.consul-config.enabled", "true",
            "quarkus.consul.enabled", "true",
            
            // Use test-specific Consul paths
            "quarkus.consul-config.properties-value-keys[0]", "test/config/application",
            
            // Test cluster configuration
            "rokkon.cluster.name", "integration-test-cluster"
        );
    }
}
```

**RealValidatorsTestProfile - Component Tests with Real Validators**:
```java
public class RealValidatorsTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        
        // Disable Consul but use real validators
        config.put("quarkus.consul-config.enabled", "false");
        config.put("quarkus.consul.enabled", "false");
        
        // Use REAL validators for component testing
        config.put("test.validators.mode", "real");
        config.put("test.validators.use-real", "true");
        
        return config;
    }
}

#### 6. Validation Framework Refactoring

A major breakthrough in our testing strategy was refactoring the validation framework to break circular dependencies between modules. This was "the center of why a lot of our tests were so hard to create and take so long."

**The Problem**: 
- Validation interfaces in `rokkon-commons` were importing model classes from `engine/models`
- This created circular dependencies that made mocking difficult
- Tests couldn't easily inject or mock validators

**The Solution - Tagged/Marker Interfaces**:

```java
// In rokkon-commons - generic interfaces with no model dependencies
public interface ConfigValidatable {
    // Marker interface for validatable configurations
}

public interface ConfigValidator<T extends ConfigValidatable> {
    ValidationResult validate(T config);
    default String getValidatorName() { return this.getClass().getSimpleName(); }
    default int getPriority() { return 100; }
}
```

```java
// In engine/models - specific implementations
public record PipelineConfig(
    String name,
    Map<String, PipelineStepConfig> pipelineSteps
) implements PipelineConfigValidatable {
    
    public PipelineConfig {
        // Constructor validation - pipelines MUST have names
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("PipelineConfig name cannot be null or blank.");
        }
    }
}

// Tagged interface extending the generic one
public interface PipelineConfigValidatable extends ConfigValidatable {
}
```

**CompositeValidator with CDI Producer**:

The `CompositeValidator` needed a CDI producer because it doesn't have a no-arg constructor:

```java
@ApplicationScoped
public class ValidatorProducer {
    
    @Inject
    Instance<ConfigValidator<PipelineConfig>> pipelineValidators;
    
    @Produces
    @ApplicationScoped
    public CompositeValidator<PipelineConfig> pipelineConfigValidator() {
        List<ConfigValidator<PipelineConfig>> validators = new ArrayList<>();
        
        // Add all discovered validators
        for (ConfigValidator<PipelineConfig> validator : pipelineValidators) {
            if (!(validator instanceof CompositeValidator)) {
                validators.add(validator);
            }
        }
        
        return new CompositeValidator<>("PipelineConfigComposite", validators);
    }
}
```

**Test-Specific Validator Producer**:

For tests, we created a flexible producer that can switch between empty, mock, or real validators:

```java
@Mock
@ApplicationScoped
@Alternative
@Priority(1)
public class TestValidatorProducer {
    
    @ConfigProperty(name = "test.validators.mode", defaultValue = "empty")
    String validatorMode; // empty, real, failing, mixed
    
    @Produces
    @ApplicationScoped
    public CompositeValidator<PipelineConfig> testPipelineConfigValidator() {
        CompositeValidatorBuilder<PipelineConfig> builder = CompositeValidatorBuilder.<PipelineConfig>create()
            .withName("TestPipelineConfigValidator");
        
        switch (validatorMode) {
            case "real":
                // Use all real validators
                for (ConfigValidator<PipelineConfig> validator : realPipelineValidators) {
                    if (!(validator instanceof CompositeValidator)) {
                        builder.addValidator(validator);
                    }
                }
                break;
                
            case "failing":
                // Always fail for testing error paths
                builder.withFailingValidation("Test validation failure");
                break;
                
            case "empty":
            default:
                // No validation - always succeeds
                builder.withEmptyValidation();
                break;
        }
        
        return builder.build();
    }
}
```

**Builder Pattern for Validators**:

We introduced a builder pattern to make validator creation less error-prone:

```java
public class CompositeValidatorBuilder<T extends ConfigValidatable> {
    
    public static <T extends ConfigValidatable> CompositeValidatorBuilder<T> create() {
        return new CompositeValidatorBuilder<>();
    }
    
    public CompositeValidatorBuilder<T> withName(String name) {
        this.name = name;
        return this;
    }
    
    public CompositeValidatorBuilder<T> addValidator(ConfigValidator<T> validator) {
        this.validators.add(validator);
        return this;
    }
    
    public CompositeValidatorBuilder<T> withEmptyValidation() {
        this.validators.clear();
        this.validators.add(new ConfigValidator<T>() {
            @Override
            public ValidationResult validate(T config) {
                return ValidationResult.empty();
            }
        });
        return this;
    }
    
    public CompositeValidator<T> build() {
        return new CompositeValidator<>(name, validators);
    }
}
```

#### 7. Integration Test Naming Convention

All integration tests should follow the "IT" suffix convention:
- Unit tests: `SomeServiceTest.java` (in `src/test/java`)
- Integration tests: `SomeServiceIT.java` (in `src/integrationTest/java`)

This helps:
- Gradle can easily separate test execution
- Developers immediately know which tests require infrastructure
- CI/CD can run different test suites at different stages

### Migration Checklist

When refactoring tests:

1. **Identify Test Type**:
   - [ ] Is it testing a single class? → Unit test with mocks
   - [ ] Is it testing service interactions? → Component test with selective mocks
   - [ ] Does it need real infrastructure? → Integration test

2. **Check for Context Issues**:
   - [ ] SmallRyeContextManager errors? → Ensure we can't mock the dependency causing the issue. If this is a test geared toward consul-soecific functionality or docker container functionality then it should go into an integration test. 
   - [ ] Complex async operations? → Consider integration test
   - [ ] Can be simplified with mocks? → Keep as unit test

3. **Apply Patterns**:
   - [ ] Extract common logic to base class
   - [ ] Create unit test with mocks
   - [ ] Create integration test with real services
   - [ ] Use Logger instead of System.out
   - [ ] Create interfaces for hard-to-mock components

4. **Organize Code**:
   - [ ] Base classes in `src/test/java`
   - [ ] Unit tests in `src/test/java`
   - [ ] Integration tests in `src/integrationTest/java`
   - [ ] Keep `@Disabled` annotation with explanation for WIP tests

## Quarkus-Specific Testing Guidelines

Based on Quarkus testing best practices, we follow these conventions:

### 1. Test Separation

#### Unit Tests (@QuarkusTest)
- **Location**: `src/test/java`
- **Execution**: Maven Surefire plugin during `test` phase
- **Purpose**: Test with mocked dependencies, fast feedback
- **Example**:
```java
@QuarkusTest
@TestProfile(NoConsulTestProfile.class)
class ServiceUnitTest extends ServiceTestBase {
    @InjectMock
    ExternalService mockService;
}
```

#### Integration Tests (@QuarkusIntegrationTest)
- **Location**: `src/integrationTest/java`
- **Execution**: Maven Failsafe plugin during `integration-test` phase
- **Naming**: Must end with `IT` (e.g., `ServiceIT.java`)
- **Purpose**: Test with real infrastructure via Testcontainers
- **Example**:
```java
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
class ServiceIT extends ServiceTestBase {
    @Inject
    ExternalService realService;
}
```

### 2. Gradle Configuration

Quarkus automatically configures the `integrationTest` source set when using the Quarkus Gradle plugin. No manual configuration is needed!

**Integration tests are run with**:
```bash
# Run only integration tests
./gradlew integrationTest

# Run all tests (unit + integration)
./gradlew test integrationTest

# Or use Quarkus-specific task
./gradlew quarkusIntTest
```

**The Quarkus plugin automatically**:
- Creates the `src/integrationTest/java` source set
- Configures proper classpaths
- Sets up the `integrationTest` and `quarkusIntTest` tasks
- Ensures integration tests run after the application is built

### 3. Test Resources and Lifecycle

#### QuarkusTestResource
- Use `@QuarkusTestResource` for managing external services
- Implement `QuarkusTestResourceLifecycleManager` for custom resources
- Keep resources simple - only lifecycle management
- Example:
```java
public class ConsulTestResource implements QuarkusTestResourceLifecycleManager {
    private ConsulContainer consul;
    
    @Override
    public Map<String, String> start() {
        consul = new ConsulContainer("consul:latest");
        consul.start();
        return Map.of(
            "quarkus.consul.host", consul.getHost(),
            "quarkus.consul.port", String.valueOf(consul.getMappedPort(8500))
        );
    }
    
    @Override
    public void stop() {
        if (consul != null) consul.stop();
    }
}
```

### 4. Mocking with Quarkus

#### @InjectMock (Preferred)
- Requires `quarkus-junit5-mockito` dependency
- Creates Mockito mocks automatically
- Scoped to test class
- Example:
```java
@QuarkusTest
class ServiceTest {
    @InjectMock
    ExternalService externalService;
    
    @Test
    void testWithMock() {
        when(externalService.getData()).thenReturn("mocked");
        // test logic
    }
}
```

#### CDI @Alternative
- Place in `src/test/java`
- Use `@Mock` stereotype (includes `@Alternative`, `@Priority(1)`, `@Dependent`)
- Global for all tests

### 5. Test Profiles Best Practices

#### Profile Implementation
```java
public class NoConsulTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.consul-config.enabled", "false",
            "quarkus.consul.enabled", "false",
            "test.validators.mode", "empty"
        );
    }
    
    @Override
    public String getConfigProfile() {
        return "test-no-consul";
    }
}
```

#### Common Test Profiles in Rokkon
1. **NoConsulTestProfile**: Disables Consul for unit tests
2. **WithConsulTestProfile**: Enables Consul for integration tests
3. **RealValidatorsTestProfile**: Uses real validators without Consul
4. **MockValidatorsTestProfile**: Uses mock validators
5. **EmptyValidatorsTestProfile**: No validation (fastest)

### 6. Test Execution Order

Quarkus uses `QuarkusTestProfileAwareClassOrderer` to minimize restarts:
- Tests with same profile run together
- Profile changes trigger application restart
- Configure via `quarkus.test.class-orderer` property

### 7. Dev Services Integration

For integration tests, Quarkus Dev Services can automatically start containers:
- Consul: `quarkus.consul.devservices.enabled=true`
- Kafka: `quarkus.kafka.devservices.enabled=true`
- Database: Various DB-specific dev services

Access auto-configured properties:
```java
@QuarkusIntegrationTest
class ServiceIT {
    @Inject
    DevServicesContext devServicesContext;
    
    @Test
    void testWithDevServices() {
        String consulUrl = devServicesContext.devServicesProperties()
            .get("quarkus.consul.host");
    }
}
```

### 8. Continuous Testing in Dev Mode

Run tests continuously during development:
```bash
./gradlew quarkusDev
```

Configure test execution:
- `quarkus.test.continuous-testing=enabled`
- `quarkus.test.include-tags=fast`
- `quarkus.test.exclude-tags=slow`

### Test Execution Performance Improvements

Our refactoring has dramatically improved test execution times:

**Before Refactoring**:
- Tests required Consul to be running
- Each test suite took 30-60 seconds due to Consul connection attempts
- Tests would fail randomly due to Consul availability
- Developers avoided running tests locally

**After Refactoring**:
- Unit tests with NoConsulProfile: **0.220s** (previously 30+ seconds)
- Component tests with mocks: **0.143s**
- Integration tests with real Consul: 5-10s (only when needed)
- Tests run reliably without external dependencies

**Key Performance Wins**:
1. **Fast Feedback Loop**: Unit tests run in milliseconds, not seconds
2. **Reliable CI/CD**: No more random failures due to Consul connectivity
3. **Developer Productivity**: Tests can run continuously in dev mode
4. **Selective Integration**: Only run integration tests when testing actual integration points

### Practical Examples of Test Types

#### Example 1: Constructor Validation (Unit Test)
```java
@Test
void testPipelineConfigRequiresName() {
    // Test that null name throws exception
    assertThatThrownBy(() -> new PipelineConfig(null, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name cannot be null");
}
```
**Runtime**: ~5ms

#### Example 2: Service with Mocked Dependencies (Component Test)
```java
@QuarkusTest
@TestProfile(NoConsulTestProfile.class)
class CustomClusterLifecycleUnitTest {
    @InjectMock
    ClusterService clusterService;
    
    @Test
    void testStartupWithCustomClusterNotExisting() {
        when(clusterService.getCluster(eq("test-cluster")))
            .thenReturn(Uni.createFrom().item(Optional.empty()));
        
        // Test logic without Consul
    }
}
```
**Runtime**: ~220ms

#### Example 3: Real Infrastructure (Integration Test)
```java
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
class MethodicalBuildUpIT {
    @Test
    void testCreateClusterInConsul() {
        // Uses real Consul via Testcontainers
        ValidationResult result = clusterService.createCluster("test")
            .await().indefinitely();
        assertThat(result.valid()).isTrue();
    }
}
```
**Runtime**: 5-10s (includes container startup)

### Factory Methods for Test Data

We introduced factory methods to make test data creation consistent and less error-prone:

```java
// In Cluster.java
public static Cluster emptyCluster(String name) {
    return new Cluster(
        name,
        Instant.now().toString(),
        new ClusterMetadata(name, Instant.now(), null, Map.of())
    );
}

public static Cluster testCluster(String name, Map<String, Object> metadata) {
    return new Cluster(
        name,
        Instant.now().toString(),
        new ClusterMetadata(name, Instant.now(), null, metadata)
    );
}
```

This prevents issues with complex constructors and makes tests more readable:
```java
// Before
new Cluster("test", null); // Compilation error!

// After
Cluster.emptyCluster("test"); // Works perfectly
```

### Additional Patterns and Lessons from Test Migration

#### MockConsulClient Pattern

When testing Consul-dependent services without real Consul, we created a mock implementation of the Vertx Mutiny ConsulClient:

```java
public class MockConsulClient extends ConsulClient {
    private final Map<String, String> kvStore = new HashMap<>();
    
    public MockConsulClient() {
        super(null); // No delegate needed for mock
    }
    
    @Override
    public Uni<Boolean> putValue(String key, String value) {
        kvStore.put(key, value);
        return Uni.createFrom().item(true);
    }
    
    @Override
    public Uni<KeyValue> getValue(String key) {
        String value = kvStore.get(key);
        if (value == null) {
            return Uni.createFrom().nullItem();
        }
        
        // Return mock KeyValue using non-mutiny class
        KeyValue kv = new KeyValue() {
            @Override
            public String getKey() { return key; }
            @Override
            public String getValue() { return value; }
            // ... other required methods
        };
        
        return Uni.createFrom().item(kv);
    }
}
```

**Key Learning**: The Vertx Mutiny ConsulClient returns non-mutiny `KeyValue` objects wrapped in Mutiny `Uni`. This is important to remember when mocking.

#### State-Based Mocking for Complex Test Scenarios

For complex test scenarios like the MethodicalBuildUpTest, we needed mocks that could change behavior based on test progression:

```java
@QuarkusTest
class MethodicalBuildUpUnitTest extends MethodicalBuildUpTestBase {
    
    // Track state for mocking
    private boolean pipelineHasStep = false;
    
    @BeforeEach
    void setupMocks() {
        // Reset state
        pipelineHasStep = false;
        
        // Mock that changes behavior based on state
        when(testSeedingService.seedStep5_FirstPipelineStepAdded())
            .thenAnswer(invocation -> {
                pipelineHasStep = true;
                return Uni.createFrom().item(true);
            });
            
        when(pipelineConfigService.getPipeline(anyString(), anyString()))
            .thenAnswer(invocation -> {
                if (pipelineHasStep) {
                    // Return pipeline with step after seedStep5
                    PipelineStepConfig step = createTestStep();
                    return Uni.createFrom().item(Optional.of(
                        new PipelineConfig("test-pipeline", Map.of("test-step-1", step))
                    ));
                } else {
                    // Return empty pipeline before seedStep5
                    return Uni.createFrom().item(Optional.of(
                        new PipelineConfig("test-pipeline", Map.of())
                    ));
                }
            });
    }
}
```

#### Working with Nested Classes in Mocks

When creating mock data for complex models with nested classes (like `PipelineStepConfig.ProcessorInfo`), use the fully qualified class name:

```java
// ProcessorInfo is an inner class of PipelineStepConfig
PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
    "test-module",  // grpcServiceName
    null            // internalProcessorBeanName
);
```

#### Alternative Mock Services with CDI

For services that are difficult to mock with Mockito (like ClusterService interface implementations), we created alternative CDI beans:

```java
@Alternative
@ApplicationScoped
public class MockClusterService implements ClusterService {
    private final Map<String, ClusterMetadata> clusters = new ConcurrentHashMap<>();
    
    @Override
    public Uni<ValidationResult> createCluster(String clusterName) {
        if (clusterName == null || clusterName.trim().isEmpty()) {
            return Uni.createFrom().item(
                ValidationResultFactory.failure("Cluster name cannot be empty")
            );
        }
        
        if (clusters.containsKey(clusterName)) {
            return Uni.createFrom().item(
                ValidationResultFactory.failure("Cluster '" + clusterName + "' already exists")
            );
        }
        
        ClusterMetadata metadata = new ClusterMetadata(
            clusterName,
            Instant.now(),
            null,
            Map.of("status", "active", "created", Instant.now().toString())
        );
        
        clusters.put(clusterName, metadata);
        return Uni.createFrom().item(ValidationResultFactory.success());
    }
}
```

Then enable it in a test profile:

```java
public class MockClusterServiceProfile implements QuarkusTestProfile {
    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(MockClusterService.class);
    }
}
```

#### Test Migration Progress Tracking

We created TEST_INVENTORY.md to track migration progress with clear status indicators:
- ✅ Migrated to base/unit/integration pattern
- 🔧 In progress
- ❌ Not yet migrated
- 🚫 Disabled/needs fixing
- ⚠️ May need new test profile

This helped manage the large-scale migration of 172 tests across the project.

### Summary

The testing strategy has evolved from a monolithic approach where all tests required full infrastructure to a layered approach where:

1. **80% of tests** run without any external dependencies (unit/component tests)
2. **15% of tests** use selective real components (component tests with real validators)
3. **5% of tests** require full infrastructure (integration/E2E tests)

This aligns with the testing pyramid principle and provides:
- Fast developer feedback
- Reliable CI/CD pipelines
- Comprehensive coverage when needed
- Clear separation of concerns

The key insight was recognizing that circular dependencies and tight coupling were "the center of why a lot of our tests were so hard to create and take so long." By breaking these dependencies and creating proper abstractions, we've made the codebase significantly more testable and maintainable.

**Performance Improvements from Migration**:
- BasicConsulConnectionUnitTest: 0.208s (was 30+ seconds)
- ConsulConfigSuccessFailUnitTest: 0.112s (4 tests)
- IsolatedConsulKvUnitTest: 0.117s (4 tests)
- ParallelConsulKvUnitTest: 0.140s (5 tests)
- ClusterServiceUnitTest: 0.119s (6 tests)
- MethodicalBuildUpUnitTest: 3.239s (7 complex tests)

All unit tests now run without Consul, providing reliable and fast feedback during development.

### Module Organization Rules

To maintain clean architecture and prevent circular dependencies, we follow strict module organization rules:

#### 1. **rokkon-commons** - Interfaces and Contracts Only
- **Contains**: Service interfaces, marker interfaces, base abstractions
- **No Dependencies**: Must not depend on any other Rokkon modules
- **Examples**:
  ```java
  // Good - generic interface with no model dependencies
  public interface ConfigValidator<T extends ConfigValidatable> {
      ValidationResult validate(T config);
  }
  
  // Bad - importing specific models
  import com.rokkon.pipeline.config.model.PipelineConfig; // DON'T DO THIS!
  ```

#### 2. **engine/models** - Pure Data Models
- **Contains**: Records, POJOs, DTOs with no business logic
- **Allowed Dependencies**: Can depend on rokkon-commons for interfaces
- **Examples**:
  ```java
  // Good - data model implementing commons interface
  public record PipelineConfig(
      String name,
      Map<String, PipelineStepConfig> pipelineSteps
  ) implements PipelineConfigValidatable {
      // Constructor validation is OK
      public PipelineConfig {
          if (name == null || name.isBlank()) {
              throw new IllegalArgumentException("Name required");
          }
      }
  }
  
  // Bad - business logic in models
  public void deployPipeline() { // DON'T DO THIS!
      // Business logic belongs in services
  }
  ```

#### 3. **test-utilities** - Shared Test Infrastructure
- **Contains**: Test containers, mock implementations, test data builders
- **Purpose**: Reusable test components across modules
- **Examples**:
  ```java
  // Good - reusable test container
  public class ConsulTestResource implements QuarkusTestResourceLifecycleManager {
      private ConsulContainer consul;
      
      @Override
      public Map<String, String> start() {
          consul = new ConsulContainer();
          consul.start();
          return Map.of("consul.host", consul.getHost());
      }
  }
  
  // Good - mock implementation for testing
  public class MockClusterService implements ClusterService {
      private Map<String, ClusterMetadata> clusters = new HashMap<>();
      
      @Override
      public Uni<ValidationResult> createCluster(String name) {
          clusters.put(name, new ClusterMetadata(...));
          return Uni.createFrom().item(ValidationResult.success());
      }
  }
  ```

#### 4. **engine/consul** - Consul-Specific Implementations
- **Contains**: Service implementations that interact with Consul
- **Dependencies**: Can depend on models, commons, and Consul client libraries
- **Not Allowed**: Other modules should not depend on consul internals

#### 5. **engine/validators** - Validation Logic
- **Contains**: Concrete validator implementations, composite validators
- **Dependencies**: Can depend on models and commons
- **Produces**: CDI beans for validators that can be injected

### Dependency Flow

```
rokkon-commons (interfaces)
    ↑
engine/models (data)
    ↑
engine/validators (validation logic)
engine/consul (service implementations)
    ↑
rokkon-engine (main application)
    ↑
test-utilities (test helpers)
```

### Anti-Patterns to Avoid

1. **Circular Dependencies**:
   ```java
   // BAD - commons depending on models
   // In rokkon-commons
   import com.rokkon.pipeline.config.model.PipelineConfig;
   
   // BAD - models depending on service implementations
   // In engine/models
   import com.rokkon.pipeline.consul.service.ClusterServiceImpl;
   ```

2. **Business Logic in Models**:
   ```java
   // BAD - models should be pure data
   public record PipelineConfig(...) {
       public void validateWithConsul() { // NO!
           // This belongs in a service or validator
       }
   }
   ```

3. **Concrete Implementations in Commons**:
   ```java
   // BAD - commons should only have interfaces
   @ApplicationScoped
   public class DefaultValidator implements Validator { // NO!
       // Implementations belong in specific modules
   }
   ```

### Benefits of This Structure

1. **Clear Dependency Graph**: No circular dependencies
2. **Easy Mocking**: Interfaces in commons make mocking straightforward
3. **Module Independence**: Each module has a clear responsibility
4. **Test Isolation**: Test utilities centralized and reusable
5. **Faster Builds**: Clear dependencies improve build performance

This structure enforces clean architecture principles and makes the codebase more maintainable and testable.
