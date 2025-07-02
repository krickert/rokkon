package com.rokkon.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import com.rokkon.pipeline.config.model.*;
import com.rokkon.search.model.ActionType;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.test.data.ProtobufTestDataHelper;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.KeyValue;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live integration test for PipelineExecutor using Testcontainers with Docker Compose.
 * This test spins up the entire pipeline infrastructure and tests end-to-end execution.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PipelineExecutorLiveIT {
    
    private static final Logger LOG = LoggerFactory.getLogger(PipelineExecutorLiveIT.class);
    private static final String COMPOSE_FILE = "docker-compose-test.yml";
    private static final int ENGINE_HTTP_PORT = 38082;
    private static final int ENGINE_GRPC_PORT = 48082;
    private static final int CONSUL_PORT = 8500;
    
    private Vertx vertx;
    private ConsulClient consulClient;
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private ProtobufTestDataHelper testDataHelper;
    
    @Container
    static ComposeContainer environment = new ComposeContainer(
            new File(PipelineExecutorLiveIT.class.getClassLoader()
                    .getResource(COMPOSE_FILE).getFile()))
            .withExposedService("consul-server", CONSUL_PORT, 
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(60)))
            .withExposedService("pipeline-engine", ENGINE_HTTP_PORT,
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(120)))
            .withExposedService("echo-module", 39091,
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(120)))
            .withExposedService("test-module", 39092,
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(120)))
            // Expose debug ports for live debugging
            .withExposedService("pipeline-engine", 5005)
            .withExposedService("echo-module", 5006)
            .withExposedService("test-module", 5007)
            .withLocalCompose(true);
    
    @BeforeAll
    void setUp() {
        vertx = Vertx.vertx();
        objectMapper = new ObjectMapper();
        testDataHelper = new ProtobufTestDataHelper();
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        // Setup Consul client
        String consulHost = environment.getServiceHost("consul-server", CONSUL_PORT);
        Integer consulPort = environment.getServicePort("consul-server", CONSUL_PORT);
        
        ConsulClientOptions options = new ConsulClientOptions()
                .setHost(consulHost)
                .setPort(consulPort);
        
        consulClient = ConsulClient.create(vertx, options);
        
        LOG.info("=== Test Environment Started ===");
        LOG.info("Consul UI: http://{}:{}", consulHost, consulPort);
        LOG.info("Engine HTTP: http://{}:{}", 
                environment.getServiceHost("pipeline-engine", ENGINE_HTTP_PORT),
                environment.getServicePort("pipeline-engine", ENGINE_HTTP_PORT));
        LOG.info("Engine Debug: {}:{}", 
                environment.getServiceHost("pipeline-engine", 5005),
                environment.getServicePort("pipeline-engine", 5005));
        LOG.info("Echo Debug: {}:{}", 
                environment.getServiceHost("echo-module", 5006),
                environment.getServicePort("echo-module", 5006));
        LOG.info("Test Module Debug: {}:{}", 
                environment.getServiceHost("test-module", 5007),
                environment.getServicePort("test-module", 5007));
        LOG.info("===============================");
        
        // Verify Consul configuration was seeded
        verifyConsulConfiguration();
    }
    
    @AfterAll
    void tearDown() {
        if (consulClient != null) {
            consulClient.close();
        }
        if (vertx != null) {
            vertx.close();
        }
    }
    
    @Test
    @Order(1)
    void testEnvironmentHealthy() throws Exception {
        // Test Consul is healthy
        String consulUrl = String.format("http://%s:%d/v1/agent/self",
                environment.getServiceHost("consul-server", CONSUL_PORT),
                environment.getServicePort("consul-server", CONSUL_PORT));
        
        HttpRequest consulRequest = HttpRequest.newBuilder()
                .uri(URI.create(consulUrl))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        
        HttpResponse<String> consulResponse = httpClient.send(consulRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, consulResponse.statusCode(), "Consul should be healthy");
        
        // Test Engine is healthy
        String engineUrl = String.format("http://%s:%d/q/health",
                environment.getServiceHost("pipeline-engine", ENGINE_HTTP_PORT),
                environment.getServicePort("pipeline-engine", ENGINE_HTTP_PORT));
        
        HttpRequest engineRequest = HttpRequest.newBuilder()
                .uri(URI.create(engineUrl))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        
        HttpResponse<String> engineResponse = httpClient.send(engineRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, engineResponse.statusCode(), "Engine should be healthy");
    }
    
    @Test
    @Order(2)
    void testRegisterModulesInConsul() throws Exception {
        // Register echo module in Consul
        registerModuleInConsul("echo", "echo-module", 49091);
        
        // Register test module in Consul  
        registerModuleInConsul("test", "test-module", 49092);
        
        // Verify modules are registered
        Thread.sleep(2000); // Give Consul time to propagate
        
        CompletableFuture<List<String>> servicesFuture = new CompletableFuture<>();
        consulClient.catalogServices(res -> {
            if (res.succeeded()) {
                List<String> services = new ArrayList<>();
                res.result().getList().forEach(service -> services.add(service.getName()));
                servicesFuture.complete(services);
            } else {
                servicesFuture.completeExceptionally(res.cause());
            }
        });
        
        List<String> services = servicesFuture.get(10, TimeUnit.SECONDS);
        assertTrue(services.contains("echo"), "Echo module should be registered");
        assertTrue(services.contains("test"), "Test module should be registered");
    }
    
    @Test
    @Order(3)
    void testCreatePipelineConfiguration() throws Exception {
        // Create a simple echo -> test pipeline
        Map<String, PipelineStepConfig> steps = new HashMap<>();
        
        // Step 1: Echo module
        PipelineStepConfig echoStep = new PipelineStepConfig(
                "echo-step",
                StepType.INITIAL_PIPELINE,
                "Echo the input",
                null,
                null,
                null,
                Map.of("output", new PipelineStepConfig.OutputTarget(
                        "test-step",
                        TransportType.GRPC,
                        new GrpcTransportConfig("test", null),
                        null
                )),
                0,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo("echo", null)
        );
        steps.put("echo-step", echoStep);
        
        // Step 2: Test module
        PipelineStepConfig testStep = new PipelineStepConfig(
                "test-step",
                StepType.SINK,
                "Test the output",
                null,
                null,
                null,
                null, // No outputs for SINK
                0,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo("test", null)
        );
        steps.put("test-step", testStep);
        
        PipelineConfig pipeline = new PipelineConfig("echo-test-pipeline", steps);
        
        // Store pipeline configuration in Consul
        String pipelineJson = objectMapper.writeValueAsString(pipeline);
        String key = "clusters/test-cluster/pipelines/echo-test-pipeline";
        
        CompletableFuture<Boolean> putFuture = new CompletableFuture<>();
        consulClient.putValue(key, pipelineJson, res -> {
            putFuture.complete(res.succeeded());
        });
        
        assertTrue(putFuture.get(10, TimeUnit.SECONDS), "Pipeline should be stored in Consul");
        
        // Verify pipeline is stored
        CompletableFuture<String> getFuture = new CompletableFuture<>();
        consulClient.getValue(key, res -> {
            if (res.succeeded() && res.result() != null) {
                getFuture.complete(res.result().getValue());
            } else {
                getFuture.completeExceptionally(new RuntimeException("Failed to get pipeline"));
            }
        });
        
        String storedPipeline = getFuture.get(10, TimeUnit.SECONDS);
        assertNotNull(storedPipeline, "Pipeline should be retrievable from Consul");
    }
    
    @Test
    @Order(4)
    void testExecutePipeline() throws Exception {
        // Get a test document from TestDataHelper
        Collection<PipeDoc> testDocs = testDataHelper.getPipeDocuments();
        assertFalse(testDocs.isEmpty(), "Should have test documents");
        
        PipeDoc testDoc = testDocs.iterator().next();
        LOG.info("Using test document: {}", testDoc.getId());
        
        // Create pipeline execution request
        String engineUrl = String.format("http://%s:%d/api/v1/engine/execute",
                environment.getServiceHost("pipeline-engine", ENGINE_HTTP_PORT),
                environment.getServicePort("pipeline-engine", ENGINE_HTTP_PORT));
        
        // Convert PipeDoc to JSON for the request
        JsonFormat.Printer printer = JsonFormat.printer();
        String docJson = printer.print(testDoc);
        
        JsonObject requestBody = new JsonObject()
                .put("pipelineName", "echo-test-pipeline")
                .put("document", new JsonObject(docJson))
                .put("actionType", ActionType.CREATE.name());
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(engineUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.encode()))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode(), "Pipeline execution should succeed");
        
        // Parse response
        JsonObject responseJson = new JsonObject(response.body());
        assertEquals("SUCCESS", responseJson.getString("status"), "Execution should be successful");
        assertNotNull(responseJson.getString("streamId"), "Should have stream ID");
        
        LOG.info("Pipeline execution completed successfully: {}", responseJson.encode());
    }
    
    @Test
    @Order(5)
    void testMultipleDocumentExecution() throws Exception {
        // Execute pipeline with multiple documents
        Collection<PipeDoc> testDocs = testDataHelper.getPipeDocuments();
        int docCount = Math.min(5, testDocs.size()); // Test with up to 5 documents
        
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        JsonFormat.Printer printer = JsonFormat.printer();
        
        String engineUrl = String.format("http://%s:%d/api/v1/engine/execute",
                environment.getServiceHost("pipeline-engine", ENGINE_HTTP_PORT),
                environment.getServicePort("pipeline-engine", ENGINE_HTTP_PORT));
        
        int i = 0;
        for (PipeDoc doc : testDocs) {
            if (i++ >= docCount) break;
            
            String docJson = printer.print(doc);
            JsonObject requestBody = new JsonObject()
                    .put("pipelineName", "echo-test-pipeline")
                    .put("document", new JsonObject(docJson))
                    .put("actionType", ActionType.CREATE.name());
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(engineUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.encode()))
                    .build();
            
            futures.add(httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
        }
        
        // Wait for all executions to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        
        // Verify all succeeded
        for (CompletableFuture<HttpResponse<String>> future : futures) {
            HttpResponse<String> response = future.get();
            assertEquals(200, response.statusCode(), "Each execution should succeed");
            
            JsonObject responseJson = new JsonObject(response.body());
            assertEquals("SUCCESS", responseJson.getString("status"), "Each execution should be successful");
        }
        
        LOG.info("Successfully executed pipeline with {} documents", docCount);
    }
    
    private void registerModuleInConsul(String serviceName, String serviceId, int grpcPort) throws Exception {
        // Register service in Consul
        String consulUrl = String.format("http://%s:%d/v1/agent/service/register",
                environment.getServiceHost("consul-server", CONSUL_PORT),
                environment.getServicePort("consul-server", CONSUL_PORT));
        
        String serviceHost = environment.getServiceHost(serviceId, grpcPort);
        Integer servicePort = environment.getServicePort(serviceId, grpcPort);
        
        JsonObject service = new JsonObject()
                .put("ID", serviceId)
                .put("Name", serviceName)
                .put("Tags", new JsonObject().put("grpc", "true"))
                .put("Address", serviceHost)
                .put("Port", servicePort)
                .put("Check", new JsonObject()
                        .put("Name", "Service health check")
                        .put("TCP", serviceHost + ":" + servicePort)
                        .put("Interval", "10s")
                        .put("Timeout", "5s"));
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(consulUrl))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(service.encode()))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "Service registration should succeed");
        
        LOG.info("Registered {} in Consul at {}:{}", serviceName, serviceHost, servicePort);
    }
    
    private void verifyConsulConfiguration() {
        try {
            // Check if engine configuration exists in Consul
            String configKey = "config/rokkon-engine/application";
            CompletableFuture<String> configFuture = new CompletableFuture<>();
            
            consulClient.getValue(configKey, res -> {
                if (res.succeeded() && res.result() != null) {
                    configFuture.complete(res.result().getValue());
                } else {
                    configFuture.completeExceptionally(
                        new RuntimeException("Engine configuration not found in Consul"));
                }
            });
            
            String config = configFuture.get(10, TimeUnit.SECONDS);
            LOG.info("Verified engine configuration exists in Consul:");
            LOG.info("Config preview: {}", 
                config.substring(0, Math.min(200, config.length())) + "...");
        } catch (Exception e) {
            LOG.error("Failed to verify Consul configuration", e);
            fail("Consul configuration verification failed: " + e.getMessage());
        }
    }
}