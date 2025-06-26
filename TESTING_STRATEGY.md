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

TODO: QUARKUS COMES WITH A CONSUL VERTX CLIENT!!!! DO NOT USE THIS ONE!!

```java
// In rokkon-engine/src/integrationTest/java/com/rokkon/engine/registration/ModuleRegistrationIntegrationTest.java
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import io.quarkus.consul.runtime.ConsulClient; // Assuming this is the Quarkus client
import io.vertx.ext.consul.Service; // Vert.x Consul client for Service model
import javax.inject.Inject;
import static org.junit.jupiter.api.Assertions.*;

 @engine/consul/src/test/resources/testcontainers.properties @QuarkusIntegrationTest
public class ModuleRegistrationIntegrationTest {

    // Testcontainers will start a Consul container for this test
    @rokkon-engine/src/integrationTest/java/com/rokkon/pipeline/engine/registration/ContainerAwareRegistrationIT.java
    public static GenericContainer<?> consulContainer = new GenericContainer<>("consul:latest")
            .withExposedPorts(8500);

    // This configures the running Quarkus app to use the dynamic port of our test container
    static {
        System.setProperty("quarkus.consul-config.host", "localhost");
        System.setProperty("quarkus.consul-config.port", String.valueOf(consulContainer.getMappedPort(8500)));
    }

    @rokkon-engine/src/test/resources/application-test.properties
    void testRegisterModuleFlow() {
        // Arrange: Create a client to talk to the Rokkon Engine and Consul
        // The gRPC client would call the real ModuleRegistrationService on the running app
        // For simplicity, let's assume registration is triggered and we verify the result in Consul
        String serviceName = "test-parser-module";
        String serviceId = "test-parser-module-instance-1";

        // ACT: (This part would be replaced with a real gRPC call to the engine)
        // registerModule(serviceName, serviceId, "1.2.3", "10.0.0.5", 9090);

        // ASSERT: Verify the service was actually created in the real Consul container
        @Inject
        ConsulClient quarkusConsulClient; // Injected Quarkus Consul client

        // Use Awaitility for async operations
        await().atMost(10, TimeUnit.SECONDS).until(() ->
            quarkusConsulClient.getServices().toCompletionStage().toCompletableFuture().get().containsKey(serviceId)
        );

        Service registeredService = quarkusConsulClient.getServices().toCompletionStage().toCompletableFuture().get().get(serviceId);
        assertNotNull(registeredService);
        assertEquals(serviceName, registeredService.getService());
        assertTrue(registeredService.getTags().contains("version:1.2.3"));
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
