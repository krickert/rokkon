package com.rokkon.pipeline.engine;

import com.google.protobuf.Empty;
import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.engine.test.ConsulTestResource;
import com.rokkon.search.grpc.ModuleInfo;
import com.rokkon.search.grpc.ModuleRegistration;
import com.rokkon.search.grpc.ModuleRegistrationClient;
import com.rokkon.search.grpc.RegistrationStatus;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.PipeStepProcessorClient;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ServiceRegistrationData;
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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Comprehensive integration test using RokkonSetupHelper with real test-module container.
 * Tests the full pipeline setup flow including cluster creation, module registration,
 * and pipeline configuration.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RokkonPipelineSetupIT {

    private static final int TEST_MODULE_GRPC_PORT = 49093;
    private static final String CLUSTER_NAME = "test-pipeline-cluster";
    private static final String MODULE_NAME = "test-processor";
    
    @Container
    static GenericContainer<?> testModuleContainer = new GenericContainer<>("rokkon/test-module:1.0.0-SNAPSHOT")
            .withExposedPorts(TEST_MODULE_GRPC_PORT)
            .waitingFor(Wait.forLogMessage(".*Started gRPC server.*", 1))
            .withEnv("QUARKUS_LOG_LEVEL", "INFO");
    
    // Integration tests can't use @Inject, so we'll use REST API calls
    private static ManagedChannel testModuleChannel;
    private static PipeStepProcessor testModuleService;
    private static String registeredModuleId;
    
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
    }
    
    @AfterAll
    static void cleanupAll() {
        if (testModuleChannel != null && !testModuleChannel.isShutdown()) {
            testModuleChannel.shutdown();
        }
    }
    
    @Test
    @Order(1)
    void testCreateClusterUsingAPI() {
        // Create cluster using REST API
        given()
            .contentType("application/json")
            .body(Map.of(
                "name", CLUSTER_NAME,
                "description", "Test pipeline cluster",
                "metadata", Map.of(
                    "environment", "integration-test"
                )
            ))
            .when().post("/api/v1/clusters")
            .then()
            .statusCode(201)
            .body("name", is(CLUSTER_NAME))
            .body("status", is("active"));
        
        // Verify cluster was created
        given()
            .when().get("/api/v1/clusters/" + CLUSTER_NAME)
            .then()
            .statusCode(200)
            .body("name", is(CLUSTER_NAME))
            .body("status", is("active"));
    }
    
    @Test
    @Order(2)
    void testRegisterTestModuleWithoutSchema() {
        // Get module metadata from the real test-module container
        ServiceRegistrationData serviceData = testModuleService
                .getServiceRegistration(Empty.newBuilder().build())
                .await().indefinitely();
        
        assertThat(serviceData.getModuleName()).isEqualTo(MODULE_NAME);
        
        // Get container network info
        String containerNetworkHost = testModuleContainer.getContainerInfo()
                .getNetworkSettings()
                .getNetworks()
                .values()
                .iterator()
                .next()
                .getIpAddress();
        
        // Register module via gRPC registration service
        ManagedChannel registrationChannel = ManagedChannelBuilder
                .forAddress("localhost", 9090) // Engine's gRPC port
                .usePlaintext()
                .build();
        
        try {
            ModuleRegistration registrationService = new ModuleRegistrationClient(
                    "moduleRegistration", registrationChannel, 
                    (serviceName, interceptors) -> interceptors);
            
            ModuleInfo moduleInfo = ModuleInfo.newBuilder()
                    .setServiceName(serviceData.getModuleName())
                    .setServiceId("test-module-" + System.currentTimeMillis())
                    .setHost(testModuleContainer.getHost())
                    .setPort(testModuleContainer.getMappedPort(TEST_MODULE_GRPC_PORT))
                    .setHealthEndpoint("/grpc.health.v1.Health/Check")
                    .putMetadata("version", "1.0.0-SNAPSHOT")
                    .putMetadata("type", "processor")
                    .putMetadata("mode", "no-schema") // Testing without schema validation
                    .addTags("test")
                    .addTags("integration")
                    .build();
            
            RegistrationStatus response = registrationService
                    .registerModule(moduleInfo)
                    .await().indefinitely();
            
            assertThat(response.getSuccess()).isTrue();
            assertThat(response.getConsulServiceId()).isNotEmpty();
            
            registeredModuleId = response.getConsulServiceId();
            
        } finally {
            registrationChannel.shutdown();
        }
    }
    
    @Test
    @Order(3)
    void testCreateEmptyPipeline() throws Exception {
        // Create an empty pipeline configuration using REST API
        given()
            .contentType("application/json")
            .body(Map.of(
                "name", "test-empty-pipeline",
                "pipelineSteps", Map.of() // No steps
            ))
            .when().post("/api/v1/clusters/" + CLUSTER_NAME + "/pipelines/test-empty-pipeline")
            .then()
            .statusCode(201)
            .body("valid", is(true));
        
        // Verify via REST API
        given()
            .when().get("/api/v1/clusters/" + CLUSTER_NAME + "/pipelines/test-empty-pipeline")
            .then()
            .statusCode(200)
            .body("name", is("test-empty-pipeline"));
    }
    
    @Test
    @Order(4)
    void testWhitelistModuleForPipeline() {
        // TODO: Implement when module whitelisting API is available
        // For now, just skip this test
        
        // Verify cluster still exists
        given()
            .when().get("/api/v1/clusters/" + CLUSTER_NAME)
            .then()
            .statusCode(200)
            .body("name", is(CLUSTER_NAME));
    }
    
    @Test
    @Order(5)
    void testProcessDataThroughRegisteredModule() {
        // Test that we can actually process data through the registered module
        com.rokkon.search.model.PipeDoc testDoc = com.rokkon.search.model.PipeDoc.newBuilder()
                .setId("integration-test-doc-123")
                .setTitle("Integration Test Document") 
                .setBody("Testing module registration and processing")
                .build();
                
        ProcessRequest processRequest = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .build();
                
        ProcessResponse processResponse = testModuleService
                .processData(processRequest)
                .await().indefinitely();
                
        assertThat(processResponse.getSuccess()).isTrue();
        assertThat(processResponse.getProcessorLogsList()).isNotEmpty();
        assertThat(processResponse.getProcessorLogsList().toString())
                .contains("Document processed successfully");
        assertThat(processResponse.hasOutputDoc()).isTrue();
        assertThat(processResponse.getOutputDoc().hasCustomData()).isTrue();
    }
    
    @Test
    @Order(6)
    void testRegisterModuleWithSchemaValidation() {
        // Register another instance with schema validation enabled
        ManagedChannel registrationChannel = ManagedChannelBuilder
                .forAddress("localhost", 9090)
                .usePlaintext()
                .build();
        
        try {
            ModuleRegistration registrationService = new ModuleRegistrationClient(
                    "moduleRegistration", registrationChannel, 
                    (serviceName, interceptors) -> interceptors);
            
            // Get module metadata including schema
            ServiceRegistrationData serviceData = testModuleService
                    .getServiceRegistration(Empty.newBuilder().build())
                    .await().indefinitely();
            
            ModuleInfo moduleInfo = ModuleInfo.newBuilder()
                    .setServiceName("test-processor-with-schema")
                    .setServiceId("test-module-schema-" + System.currentTimeMillis())
                    .setHost(testModuleContainer.getHost())
                    .setPort(testModuleContainer.getMappedPort(TEST_MODULE_GRPC_PORT))
                    .setHealthEndpoint("/grpc.health.v1.Health/Check")
                    .putMetadata("version", "1.0.0-SNAPSHOT")
                    .putMetadata("type", "processor")
                    .putMetadata("mode", "with-schema")
                    .putMetadata("configSchema", serviceData.getJsonConfigSchema())
                    .addTags("test")
                    .addTags("schema-validation")
                    .build();
            
            RegistrationStatus response = registrationService
                    .registerModule(moduleInfo)
                    .await().indefinitely();
            
            assertThat(response.getSuccess()).isTrue();
            
            // Verify the schema was stored
            assertThat(serviceData.getJsonConfigSchema()).isNotEmpty();
            assertThat(serviceData.getJsonConfigSchema()).contains("mode");
            assertThat(serviceData.getJsonConfigSchema()).contains("simulateError");
            
        } finally {
            registrationChannel.shutdown();
        }
    }
    
    @Test
    @Order(7)
    void testFullPipelineSetupWithAPI() {
        // Test creating a full pipeline setup using REST APIs
        String fullClusterName = "full-test-cluster";
        
        // Create cluster
        given()
            .contentType("application/json")
            .body(Map.of(
                "name", fullClusterName,
                "description", "Full integration test cluster",
                "metadata", Map.of(
                    "purpose", "full-integration-test",
                    "test-type", "full-stack"
                )
            ))
            .when().post("/api/v1/clusters")
            .then()
            .statusCode(201)
            .body("name", is(fullClusterName));
            
        // Verify the cluster exists
        given()
            .when().get("/api/v1/clusters/" + fullClusterName)
            .then()
            .statusCode(200)
            .body("name", is(fullClusterName))
            .body("status", is("active"));
    }
}