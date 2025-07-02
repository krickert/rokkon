package com.rokkon.pipeline.engine.test;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.PipeStepProcessorGrpc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.RegistrationRequest;
import com.rokkon.search.sdk.ServiceRegistrationResponse;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.consul.CheckOptions;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.ServiceOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;

/**
 * Test resource that:
 * 1. Starts Consul container with proper engine configuration
 * 2. Starts a test gRPC echo service
 * 3. Registers the service in Consul for dynamic discovery
 */
public class ConsulTestResourceWithDynamicGrpc implements QuarkusTestResourceLifecycleManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(ConsulTestResourceWithDynamicGrpc.class);
    private static final String TEST_ECHO_SERVICE = "test-echo";
    private static final AtomicInteger processedCount = new AtomicInteger(0);
    
    private ConsulContainer consulContainer;
    private Server grpcServer;
    private Vertx vertx;
    private ConsulClient consulClient;
    private int grpcPort;
    
    @Override
    public Map<String, String> start() {
        LOG.info("Starting Consul container with test gRPC service...");
        
        // Start Consul container
        consulContainer = new ConsulContainer(DockerImageName.parse("hashicorp/consul:latest"));
        consulContainer.start();
        
        String consulHost = consulContainer.getHost();
        int consulPort = consulContainer.getFirstMappedPort();
        
        // Create Vertx instance
        vertx = Vertx.vertx();
        
        // Create Consul client
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consulHost)
            .setPort(consulPort);
        consulClient = ConsulClient.create(vertx, options);
        
        // Seed required engine configuration
        seedEngineConfiguration();
        
        // Start test gRPC service
        startTestGrpcService();
        
        // Register gRPC service in Consul
        registerGrpcServiceInConsul();
        
        // Return configuration for Quarkus
        return Map.of(
            "quarkus.consul-config.agent.host-port", consulHost + ":" + consulPort,
            "consul.host", consulHost,
            "consul.port", String.valueOf(consulPort),
            "quarkus.consul-config.enabled", "true",
            "quarkus.consul-config.fail-on-missing-key", "false"
        );
    }
    
    @Override
    public void stop() {
        if (grpcServer != null) {
            LOG.info("Stopping test gRPC server");
            grpcServer.shutdown();
        }
        
        if (consulClient != null) {
            consulClient.close();
        }
        
        if (vertx != null) {
            vertx.close();
        }
        
        if (consulContainer != null) {
            LOG.info("Stopping Consul container");
            consulContainer.stop();
        }
    }
    
    private void seedEngineConfiguration() {
        try {
            // Seed application configuration required by engine
            JsonObject appConfig = new JsonObject()
                .put("rokkon", new JsonObject()
                    .put("cluster", new JsonObject()
                        .put("name", "test-cluster"))
                    .put("engine", new JsonObject()
                        .put("name", "test-engine")
                        .put("grpc-port", 48082)
                        .put("rest-port", 38082))
                    .put("modules", new JsonObject()
                        .put("whitelist", List.of("echo", "test-echo", "test-module"))
                        .put("require-whitelist", false))
                    .put("consul", new JsonObject()
                        .put("cleanup", new JsonObject()
                            .put("enabled", false))
                        .put("health", new JsonObject()
                            .put("check-interval", "10s")
                            .put("timeout", "5s")))
                );
            
            // Use execInContainer to run consul kv put
            var result = consulContainer.execInContainer(
                "consul", "kv", "put", "config/application", appConfig.encodePrettily()
            );
            
            if (result.getExitCode() == 0) {
                LOG.info("Successfully seeded application configuration");
            } else {
                LOG.error("Failed to seed configuration: {}", result.getStderr());
            }
            
            // Also seed config/rokkon-engine/application for engine-specific config
            JsonObject engineConfig = new JsonObject()
                .put("rokkon", new JsonObject()
                    .put("cluster", new JsonObject()
                        .put("name", "test-cluster"))
                    .put("engine", new JsonObject()
                        .put("name", "test-engine")));
            
            consulContainer.execInContainer(
                "consul", "kv", "put", "config/rokkon-engine/application", engineConfig.encodePrettily()
            );
            
        } catch (IOException | InterruptedException e) {
            LOG.error("Error seeding Consul configuration", e);
            throw new RuntimeException("Failed to seed Consul configuration", e);
        }
    }
    
    private void startTestGrpcService() {
        try {
            // Find a free port
            try (ServerSocket socket = new ServerSocket(0)) {
                grpcPort = socket.getLocalPort();
            }
            
            // Start gRPC server with test echo service
            grpcServer = ServerBuilder.forPort(grpcPort)
                .addService(new TestEchoService())
                .build()
                .start();
            
            LOG.info("Started test gRPC echo service on port {}", grpcPort);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to start test gRPC server", e);
        }
    }
    
    private void registerGrpcServiceInConsul() {
        ServiceOptions serviceOptions = new ServiceOptions()
            .setName(TEST_ECHO_SERVICE)
            .setId(TEST_ECHO_SERVICE + "-" + grpcPort)
            .setAddress("localhost")  // Use localhost since we're not in Docker
            .setPort(grpcPort)
            .setTags(List.of("grpc", "test", "echo", "pipeline-module"))
            .setMeta(Map.of(
                "grpc-port", String.valueOf(grpcPort),
                "service-type", "MODULE",
                "module-type", "ECHO"
            ))
            .setCheckOptions(new CheckOptions()
                .setTcp("localhost:" + grpcPort)
                .setInterval("5s")
                .setTimeout("3s")
            );
        
        consulClient.registerService(serviceOptions)
            .toCompletionStage()
            .toCompletableFuture()
            .join();
        
        // Wait for service to be discoverable
        await().atMost(Duration.ofSeconds(10))
            .until(() -> {
                var future = consulClient.healthServiceNodes(TEST_ECHO_SERVICE, true)
                    .toCompletionStage()
                    .toCompletableFuture();
                
                var serviceList = future.join();
                return serviceList != null && 
                       serviceList.getList() != null && 
                       !serviceList.getList().isEmpty();
            });
        
        LOG.info("Registered {} service in Consul on port {}", TEST_ECHO_SERVICE, grpcPort);
    }
    
    /**
     * Simple echo implementation of PipeStepProcessor for testing.
     */
    static class TestEchoService extends PipeStepProcessorGrpc.PipeStepProcessorImplBase {
        
        @Override
        public void processData(ProcessRequest request, StreamObserver<ProcessResponse> responseObserver) {
            LOG.info("Echo service processing document: {}", request.getDocument().getId());
            processedCount.incrementAndGet();
            
            // Echo back the document with a marker
            PipeDoc outputDoc = PipeDoc.newBuilder(request.getDocument())
                .setBody("ECHO: " + request.getDocument().getBody())
                .putMetadata("processed_by", TEST_ECHO_SERVICE)
                .putMetadata("echo_count", String.valueOf(processedCount.get()))
                .build();
            
            ProcessResponse response = ProcessResponse.newBuilder()
                .setSuccess(true)
                .setOutputDoc(outputDoc)
                .addProcessorLogs("Echoed by test service")
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        @Override
        public void getServiceRegistration(RegistrationRequest request, 
                                         StreamObserver<ServiceRegistrationResponse> responseObserver) {
            ServiceRegistrationResponse response = ServiceRegistrationResponse.newBuilder()
                .setModuleName(TEST_ECHO_SERVICE)
                .setVersion("1.0.0-test")
                .setDisplayName("Test Echo Service")
                .setDescription("Echo service for integration testing")
                .setHealthCheckPassed(true)
                .setHealthCheckMessage("Healthy")
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}