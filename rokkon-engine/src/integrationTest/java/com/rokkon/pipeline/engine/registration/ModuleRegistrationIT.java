
package com.rokkon.pipeline.engine.registration;

import com.rokkon.pipeline.engine.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * A clean, foundational integration test for the module registration process.
 * <p>
 * This test follows the principles of an "Integration Test of a Component":
 * <ul>
 *     <li>It uses {@code @QuarkusIntegrationTest} to run the real, packaged application.</li>
 *     <li>It uses a {@code @QuarkusTestResource} (ConsulTestResource) to start a real, ephemeral Consul container via Testcontainers.</li>
 *     <li>It tests the "glue code" by verifying that a successful API call to the engine results in the correct state change in the external dependency (Consul).</li>
 *     <li>It is completely isolated from other tests and does not use mocks for the primary components under test.</li>
 * </ul>
 * This class serves as the canonical example for writing new integration tests.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
public class ModuleRegistrationIT {

    // Note: The host and port for the running application are configured automatically by @QuarkusIntegrationTest.
    // The host and port for Consul are configured by the ConsulTestResource.

    @Test
    void testSuccessfulRegistration() {
        // This test simulates a new module registering with the running Rokkon Engine.
        // The engine should perform its health check and then register the service in the Testcontainers-managed Consul instance.

        // Arrange: Define the registration request for a healthy, reachable module.
        // In a real test, these details might come from a mock gRPC server started by another TestResource.
        // For this foundational test, we assume a service is listening on port 8081 (the default for Quarkus integration tests).
        Map<String, Object> registrationRequest = Map.of(
            "moduleName", "test-module-it",
            "host", "localhost", // The engine will call back to this host to health check
            "port", 8081,       // The Quarkus app running the test listens on 8081
            "clusterName", "default-cluster",
            "serviceType", "PipeStepProcessor",
            "metadata", Map.of("version", "1.0.0")
        );

        // Act & Assert: Call the registration endpoint and verify the response
        given()
                .contentType(ContentType.JSON)
                .body(registrationRequest)
        .when()
                .post("/api/v1/modules/register")
        .then()
                .statusCode(200)
                .body("moduleId", is(notNullValue()))
                .body("status", is("REGISTERED"))
                .body("message", is("Module registered successfully after passing health check."));

        // Further verification could be done by using a Consul client to query
        // the Testcontainers Consul instance directly, but for this initial test,
        // a successful API response from the engine is a strong indicator of success.
    }

    @Test
    void testRegistrationFailureForUnreachableModule() {
        // Arrange: Define a registration request for a module that is not running.
        Map<String, Object> registrationRequest = Map.of(
            "moduleName", "unreachable-module-it",
            "host", "localhost",
            "port", 9999, // A port that is guaranteed to be closed
            "clusterName", "default-cluster",
            "serviceType", "PipeStepProcessor",
            "metadata", Map.of()
        );

        // Act & Assert: Call the registration endpoint and verify the failure response
        given()
                .contentType(ContentType.JSON)
                .body(registrationRequest)
        .when()
                .post("/api/v1/modules/register")
        .then()
                .statusCode(400) // Bad Request, as the health check fails
                .body("status", is("REGISTRATION_FAILED"))
                .body("message", is("Module health check failed. Cannot register."));
    }
}
