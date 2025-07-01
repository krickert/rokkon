package com.rokkon.pipeline.engine;

import com.google.protobuf.Empty;
import com.rokkon.pipeline.engine.test.ConsulTestResource;
import com.rokkon.search.grpc.ModuleInfo;
import com.rokkon.search.grpc.ModuleRegistration;
import com.rokkon.search.grpc.ModuleRegistrationClient;
import com.rokkon.search.grpc.RegistrationStatus;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.PipeStepProcessorClient;
import com.rokkon.search.sdk.RegistrationRequest;
import com.rokkon.search.sdk.ServiceRegistrationResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.*;

/**
 * Integration test for module registration functionality.
 * Tests the module registration flow using real containers.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ModuleRegistrationIT {

    private static final int TEST_MODULE_GRPC_PORT = 49093;

    @Container
    static GenericContainer<?> testModuleContainer = new GenericContainer<>("rokkon/test-module:1.0.0-SNAPSHOT")
            .withExposedPorts(TEST_MODULE_GRPC_PORT)
            .waitingFor(Wait.forLogMessage(".*Started gRPC server.*", 1))
            .withEnv("QUARKUS_LOG_LEVEL", "INFO");

    private static ManagedChannel testModuleChannel;
    private static PipeStepProcessor testModuleService;
    private static ManagedChannel registrationChannel;
    private static ModuleRegistration registrationService;

    @BeforeAll
    static void setupAll() {
        // Connect to test module container
        String moduleHost = testModuleContainer.getHost();
        Integer modulePort = testModuleContainer.getMappedPort(TEST_MODULE_GRPC_PORT);

        testModuleChannel = ManagedChannelBuilder
                .forAddress(moduleHost, modulePort)
                .usePlaintext()
                .build();
        testModuleService = new PipeStepProcessorClient("pipeStepProcessor", testModuleChannel,
                (serviceName, interceptors) -> interceptors);

        // Connect to registration service
        registrationChannel = ManagedChannelBuilder
                .forAddress("localhost", 9000) // Engine's gRPC port
                .usePlaintext()
                .build();
        registrationService = new ModuleRegistrationClient(
                "moduleRegistration", registrationChannel,
                (serviceName, interceptors) -> interceptors);
    }

    @AfterAll
    static void cleanupAll() {
        if (testModuleChannel != null && !testModuleChannel.isShutdown()) {
            testModuleChannel.shutdown();
        }
        if (registrationChannel != null && !registrationChannel.isShutdown()) {
            registrationChannel.shutdown();
        }
    }

    @Test
    @Order(1)
    void testPingEndpoint() {
        given()
            .when().get("/ping")
            .then()
            .statusCode(200)
            .body(is("pong"));
    }

    @Test
    @Order(2)
    void testModuleRegistrationWithNonExistentCluster() {
        // Test registering module with real test container
        ServiceRegistrationResponse serviceData = testModuleService
                .getServiceRegistration(RegistrationRequest.newBuilder().build())
                .await().indefinitely();

        assertThat(serviceData.getModuleName()).isEqualTo("test-processor");

        // Register module via gRPC
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
                .setServiceName(serviceData.getModuleName())
                .setServiceId("test-module-" + System.currentTimeMillis())
                .setHost(testModuleContainer.getHost())
                .setPort(testModuleContainer.getMappedPort(TEST_MODULE_GRPC_PORT))
                .setHealthEndpoint("/grpc.health.v1.Health/Check")
                .putMetadata("version", "1.0.0-SNAPSHOT")
                .putMetadata("type", "processor")
                .addTags("test")
                .addTags("integration")
                .build();

        RegistrationStatus response = registrationService
                .registerModule(moduleInfo)
                .await().indefinitely();

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getConsulServiceId()).isNotEmpty();
    }

    @Test
    @Order(3)
    void testRegisterModuleViaREST() {
        given()
            .contentType("application/json")
            .body(Map.of(
                "serviceName", "test-rest-module",
                "serviceId", "test-rest-" + System.currentTimeMillis(),
                "host", testModuleContainer.getHost(),
                "port", testModuleContainer.getMappedPort(TEST_MODULE_GRPC_PORT),
                "healthCheckEndpoint", "/grpc.health.v1.Health/Check",
                "metadata", Map.of(
                    "version", "1.0.0-SNAPSHOT",
                    "registeredVia", "REST"
                ),
                "tags", new String[]{"rest", "test"}
            ))
            .when().post("/api/v1/modules/register")
            .then()
            .statusCode(200)
            .body("success", is(true))
            .body("consulServiceId", notNullValue());
    }
}
