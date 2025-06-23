package com.rokkon.pipeline.engine;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.pipeline.config.model.*;
import java.util.Collections;
import java.util.List;
import com.rokkon.pipeline.engine.test.ConsulTestResource;
import com.rokkon.search.grpc.ModuleInfo;
import com.rokkon.search.grpc.ModuleRegistration;
import com.rokkon.search.grpc.ModuleRegistrationClient;
import com.rokkon.search.grpc.RegistrationStatus;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.*;
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
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * End-to-end integration test demonstrating a complete pipeline setup and execution flow.
 * This test uses real services and containers to simulate a production-like environment.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FullPipelineE2EIT {

    private static final String CLUSTER_NAME = "e2e-test-cluster";
    private static final String PIPELINE_NAME = "e2e-test-pipeline";
    private static final int TEST_MODULE_PORT = 49093;

    @Container
    static GenericContainer<?> testModule1 = new GenericContainer<>("rokkon/test-module:1.0.0-SNAPSHOT")
            .withExposedPorts(TEST_MODULE_PORT)
            .waitingFor(Wait.forLogMessage(".*Started gRPC server.*", 1))
            .withEnv("QUARKUS_LOG_LEVEL", "INFO")
            .withEnv("test.processor.name", "processor-1");

    @Container
    static GenericContainer<?> testModule2 = new GenericContainer<>("rokkon/test-module:1.0.0-SNAPSHOT")
            .withExposedPorts(TEST_MODULE_PORT)
            .waitingFor(Wait.forLogMessage(".*Started gRPC server.*", 1))
            .withEnv("QUARKUS_LOG_LEVEL", "INFO")
            .withEnv("test.processor.name", "processor-2");

    // Integration tests can't use @Inject, so we'll use REST API calls

    private static String module1Id;
    private static String module2Id;
    private static ManagedChannel registrationChannel;
    private static ModuleRegistration registrationService;

    @BeforeAll
    static void setupAll() {
        // Set up registration service channel
        registrationChannel = ManagedChannelBuilder
                .forAddress("localhost", 9090)
                .usePlaintext()
                .build();
        registrationService = new ModuleRegistrationClient(
                "moduleRegistration", registrationChannel,
                (serviceName, interceptors) -> interceptors);
    }

    @AfterAll
    static void cleanupAll() {
        if (registrationChannel != null && !registrationChannel.isShutdown()) {
            registrationChannel.shutdown();
        }
    }

    @Test
    @Order(1)
    void testCreateClusterAndVerifyServices() {
        // Create cluster using REST API
        given()
            .contentType("application/json")
            .body(Map.of(
                "name", CLUSTER_NAME,
                "description", "End-to-end test cluster",
                "metadata", Map.of(
                    "purpose", "integration-testing",
                    "managed_by", "E2E-Test"
                )
            ))
            .when().post("/api/v1/clusters")
            .then()
            .statusCode(201)
            .body("name", is(CLUSTER_NAME))
            .body("status", is("active"));

        // Verify cluster exists via REST API
        given()
            .when().get("/api/v1/clusters/" + CLUSTER_NAME)
            .then()
            .statusCode(200)
            .body("name", is(CLUSTER_NAME))
            .body("status", is("active"))
            .body("metadata.managed_by", is("E2E-Test"));
    }

    @Test
    @Order(2)
    void testRegisterMultipleModules() {
        // Register first module
        module1Id = registerModule(testModule1, "processor-1", "First processor in pipeline");
        assertThat(module1Id).isNotNull();

        // Register second module
        module2Id = registerModule(testModule2, "processor-2", "Second processor in pipeline");
        assertThat(module2Id).isNotNull();

        // Verify both modules are registered
        given()
            .when().get("/api/v1/modules")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(2));
    }

    // TODO: Uncomment when whitelisting API is available
    // @Test
    // @Order(3)
    void testWhitelistModulesForCluster() {
        // Whitelist first module
        given()
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "processor-1",
                "moduleId", module1Id,
                "serviceType", "PipeStepProcessor",
                "metadata", Map.of("whitelisted", "true")
            ))
            .when().post("/api/v1/clusters/" + CLUSTER_NAME + "/modules/whitelist")
            .then()
            .statusCode(200)
            .body("success", is(true));

        // Whitelist second module
        given()
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "processor-2",
                "moduleId", module2Id,
                "serviceType", "PipeStepProcessor",
                "metadata", Map.of("whitelisted", "true")
            ))
            .when().post("/api/v1/clusters/" + CLUSTER_NAME + "/modules/whitelist")
            .then()
            .statusCode(200)
            .body("success", is(true));

        // Verify whitelisted modules
        given()
            .when().get("/api/v1/clusters/" + CLUSTER_NAME + "/modules/whitelisted")
            .then()
            .statusCode(200)
            .body("size()", is(2));
    }

    @Test
    @Order(4)
    void testCreatePipelineWithTwoSteps() throws Exception {
        // Create pipeline configuration with two processing steps

        // Step 1: First processor with output to step2
        PipelineStepConfig step1 = new PipelineStepConfig(
                "step1",
                StepType.PIPELINE,
                "First processing step",
                null,  // customConfigSchemaId
                new PipelineStepConfig.JsonConfigOptions(
                        Map.of("mode", "test", "addMetadata", "true")
                ),
                Collections.emptyList(),  // kafkaInputs
                Map.of("default", new PipelineStepConfig.OutputTarget(
                        "step2",
                        TransportType.GRPC,
                        new GrpcTransportConfig("processor-2", Map.of("timeout", "5000")),
                        null
                )),
                0,     // maxRetries
                1000L, // retryBackoffMs
                30000L,// maxRetryBackoffMs
                2.0,   // retryBackoffMultiplier
                null,  // stepTimeoutMs
                new PipelineStepConfig.ProcessorInfo("processor-1", null)
        );

        // Step 2: Second processor - in a real pipeline, outputs would point to next step
        // For this test, we'll have step2 as the final step with no outputs
        PipelineStepConfig step2 = new PipelineStepConfig(
                "step2",
                StepType.PIPELINE,
                "Second processing step",
                null,  // customConfigSchemaId
                new PipelineStepConfig.JsonConfigOptions(
                        Map.of("mode", "validate", "requireSchema", "false")
                ),
                Collections.emptyList(),  // kafkaInputs
                Collections.emptyMap(),   // No outputs - this is the final step
                0,     // maxRetries
                1000L, // retryBackoffMs
                30000L,// maxRetryBackoffMs
                2.0,   // retryBackoffMultiplier
                null,  // stepTimeoutMs
                new PipelineStepConfig.ProcessorInfo("processor-2", null)
        );

        // Create pipeline with two steps
        PipelineConfig pipeline = new PipelineConfig(
                PIPELINE_NAME,
                Map.of(
                        "step1", step1,
                        "step2", step2
                )
        );

        // Save pipeline using REST API
        given()
            .contentType("application/json")
            .body(pipeline)
            .when().post("/api/v1/clusters/" + CLUSTER_NAME + "/pipelines/" + PIPELINE_NAME)
            .then()
            .statusCode(201)
            .body("valid", is(true));

        // Verify via REST API
        given()
            .when().get("/api/v1/clusters/" + CLUSTER_NAME + "/pipelines/" + PIPELINE_NAME)
            .then()
            .statusCode(200)
            .body("name", is(PIPELINE_NAME))
            .body("pipelineSteps.size()", is(2));
    }

    @Test
    @Order(5)
    void testProcessDocumentThroughPipeline() {
        // Connect to both modules
        ManagedChannel channel1 = ManagedChannelBuilder
                .forAddress(testModule1.getHost(), testModule1.getMappedPort(TEST_MODULE_PORT))
                .usePlaintext()
                .build();
        PipeStepProcessor processor1 = new PipeStepProcessorClient("processor1", channel1,
                (serviceName, interceptors) -> interceptors);

        ManagedChannel channel2 = ManagedChannelBuilder
                .forAddress(testModule2.getHost(), testModule2.getMappedPort(TEST_MODULE_PORT))
                .usePlaintext()
                .build();
        PipeStepProcessor processor2 = new PipeStepProcessorClient("processor2", channel2,
                (serviceName, interceptors) -> interceptors);

        try {
            // Create test document
            PipeDoc testDoc = PipeDoc.newBuilder()
                    .setId("e2e-doc-" + UUID.randomUUID())
                    .setTitle("End-to-End Test Document")
                    .setBody("This document will be processed through the full pipeline")
                    .build();

            // Process through first step
            ServiceMetadata metadata1 = ServiceMetadata.newBuilder()
                    .setPipelineName(PIPELINE_NAME)
                    .setPipeStepName("step1")
                    .setStreamId(UUID.randomUUID().toString())
                    .setCurrentHopNumber(1)
                    .build();

            ProcessConfiguration config1 = ProcessConfiguration.newBuilder()
                    .putConfigParams("mode", "test")
                    .putConfigParams("addMetadata", "true")
                    .build();

            ProcessRequest request1 = ProcessRequest.newBuilder()
                    .setDocument(testDoc)
                    .setMetadata(metadata1)
                    .setConfig(config1)
                    .build();

            ProcessResponse response1 = processor1.processData(request1)
                    .await().indefinitely();

            assertThat(response1.getSuccess()).isTrue();
            assertThat(response1.hasOutputDoc()).isTrue();
            assertThat(response1.getOutputDoc().hasCustomData()).isTrue();

            // Verify first processor added its metadata
            Struct customData1 = response1.getOutputDoc().getCustomData();
            assertThat(customData1.getFieldsMap()).containsKey("processed_by");
            assertThat(customData1.getFieldsOrThrow("processed_by").getStringValue()).isEqualTo("processor-1");

            // Process through second step with output from first
            ServiceMetadata metadata2 = ServiceMetadata.newBuilder()
                    .setPipelineName(PIPELINE_NAME)
                    .setPipeStepName("step2")
                    .setStreamId(metadata1.getStreamId())
                    .setCurrentHopNumber(2)
                    .build();

            ProcessConfiguration config2 = ProcessConfiguration.newBuilder()
                    .putConfigParams("mode", "validate")
                    .build();

            ProcessRequest request2 = ProcessRequest.newBuilder()
                    .setDocument(response1.getOutputDoc())
                    .setMetadata(metadata2)
                    .setConfig(config2)
                    .build();

            ProcessResponse response2 = processor2.processData(request2)
                    .await().indefinitely();

            assertThat(response2.getSuccess()).isTrue();
            assertThat(response2.getProcessorLogsList().toString()).contains("Schema validation passed");
            assertThat(response2.hasOutputDoc()).isTrue();

            // Verify both processors' metadata is present
            Struct customData2 = response2.getOutputDoc().getCustomData();
            assertThat(customData2.getFieldsMap()).containsKey("processed_by");
            // The second processor overwrites the processed_by field
            assertThat(customData2.getFieldsOrThrow("processed_by").getStringValue()).isEqualTo("processor-2");

            // Both processors should have added timestamps
            assertThat(customData2.getFieldsMap()).containsKey("processing_timestamp");

        } finally {
            channel1.shutdown();
            channel2.shutdown();
        }
    }

    @Test
    @Order(6)
    void testPipelineWithErrorHandling() {
        // Connect to first module
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(testModule1.getHost(), testModule1.getMappedPort(TEST_MODULE_PORT))
                .usePlaintext()
                .build();
        PipeStepProcessor processor = new PipeStepProcessorClient("processor", channel,
                (serviceName, interceptors) -> interceptors);

        try {
            // Create document that will fail validation
            PipeDoc invalidDoc = PipeDoc.newBuilder()
                    .setId("invalid-doc-" + UUID.randomUUID())
                    // Missing title and body - will fail validation
                    .build();

            // Configure processor to validate schema
            Struct configStruct = Struct.newBuilder()
                    .putFields("mode", Value.newBuilder().setStringValue("validate").build())
                    .putFields("requireSchema", Value.newBuilder().setBoolValue(true).build())
                    .build();

            ProcessRequest request = ProcessRequest.newBuilder()
                    .setDocument(invalidDoc)
                    .setConfig(ProcessConfiguration.newBuilder()
                            .setCustomJsonConfig(configStruct)
                            .build())
                    .build();

            ProcessResponse response = processor.processData(request)
                    .await().indefinitely();

            // Should fail validation
            assertThat(response.getSuccess()).isFalse();
            assertThat(response.getProcessorLogsList().toString()).contains("Schema validation failed");
            assertThat(response.hasErrorDetails()).isTrue();

            Struct errorDetails = response.getErrorDetails();
            assertThat(errorDetails.getFieldsMap()).containsKey("error_type");
            assertThat(errorDetails.getFieldsMap()).containsKey("error_message");
            assertThat(errorDetails.getFieldsOrThrow("error_message").getStringValue())
                    .contains("title is required");

        } finally {
            channel.shutdown();
        }
    }

    @Test
    @Order(7)
    void testMultiClusterPipelineConfiguration() throws Exception {
        // Create a second cluster for production workloads
        String prodClusterName = "production-cluster";

        // Create production cluster
        given()
            .contentType("application/json")
            .body(Map.of(
                "name", prodClusterName,
                "description", "Production pipeline cluster",
                "metadata", Map.of(
                    "environment", "production",
                    "sla", "99.99%"
                )
            ))
            .when().post("/api/v1/clusters")
            .then()
            .statusCode(201)
            .body("name", is(prodClusterName));

            // Create a production pipeline with different configuration
            PipelineStepConfig prodStep1 = new PipelineStepConfig(
                    "ingest",
                    StepType.PIPELINE,
                    "Production ingestion step",
                    null,
                    new PipelineStepConfig.JsonConfigOptions(
                            Map.of(
                                "mode", "production",
                                "batchSize", "1000",
                                "compressionEnabled", "true"
                            )
                    ),
                    Collections.emptyList(),
                    Map.of("default", new PipelineStepConfig.OutputTarget(
                            "transform",
                            TransportType.KAFKA,  // Using Kafka for production
                            null,
                            new KafkaTransportConfig(
                                    "prod-transform-topic",
                                    "pipedocId",  // partitionKeyField
                                    "snappy",     // compressionType
                                    65536,        // batchSize
                                    100,          // lingerMs
                                    Map.of("acks", "all", "max.in.flight.requests.per.connection", "5")
                            )
                    )),
                    3,      // More retries for production
                    1000L,
                    60000L, // Higher max backoff
                    2.0,
                    30000L, // 30 second timeout
                    new PipelineStepConfig.ProcessorInfo("processor-1", null)
            );

            PipelineStepConfig prodStep2 = new PipelineStepConfig(
                    "transform",
                    StepType.PIPELINE,
                    "Production transformation step",
                    null,
                    new PipelineStepConfig.JsonConfigOptions(
                            Map.of(
                                "mode", "production",
                                "parallelism", "4",
                                "enableMetrics", "true"
                            )
                    ),
                    List.of(new KafkaInputDefinition(
                            List.of("prod-transform-topic"),
                            "production-consumer-group",
                            Map.of("auto.offset.reset", "earliest")
                    )),
                    Collections.emptyMap(), // Final step
                    3,
                    1000L,
                    60000L,
                    2.0,
                    45000L, // 45 second timeout
                    new PipelineStepConfig.ProcessorInfo("processor-2", null)
            );

            PipelineConfig prodPipeline = new PipelineConfig(
                    "production-pipeline",
                    Map.of(
                        "ingest", prodStep1,
                        "transform", prodStep2
                    )
            );

            // Save production pipeline
            given()
                .contentType("application/json")
                .body(prodPipeline)
                .when().post("/api/v1/clusters/" + prodClusterName + "/pipelines/production-pipeline")
                .then()
                .statusCode(201)
                .body("valid", is(true));

            // Verify both clusters have their own pipelines
            given()
                .when().get("/api/v1/clusters/" + CLUSTER_NAME + "/pipelines")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));

            given()
                .when().get("/api/v1/clusters/" + prodClusterName + "/pipelines")
                .then()
                .statusCode(200)
                .body("size()", is(1));
    }

    private String registerModule(GenericContainer<?> container, String moduleName, String description) {
        // Get module service registration data
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(container.getHost(), container.getMappedPort(TEST_MODULE_PORT))
                .usePlaintext()
                .build();

        try {
            PipeStepProcessor processor = new PipeStepProcessorClient("processor", channel,
                    (serviceName, interceptors) -> interceptors);

            ServiceRegistrationResponse serviceData = processor
                    .getServiceRegistration(RegistrationRequest.newBuilder().build())
                    .await().indefinitely();

            // Register module
            ModuleInfo moduleInfo = ModuleInfo.newBuilder()
                    .setServiceName(moduleName)
                    .setServiceId(moduleName + "-" + System.currentTimeMillis())
                    .setHost(container.getHost())
                    .setPort(container.getMappedPort(TEST_MODULE_PORT))
                    .setHealthEndpoint("/grpc.health.v1.Health/Check")
                    .putMetadata("version", "1.0.0-SNAPSHOT")
                    .putMetadata("description", description)
                    .putMetadata("configSchema", serviceData.getJsonConfigSchema())
                    .addTags("e2e-test")
                    .build();

            RegistrationStatus response = registrationService
                    .registerModule(moduleInfo)
                    .await().indefinitely();

            assertThat(response.getSuccess()).isTrue();
            return response.getConsulServiceId();

        } finally {
            channel.shutdown();
        }
    }
}
