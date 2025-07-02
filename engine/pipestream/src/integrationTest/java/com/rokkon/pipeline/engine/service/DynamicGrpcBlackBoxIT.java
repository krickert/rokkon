package com.rokkon.pipeline.engine.service;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.ServiceOptions;
import io.vertx.ext.consul.CheckOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.rokkon.search.sdk.PipeStepProcessorGrpc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.RegistrationRequest;
import com.rokkon.search.sdk.ServiceRegistrationResponse;
import com.rokkon.search.model.PipeDoc;
import com.google.protobuf.Timestamp;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Black-box integration test that verifies dynamic gRPC service discovery.
 * This test:
 * 1. Starts a real gRPC server implementing PipeStepProcessor
 * 2. Registers it in Consul (using testcontainers)
 * 3. Deploys a pipeline configuration to the engine that uses the service
 * 4. Verifies the engine can discover and call the service
 */
@Testcontainers
@QuarkusIntegrationTest
public class DynamicGrpcBlackBoxIT {
    
    private static final Logger LOG = LoggerFactory.getLogger(DynamicGrpcBlackBoxIT.class);
    private static final String TEST_SERVICE_NAME = "blackbox-test-processor";
    private static final AtomicReference<String> lastProcessedDocId = new AtomicReference<>();
    
    @Container
    static ConsulContainer consulContainer = new ConsulContainer(DockerImageName.parse("hashicorp/consul:latest"))
        .withExposedPorts(8500);
    
    private static Server grpcServer;
    private static ConsulClient consulClient;
    private static Vertx vertx;
    private static int grpcPort;
    
    @BeforeAll
    static void setup() throws IOException {
        // Start Consul container and wait for it
        consulContainer.start();
        
        // Create Vertx instance
        vertx = Vertx.vertx();
        
        // Find a free port for gRPC server
        try (ServerSocket socket = new ServerSocket(0)) {
            grpcPort = socket.getLocalPort();
        }
        
        // Start test gRPC server with our test implementation
        grpcServer = ServerBuilder.forPort(grpcPort)
            .addService(new TestPipeStepProcessorService())
            .build()
            .start();
        
        LOG.info("Started test gRPC server on port {}", grpcPort);
        
        // Create Consul client
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consulContainer.getHost())
            .setPort(consulContainer.getMappedPort(8500));
        
        consulClient = ConsulClient.create(vertx, options);
        
        // Register service in Consul
        registerServiceInConsul();
        
        // Wait for service to be discoverable
        waitForServiceDiscovery();
        
