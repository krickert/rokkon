package com.rokkon.pipeline.engine.grpc;

import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.MutinyPipeStepProcessorGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.smallrye.mutiny.Uni;
import io.smallrye.stork.api.LoadBalancer;
import io.smallrye.stork.api.ServiceInstance;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.ServiceOptions;
import org.junit.jupiter.api.*;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for Dynamic gRPC functionality with real Consul.
 * 
 * IMPORTANT: This test creates REAL instances of beans instead of using CDI injection.
 * This approach avoids multi-module CDI bean discovery issues and tests actual behavior.
 * 
 * Test Flow:
 * 1. Start Consul container (on random port via Testcontainers)
 * 2. Create real Vertx and ConsulClient instances
 * 3. Create test ServiceDiscovery that uses the ConsulClient
 * 4. Create real DynamicGrpcClientFactory and configure it
 * 5. Test service registration, discovery, and gRPC calls
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DynamicGrpcIntegrationIT {
    
    @Container
    static ConsulContainer consul = new ConsulContainer(DockerImageName.parse("hashicorp/consul:latest"))
            .withCommand("agent -dev -client 0.0.0.0 -log-level debug");
    
    // Real instances - not injected
    private Vertx vertx;
    private ConsulClient consulClient;
    private ServiceDiscovery serviceDiscovery;
    private DynamicGrpcClientFactory clientFactory;
    
    // Test gRPC server
    private static Server grpcServer;
    private static int grpcPort;
    private static String serviceId;
    
    @BeforeAll
    static void setupGrpcServer() throws IOException {
        // Find a free port for our test gRPC server
        try (ServerSocket socket = new ServerSocket(0)) {
            grpcPort = socket.getLocalPort();
        }
        
        // Create a simple gRPC server for testing
        grpcServer = ServerBuilder.forPort(grpcPort)
            .addService(new TestPipeStepProcessor())
            .build();
        
        grpcServer.start();
        serviceId = "test-service-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    @AfterAll
    static void teardownGrpcServer() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.shutdown();
            grpcServer.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
    
    @BeforeEach
    void setup() {
        System.out.println("=== Setting up integration test ===");
        System.out.println("Consul container host: " + consul.getHost());
        System.out.println("Consul container port: " + consul.getFirstMappedPort());
        
        // 1. Create Vertx instance
        vertx = Vertx.vertx();
        
        // 2. Create Consul client directly
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consul.getHost())
            .setPort(consul.getFirstMappedPort());
        
        consulClient = ConsulClient.create(vertx, options);
        
        // 3. Wait a bit for the connection to stabilize
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 4. Create test service discovery that uses our ConsulClient
        serviceDiscovery = new TestConsulServiceDiscovery(consulClient);
        
        // 5. Create real DynamicGrpcClientFactory and configure it
        clientFactory = new DynamicGrpcClientFactory();
        clientFactory.setServiceDiscovery(serviceDiscovery);
        
        System.out.println("=== Setup complete ===");
    }
    
    @AfterEach
    void cleanup() {
        // Deregister service after each test
        if (serviceId != null && consulClient != null) {
            consulClient.deregisterService(serviceId);
        }
        
        // Clean up resources
        if (consulClient != null) {
            consulClient.close();
        }
        if (vertx != null) {
            vertx.close();
        }
    }
    
    @Test
    @Order(1)
    void testServiceRegistrationAndDiscovery() {
        // Register a test service in Consul
        ServiceOptions service = new ServiceOptions()
            .setName("test-grpc-service")
            .setId(serviceId)
            .setAddress("localhost")  // Use localhost for local testing
            .setPort(grpcPort)
            .setTags(List.of("grpc", "test"));
        
        // Register the service
        Uni.createFrom().completionStage(() -> consulClient.registerService(service).toCompletionStage())
            .await().atMost(Duration.ofSeconds(5));
        
        // Wait for service to be registered and healthy
        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            var services = Uni.createFrom().completionStage(() -> 
                consulClient.healthServiceNodes("test-grpc-service", true).toCompletionStage()
            ).await().atMost(Duration.ofSeconds(2));
            
            return services != null && !services.getList().isEmpty();
        });
        
        // Discover the service using our ServiceDiscovery
        var serviceInstance = serviceDiscovery.discoverService("test-grpc-service")
            .await().atMost(Duration.ofSeconds(5));
        
        assertThat(serviceInstance).isNotNull();
        assertThat(serviceInstance.getHost()).isEqualTo("localhost");
        assertThat(serviceInstance.getPort()).isEqualTo(grpcPort);
    }
    
    @Test
    @Order(2)
    void testDynamicGrpcClientCall() {
        // Register service first
        ServiceOptions service = new ServiceOptions()
            .setName("echo-test")
            .setId(serviceId)
            .setAddress("localhost")  // Use localhost since we're in the same JVM
            .setPort(grpcPort)
            .setTags(List.of("grpc", "test"));
        
        Uni.createFrom().completionStage(() -> consulClient.registerService(service).toCompletionStage())
            .await().atMost(Duration.ofSeconds(5));
        
        // Wait for registration
        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            var services = Uni.createFrom().completionStage(() -> 
                consulClient.healthServiceNodes("echo-test", true).toCompletionStage()
            ).await().atMost(Duration.ofSeconds(2));
            
            return services != null && !services.getList().isEmpty();
        });
        
        // Use our client factory to get a client
        PipeStepProcessor client = clientFactory.getClientForService("echo-test")
            .await().atMost(Duration.ofSeconds(5));
        
        assertThat(client).isNotNull();
        
        // Make a gRPC call
        ProcessRequest request = ProcessRequest.newBuilder()
            .setDocument(com.rokkon.search.model.PipeDoc.newBuilder()
                .setId("test-doc-1")
                .setBody("Hello, World!")
                .build())
            .setMetadata(com.rokkon.search.sdk.ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("test-step")
                .setStreamId("stream-1")
                .build())
            .build();
        
        ProcessResponse response = client.processData(request)
            .await().atMost(Duration.ofSeconds(5));
        
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        assertThat(response.getOutputDoc().getBody()).contains("Hello, World!");
    }
    
    @Test
    @Order(3)
    void testMutinyClientCall() {
        // Register service
        ServiceOptions service = new ServiceOptions()
            .setName("mutiny-test")
            .setId(serviceId + "-mutiny")
            .setAddress("localhost")
            .setPort(grpcPort)
            .setTags(List.of("grpc", "test"));
        
        Uni.createFrom().completionStage(() -> consulClient.registerService(service).toCompletionStage())
            .await().atMost(Duration.ofSeconds(5));
        
        // Wait for registration
        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            var services = Uni.createFrom().completionStage(() -> 
                consulClient.healthServiceNodes("mutiny-test", true).toCompletionStage()
            ).await().atMost(Duration.ofSeconds(2));
            
            return services != null && !services.getList().isEmpty();
        });
        
        // Get Mutiny client through our factory
        MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub mutinyClient = 
            clientFactory.getMutinyClientForService("mutiny-test")
                .await().atMost(Duration.ofSeconds(5));
        
        assertThat(mutinyClient).isNotNull();
        
        // Make a reactive gRPC call
        ProcessRequest request = ProcessRequest.newBuilder()
            .setDocument(com.rokkon.search.model.PipeDoc.newBuilder()
                .setId("test-doc-2")
                .setBody("Hello, Mutiny!")
                .build())
            .setMetadata(com.rokkon.search.sdk.ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("test-step")
                .setStreamId("stream-2")
                .build())
            .build();
        
        // Use the Mutiny API directly
        ProcessResponse response = mutinyClient.processData(request)
            .await().atMost(Duration.ofSeconds(5));
        
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        assertThat(response.getOutputDoc().getBody()).contains("Hello, Mutiny!");
    }
    
    @Test
    @Order(4)
    void testLoadBalancing() {
        // Register multiple instances of the same service
        String serviceName = "load-balanced-service";
        
        // Start two more gRPC servers
        int port1 = findFreePort();
        int port2 = findFreePort();
        
        Server server1 = ServerBuilder.forPort(port1)
            .addService(new TestPipeStepProcessor("server1"))
            .build();
        
        Server server2 = ServerBuilder.forPort(port2)
            .addService(new TestPipeStepProcessor("server2"))
            .build();
        
        try {
            server1.start();
            server2.start();
            
            // Register both instances
            registerService(serviceName, "instance1", "localhost", port1);
            registerService(serviceName, "instance2", "localhost", port2);
            
            // Wait for both to be registered
            await().atMost(10, TimeUnit.SECONDS).until(() -> {
                var services = Uni.createFrom().completionStage(() -> 
                    consulClient.healthServiceNodes(serviceName, true).toCompletionStage()
                ).await().atMost(Duration.ofSeconds(2));
                
                return services != null && services.getList().size() == 2;
            });
            
            // Make multiple calls and verify load balancing
            int server1Calls = 0;
            int server2Calls = 0;
            
            for (int i = 0; i < 10; i++) {
                // Use uncached to force new discovery each time
                PipeStepProcessor client = clientFactory.getClientForServiceUncached(serviceName)
                    .await().atMost(Duration.ofSeconds(5));
                
                ProcessRequest request = ProcessRequest.newBuilder()
                    .setDocument(com.rokkon.search.model.PipeDoc.newBuilder()
                        .setId("test-doc-" + i)
                        .setBody("test")
                        .build())
                    .setMetadata(com.rokkon.search.sdk.ServiceMetadata.newBuilder()
                        .setPipelineName("test")
                        .setPipeStepName("step")
                        .setStreamId("stream-" + i)
                        .build())
                    .build();
                
                ProcessResponse response = client.processData(request)
                    .await().atMost(Duration.ofSeconds(5));
                
                String responseText = response.getOutputDoc().getBody();
                if (responseText.contains("server1")) {
                    server1Calls++;
                } else if (responseText.contains("server2")) {
                    server2Calls++;
                }
            }
            
            // Verify both servers received calls (random distribution)
            assertThat(server1Calls).isGreaterThan(0);
            assertThat(server2Calls).isGreaterThan(0);
            assertThat(server1Calls + server2Calls).isEqualTo(10);
            
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                server1.shutdown();
                server2.shutdown();
                server1.awaitTermination(5, TimeUnit.SECONDS);
                server2.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }
    
    private void registerService(String name, String id, String host, int port) {
        ServiceOptions service = new ServiceOptions()
            .setName(name)
            .setId(id)
            .setAddress(host)
            .setPort(port)
            .setTags(List.of("grpc", "test"));
        
        Uni.createFrom().completionStage(() -> consulClient.registerService(service).toCompletionStage())
            .await().atMost(Duration.ofSeconds(5));
    }
    
    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to find free port", e);
        }
    }
    
    /**
     * Test implementation of ServiceDiscovery that directly uses ConsulClient.
     * This avoids the need for ConsulConnectionManager and its CDI dependencies.
     */
    static class TestConsulServiceDiscovery implements ServiceDiscovery {
        private final ConsulClient consulClient;
        private final LoadBalancer loadBalancer = new RandomLoadBalancer();
        
        TestConsulServiceDiscovery(ConsulClient consulClient) {
            this.consulClient = consulClient;
        }
        
        @Override
        public Uni<ServiceInstance> discoverService(String serviceName) {
            return discoverAllInstances(serviceName)
                .map(instances -> {
                    if (instances == null || instances.isEmpty()) {
                        throw new RuntimeException(
                            "No healthy instances found for service: " + serviceName
                        );
                    }
                    
                    // Use load balancer to select an instance
                    return loadBalancer.selectServiceInstance(instances);
                });
        }
        
        @Override
        public Uni<List<ServiceInstance>> discoverAllInstances(String serviceName) {
            return Uni.createFrom().completionStage(() -> 
                consulClient.healthServiceNodes(serviceName, true).toCompletionStage()
            ).map(serviceList -> {
                if (serviceList == null || serviceList.getList().isEmpty()) {
                    return List.of();
                }
                
                return serviceList.getList().stream()
                    .map(entry -> {
                        var service = entry.getService();
                        return (ServiceInstance) new ConsulServiceInstance(
                            service.getId(),
                            service.getAddress(),
                            service.getPort(),
                            serviceName
                        );
                    })
                    .collect(Collectors.toList());
            });
        }
    }
    
    /**
     * Consul-specific ServiceInstance implementation
     */
    static class ConsulServiceInstance implements ServiceInstance {
        private final String id;
        private final String host;
        private final int port;
        private final String serviceName;
        
        ConsulServiceInstance(String id, String host, int port, String serviceName) {
            this.id = id;
            this.host = host;
            this.port = port;
            this.serviceName = serviceName;
        }
        
        @Override
        public long getId() {
            // Convert string ID to long by using hashcode
            return id.hashCode();
        }
        
        @Override
        public String getHost() {
            return host;
        }
        
        @Override
        public int getPort() {
            return port;
        }
        
        @Override
        public boolean isSecure() {
            return false;
        }
        
        @Override
        public java.util.Map<String, String> getLabels() {
            return java.util.Map.of("service", serviceName, "id", id);
        }
        
        @Override
        public java.util.Optional<String> getPath() {
            return java.util.Optional.empty();
        }
        
        @Override
        public io.smallrye.stork.api.Metadata<? extends io.smallrye.stork.api.MetadataKey> getMetadata() {
            return io.smallrye.stork.api.Metadata.empty();
        }
    }
    
    /**
     * Test implementation of PipeStepProcessor
     */
    static class TestPipeStepProcessor extends com.rokkon.search.sdk.PipeStepProcessorGrpc.PipeStepProcessorImplBase {
        private final String serverName;
        
        TestPipeStepProcessor() {
            this("default");
        }
        
        TestPipeStepProcessor(String serverName) {
            this.serverName = serverName;
        }
        
        @Override
        public void processData(ProcessRequest request, StreamObserver<ProcessResponse> responseObserver) {
            String payload = request.getDocument().getBody();
            ProcessResponse response = ProcessResponse.newBuilder()
                .setSuccess(true)
                .setOutputDoc(com.rokkon.search.model.PipeDoc.newBuilder()
                    .setId(request.getDocument().getId())
                    .setBody("Processed: " + payload + " by " + serverName)
                    .build())
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}