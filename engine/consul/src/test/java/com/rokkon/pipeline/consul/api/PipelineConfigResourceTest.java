package com.rokkon.pipeline.consul.api;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PipelineConfigResourceTest {

    private PipelineConfig testConfig;

    @BeforeEach
    void setup() {
        // Create test pipeline config
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );

        // Create a Kafka transport config for the output
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test-pipeline.test-step.input", // topic
            null, // default partitionKeyField
            null, // default compressionType
            null, // default batchSize
            null, // default lingerMs
            null  // default kafkaProducerProperties
        );

        // Create an output target for the sink step
        PipelineStepConfig.OutputTarget outputTarget = new PipelineStepConfig.OutputTarget(
            "sink-step", // targetStepName
            TransportType.KAFKA, // transportType
            null, // grpcTransport (not needed for KAFKA)
            kafkaTransport
        );

        // Create the step with the output
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step",
            StepType.INITIAL_PIPELINE,
            "Test step description",
            null, // customConfigSchemaId
            null, // customConfig
            Map.of("default", outputTarget), // outputs
            null, // maxRetries
            null, // retryBackoffMs
            null, // maxRetryBackoffMs
            null, // retryBackoffMultiplier
            null, // stepTimeoutMs
            processorInfo
        );

        // Create a valid pipeline config with INITIAL_PIPELINE and SINK
        PipelineStepConfig.ProcessorInfo sourceProcessor = new PipelineStepConfig.ProcessorInfo(
            "source-service", null
        );

        // Create a Kafka transport config for the source step output
        KafkaTransportConfig sourceKafkaTransport = new KafkaTransportConfig(
            "test-pipeline.source-step.input", // topic
            null, // default partitionKeyField
            null, // default compressionType
            null, // default batchSize
            null, // default lingerMs
            null  // default kafkaProducerProperties
        );

        // Create an output target for the source step
        PipelineStepConfig.OutputTarget sourceOutputTarget = new PipelineStepConfig.OutputTarget(
            "sink-step", // targetStepName
            TransportType.KAFKA, // transportType
            null, // grpcTransport (not needed for KAFKA)
            sourceKafkaTransport
        );

        // Create the source step with the output
        PipelineStepConfig sourceStep = new PipelineStepConfig(
            "source-step",
            StepType.INITIAL_PIPELINE,
            "Source step description",
            null, // customConfigSchemaId
            null, // customConfig
            Map.of("default", sourceOutputTarget), // outputs
            null, // maxRetries
            null, // retryBackoffMs
            null, // maxRetryBackoffMs
            null, // retryBackoffMultiplier
            null, // stepTimeoutMs
            sourceProcessor
        );

        PipelineStepConfig.ProcessorInfo sinkProcessor = new PipelineStepConfig.ProcessorInfo(
            "sink-service", null
        );
        PipelineStepConfig sinkStep = new PipelineStepConfig(
            "sink-step",
            StepType.SINK,
            sinkProcessor
        );

        testConfig = new PipelineConfig(
            "test-pipeline",
            Map.of(
                "source-step", sourceStep,
                "sink-step", sinkStep
            )
        );
    }

    @Test
    @Disabled("Test is failing with QuarkusBindException due to port binding issues")
    @Order(1)
    void testCreatePipeline() {
        // TODO: This test is likely to fail with a QuarkusBindException
        // This is due to port binding issues that will be addressed in future integration work
        given()
            .contentType(ContentType.JSON)
            .body(testConfig)
            .when()
            .post("/api/v1/clusters/test-cluster/pipelines/test-pipeline")
            .then()
            .statusCode(201)
            .body("success", equalTo(true))
            .body("message", equalTo("Pipeline created successfully"));
    }

    @Test
    @Disabled("Test depends on testCreatePipeline and fails due to port binding issues")
    @Order(2)
    void testGetPipeline() {
        // TODO: This test depends on testCreatePipeline passing
        // It will fail due to the same port binding issues
        given()
            .when()
            .get("/api/v1/clusters/test-cluster/pipelines/test-pipeline")
            .then()
            .statusCode(200)
            .body("name", equalTo("test-pipeline"))
            .body("pipelineSteps", hasKey("source-step"))
            .body("pipelineSteps", hasKey("sink-step"));
    }

    @Test
    @Disabled("Test depends on testCreatePipeline and fails due to port binding issues")
    @Order(3)
    void testUpdatePipeline() {
        // TODO: This test depends on testCreatePipeline passing
        // It will fail due to the same port binding issues

        // Add a middle step
        PipelineStepConfig.ProcessorInfo middleProcessor = new PipelineStepConfig.ProcessorInfo(
            "middle-service", null
        );
        PipelineStepConfig middleStep = new PipelineStepConfig(
            "middle-step",
            StepType.PIPELINE,
            middleProcessor
        );

        PipelineConfig updatedConfig = new PipelineConfig(
            "test-pipeline",
            Map.of(
                "source-step", testConfig.pipelineSteps().get("source-step"),
                "middle-step", middleStep,
                "sink-step", testConfig.pipelineSteps().get("sink-step")
            )
        );

        given()
            .contentType(ContentType.JSON)
            .body(updatedConfig)
            .when()
            .put("/api/v1/clusters/test-cluster/pipelines/test-pipeline")
            .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("message", equalTo("Pipeline updated successfully"));

        // Verify the update
        given()
            .when()
            .get("/api/v1/clusters/test-cluster/pipelines/test-pipeline")
            .then()
            .statusCode(200)
            .body("pipelineSteps", hasKey("middle-step"));
    }

    @Test
    @Disabled("Test depends on testCreatePipeline and fails due to port binding issues")
    @Order(4)
    void testDeletePipeline() {
        // TODO: This test depends on testCreatePipeline passing
        // It will fail due to the same port binding issues
        given()
            .when()
            .delete("/api/v1/clusters/test-cluster/pipelines/test-pipeline")
            .then()
            .statusCode(204);

        // Verify deletion
        given()
            .when()
            .get("/api/v1/clusters/test-cluster/pipelines/test-pipeline")
            .then()
            .statusCode(404)
            .body("success", equalTo(false))
            .body("message", equalTo("Pipeline not found"));
    }

    @Test
    @Disabled("Test is failing with QuarkusBindException due to port binding issues")
    void testCreateInvalidPipeline() {
        // TODO: This test is likely to fail with a QuarkusBindException
        // This is due to port binding issues that will be addressed in future integration work

        // Pipeline without SINK step
        PipelineStepConfig.ProcessorInfo processor = new PipelineStepConfig.ProcessorInfo(
            "service", null
        );
        PipelineStepConfig step = new PipelineStepConfig(
            "only-step",
            StepType.PIPELINE,
            processor
        );

        PipelineConfig invalidConfig = new PipelineConfig(
            "invalid-pipeline",
            Map.of("only-step", step)
        );

        given()
            .contentType(ContentType.JSON)
            .body(invalidConfig)
            .when()
            .post("/api/v1/clusters/test-cluster/pipelines/invalid-pipeline")
            .then()
            .statusCode(400)
            .body("success", equalTo(false))
            .body("errors", hasSize(greaterThan(0)));
    }

    @Test
    @Disabled("Test is failing with QuarkusBindException due to port binding issues")
    void testConcurrentPipelineCreation() {
        // TODO: This test is likely to fail with a QuarkusBindException
        // This is due to port binding issues that will be addressed in future integration work

        // Try to create the same pipeline twice
        given()
            .contentType(ContentType.JSON)
            .body(testConfig)
            .when()
            .post("/api/v1/clusters/test-cluster/pipelines/concurrent-test")
            .then()
            .statusCode(201);

        // Second creation should fail
        given()
            .contentType(ContentType.JSON)
            .body(testConfig)
            .when()
            .post("/api/v1/clusters/test-cluster/pipelines/concurrent-test")
            .then()
            .statusCode(409)
            .body("success", equalTo(false))
            .body("errors", hasItem(containsString("already exists")));

        // Cleanup
        given()
            .when()
            .delete("/api/v1/clusters/test-cluster/pipelines/concurrent-test")
            .then()
            .statusCode(204);
    }

    @Test
    @Disabled("This test is failing due to port binding issues that will be addressed in future integration work")
    void testOpenApiEndpoint() {
        given()
            .when()
            .get("/openapi")
            .then()
            .statusCode(200)
            .body("openapi", notNullValue())
            .body("info.title", equalTo("Rokkon Pipeline Configuration API"));
    }

    @Test
    @Disabled("This test is failing due to port binding issues that will be addressed in future integration work")
    void testSwaggerUIEndpoint() {
        given()
            .when()
            .get("/swagger-ui/")
            .then()
            .statusCode(200);
    }

    @Test 
    @Disabled("This test is failing due to port binding issues that will be addressed in future integration work")
    void testHealthEndpoint() {
        given()
            .when()
            .get("/q/health")
            .then()
            .statusCode(200);
    }
}