        // Configure the engine to use our Consul instance
        System.setProperty("quarkus.consul-config.agent.host-port", 
            consulContainer.getHost() + ":" + consulContainer.getMappedPort(8500));
    }
    
    @AfterAll
    static void cleanup() {
        if (grpcServer != null) {
            grpcServer.shutdown();
        }
        
        if (consulClient != null) {
            consulClient.deregisterService(TEST_SERVICE_NAME + "-" + grpcPort)
                .toCompletionStage()
                .toCompletableFuture()
                .join();
            consulClient.close();
        }
        
        if (vertx != null) {
            vertx.close();
        }
    }
    
    @Test
    void testDynamicGrpcDiscoveryThroughPipeline() {
        // Create a pipeline configuration that uses our test service
        String pipelineId = "test-pipeline-" + System.currentTimeMillis();
        String clusterId = "default";
        
        JsonObject pipelineConfig = new JsonObject()
            .put("pipelineName", "Dynamic gRPC Test Pipeline")
            .put("description", "Tests dynamic gRPC service discovery")
            .put("steps", new JsonObject()
                .put("grpc-test-step", new JsonObject()
                    .put("stepName", "grpc-test-step")
                    .put("stepType", "PIPELINE")
                    .put("description", "Calls our test gRPC service")
                    .put("processorInfo", new JsonObject()
                        .put("grpcServiceName", TEST_SERVICE_NAME)
                    )
                    .put("outputRouting", new JsonObject()
                        .put("routes", new JsonObject())
                    )
                )
            );
        
        // Deploy the pipeline
        given()
            .contentType(ContentType.JSON)
            .body(pipelineConfig.encode())
            .when()
            .post("/api/v1/clusters/{clusterName}/pipelines/{pipelineId}", clusterId, pipelineId)
            .then()
            .statusCode(201);
        
        LOG.info("Deployed pipeline {} with gRPC service {}", pipelineId, TEST_SERVICE_NAME);
        
        // Now execute the pipeline with a test document
        String docId = UUID.randomUUID().toString();
        JsonObject testDocument = new JsonObject()
            .put("id", docId)
            .put("body", "Test document for dynamic gRPC discovery")
            .put("source_uri", "test://blackbox-test");
        
        // Execute pipeline (this would be through your pipeline execution API)
        // For now, let's verify the service is registered and healthy
        given()
            .when()
            .get("/api/v1/modules/dashboard")
            .then()
            .statusCode(200);
        
        // Wait a bit and check if our service processed anything
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofSeconds(1))
            .until(() -> lastProcessedDocId.get() != null);
        
        // Verify our service was called
        assertThat(lastProcessedDocId.get()).isNotNull();
        LOG.info("Successfully processed document {} through dynamic gRPC discovery", lastProcessedDocId.get());
        
        // Clean up - delete the pipeline
        given()
            .when()
            .delete("/api/v1/clusters/{clusterName}/pipelines/{pipelineId}", clusterId, pipelineId)
            .then()
            .statusCode(204);
    }
    
    private static void registerServiceInConsul() {
        ServiceOptions serviceOptions = new ServiceOptions()
            .setName(TEST_SERVICE_NAME)
            .setId(TEST_SERVICE_NAME + "-" + grpcPort)
            .setAddress("host.docker.internal") // Use host.docker.internal for Docker to access host
            .setPort(grpcPort)
            .setTags(java.util.List.of("grpc", "test", "blackbox"))
            .setCheckOptions(new CheckOptions()
                .setTcp("host.docker.internal:" + grpcPort)
                .setInterval("10s")
            );
        
        consulClient.registerService(serviceOptions)
            .toCompletionStage()
            .toCompletableFuture()
            .join();
        
        LOG.info("Registered service {} in Consul on port {}", TEST_SERVICE_NAME, grpcPort);
    }
    
    private static void waitForServiceDiscovery() {
        await().atMost(Duration.ofSeconds(10))
            .until(() -> {
                var future = consulClient.healthServiceNodes(TEST_SERVICE_NAME, true)
                    .toCompletionStage()
                    .toCompletableFuture();
                
                var serviceList = future.join();
                return serviceList != null && 
                       serviceList.getList() != null && 
                       !serviceList.getList().isEmpty();
            });
        
        LOG.info("Service {} is now discoverable in Consul", TEST_SERVICE_NAME);
    }
    
    /**
     * Simple test implementation of PipeStepProcessor that records what it processes.
     */
    static class TestPipeStepProcessorService extends PipeStepProcessorGrpc.PipeStepProcessorImplBase {
        
        @Override
        public void processData(ProcessRequest request, StreamObserver<ProcessResponse> responseObserver) {
            LOG.info("Processing document: {}", request.getDocument().getId());
            
            // Record that we processed this document
            lastProcessedDocId.set(request.getDocument().getId());
            
            // Create response with modified document
            PipeDoc outputDoc = PipeDoc.newBuilder()
                .setId(request.getDocument().getId())
                .setBody("PROCESSED BY BLACKBOX TEST: " + request.getDocument().getBody())
                .setSourceUri(request.getDocument().getSourceUri())
                .setProcessedDate(Timestamp.newBuilder()
                    .setSeconds(Instant.now().getEpochSecond())
                    .build())
                .build();
            
            ProcessResponse response = ProcessResponse.newBuilder()
                .setSuccess(true)
                .setOutputDoc(outputDoc)
                .addProcessorLogs("Processed by black-box test gRPC service")
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        @Override
        public void getServiceRegistration(RegistrationRequest request, 
                                         StreamObserver<ServiceRegistrationResponse> responseObserver) {
            ServiceRegistrationResponse response = ServiceRegistrationResponse.newBuilder()
                .setModuleName(TEST_SERVICE_NAME)
                .setVersion("1.0.0")
                .setDisplayName("Black-box Test Processor")
                .setDescription("Test processor for integration testing")
                .setHealthCheckPassed(true)
                .setHealthCheckMessage("Healthy")
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}