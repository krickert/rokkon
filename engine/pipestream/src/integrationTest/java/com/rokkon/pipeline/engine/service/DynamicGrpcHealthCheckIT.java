package com.rokkon.pipeline.engine.service;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rokkon.search.sdk.PipeStepProcessorGrpc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.RegistrationRequest;
import com.rokkon.search.sdk.ServiceRegistrationResponse;
import com.rokkon.search.model.PipeDoc;
import com.google.protobuf.Timestamp;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Black-box integration test that verifies the engine can work with dynamic gRPC services.
 * This test verifies:
 * 1. The engine starts successfully
 * 2. Health endpoints work
 * 3. Module dashboard endpoint works (which uses service discovery internally)
 */
@QuarkusIntegrationTest
@QuarkusTestResource(GrpcServiceTestResource.class)
public class DynamicGrpcHealthCheckIT {
    
    private static final Logger LOG = LoggerFactory.getLogger(DynamicGrpcHealthCheckIT.class);
    
    @Test
    void testEngineHealthWithDynamicGrpc() {
        // Verify the engine is running and healthy
        given()
            .when()
            .get("/q/health")
            .then()
            .statusCode(200)
            .body("status", is("UP"));
        
        LOG.info("Engine health check passed");
    }
    
    @Test
    void testModuleDashboard() {
        // This endpoint uses the module discovery service internally
        given()
            .when()
            .get("/api/v1/modules/dashboard")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON);
        
        LOG.info("Module dashboard endpoint works");
    }
    
    @Test
    void testPipelineDeploymentWithGrpcService() {
        String pipelineId = "health-check-pipeline";
        String clusterId = "default";
        
        // Create a simple pipeline config that references a gRPC service
        String pipelineConfig = """
            {
                "pipelineName": "Health Check Pipeline",
                "description": "Verifies gRPC service configuration",
                "steps": {
                    "test-step": {
                        "stepName": "test-step",
                        "stepType": "PIPELINE",
                        "description": "Test step with gRPC service",
                        "processorInfo": {
                            "grpcServiceName": "%s"
                        },
                        "outputRouting": {
                            "routes": {}
                        }
                    }
                }
            }
            """.formatted(GrpcServiceTestResource.SERVICE_NAME);
        
        // Deploy the pipeline - this validates the configuration
        given()
            .contentType(ContentType.JSON)
            .body(pipelineConfig)
            .when()
            .post("/api/v1/clusters/{clusterName}/pipelines/{pipelineId}", clusterId, pipelineId)
            .then()
            .statusCode(201);
        
        LOG.info("Successfully deployed pipeline with gRPC service reference");
        
        // Verify we can retrieve it
        given()
            .when()
            .get("/api/v1/clusters/{clusterName}/pipelines/{pipelineId}", clusterId, pipelineId)
            .then()
            .statusCode(200)
            .body("pipelineName", is("Health Check Pipeline"));
        
        // Clean up
        given()
            .when()
            .delete("/api/v1/clusters/{clusterName}/pipelines/{pipelineId}", clusterId, pipelineId)
            .then()
            .statusCode(204);
    }
    
    /**
     * Simple gRPC service implementation for testing.
     * Tracks how many times it's called to verify the engine can reach it.
     */
    public static class TestGrpcService extends PipeStepProcessorGrpc.PipeStepProcessorImplBase {
        
        private static final AtomicInteger callCount = new AtomicInteger(0);
        
        @Override
        public void processData(ProcessRequest request, StreamObserver<ProcessResponse> responseObserver) {
            int count = callCount.incrementAndGet();
            LOG.info("Test gRPC service called {} times, processing doc: {}", 
                count, request.getDocument().getId());
            
            // Echo back the document with a timestamp
            PipeDoc outputDoc = PipeDoc.newBuilder()
                .setId(request.getDocument().getId())
                .setBody("Processed: " + request.getDocument().getBody())
                .setProcessedDate(Timestamp.newBuilder()
                    .setSeconds(Instant.now().getEpochSecond())
                    .build())
                .build();
            
            ProcessResponse response = ProcessResponse.newBuilder()
                .setSuccess(true)
                .setOutputDoc(outputDoc)
                .addProcessorLogs("Processed by test gRPC service, call #" + count)
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        @Override
        public void getServiceRegistration(RegistrationRequest request, 
                                         StreamObserver<ServiceRegistrationResponse> responseObserver) {
            ServiceRegistrationResponse response = ServiceRegistrationResponse.newBuilder()
                .setModuleName(GrpcServiceTestResource.SERVICE_NAME)
                .setVersion("1.0.0")
                .setDisplayName("Integration Test Processor")
                .setDescription("Test processor for health check")
                .setHealthCheckPassed(true)
                .setHealthCheckMessage("Healthy - " + callCount.get() + " calls processed")
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        public static int getCallCount() {
            return callCount.get();
        }
    }
}