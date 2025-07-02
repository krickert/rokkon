package com.rokkon.pipeline.engine.registration;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import com.rokkon.test.containers.TestModuleContainerResource;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.common.QuarkusTestResource;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import org.jboss.logging.Logger;
import jakarta.inject.Inject;
import com.rokkon.pipeline.consul.service.DELETE_ME_GlobalModuleRegistryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.*;

/**
 * Container-aware module registration test that properly handles the distinction between:
 * 1. External health checks (using mapped ports) for validation
 * 2. Consul registration (using internal ports) for production
 * 
 * This test demonstrates the correct way to register containerized modules.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
@QuarkusTestResource(TestModuleContainerResource.class)
class ContainerAwareRegistrationIT extends ModuleRegistrationTestBase {
    
    private static final Logger LOG = Logger.getLogger(ContainerAwareRegistrationIT.class);
    
    @Inject
    DELETE_ME_GlobalModuleRegistryService moduleRegistry;
    
    @ConfigProperty(name = "test.module.container.grpc.port")
    int externalGrpcPort;
    
    @ConfigProperty(name = "test.module.container.http.port")
    int externalHttpPort;
    
    @ConfigProperty(name = "test.module.container.internal.grpc.port")
    int internalGrpcPort;
    
    @ConfigProperty(name = "test.module.container.internal.http.port") 
    int internalHttpPort;
    
    @ConfigProperty(name = "test.module.container.name")
    String containerName;
    
    @ConfigProperty(name = "test.module.container.id")
    String containerId;
    
    @ConfigProperty(name = "test.module.container.network.alias", defaultValue = "test-module")
    String networkAlias;
    
    @ConfigProperty(name = "quarkus.http.test-port")
    int enginePort;
    
    @ConfigProperty(name = "consul.host")
    String consulHost;
    
    @ConfigProperty(name = "consul.port")
    String consulPort;
    
    @ConfigProperty(name = "consul.container.host", defaultValue = "localhost")
    String consulContainerHost;
    
    @ConfigProperty(name = "consul.container.port", defaultValue = "8500")
    String consulContainerPort;
    
    private ManagedChannel healthCheckChannel;
    
    @AfterEach
    void cleanupRegistrations() {
        LOG.info("Cleaning up module registrations after test");
        
        // Clean up all modules registered with the test container
        try {
            // Get all registered modules
            var modules = moduleRegistry.listRegisteredModules()
                .await().atMost(java.time.Duration.ofSeconds(5));
            
            // Find and deregister any modules that use our test container
            for (var module : modules) {
                if (module.containerId() != null && module.containerId().equals(containerId)) {
                    LOG.infof("Deregistering module %s (container: %s)", module.moduleId(), containerId);
                    moduleRegistry.deregisterModule(module.moduleId())
                        .await().atMost(java.time.Duration.ofSeconds(5));
                }
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to cleanup module registrations, this may cause subsequent tests to fail");
        }
    }
    
    // === Implementation of abstract methods ===
    
    @Override
    protected String getBaseUrl() {
        return "http://localhost:" + enginePort;
    }
    
    @Override
    protected String getWorkingModuleHost() {
        // For external validation
        return "localhost";
    }
    
    @Override
    protected int getWorkingModulePort() {
        // Use external port for validation
        return externalGrpcPort;
    }
    
    @Override
    protected String getHealthCheckHost() {
        return "localhost";
    }
    
    @Override
    protected int getHealthCheckPort() {
        return externalGrpcPort;
    }
    
    @Override
    protected String getUnreachableHost() {
        return "container-test-invalid-" + System.currentTimeMillis() + ".docker";
    }
    
    @Override
    protected int getClosedPort() {
        return 23456;
    }
    
    @Override
    protected int getNonGrpcPort() {
        // Return the external HTTP port (not gRPC)
        return externalHttpPort;
    }
    
    @Override
    protected Map<String, String> getRegistrationMetadata() {
        // Container-specific metadata
        // When containers are on the same network, use the network alias
        // Otherwise fall back to host IP
        boolean useNetworkAlias = !networkAlias.equals("test-module") || 
                                 !consulContainerHost.equals("localhost");
        
        String consulAccessibleHost;
        int consulAccessiblePort;
        
        if (useNetworkAlias) {
            // Containers are on the same network - use internal communication
            consulAccessibleHost = networkAlias;
            consulAccessiblePort = internalGrpcPort;
            System.out.println("Using network alias for Consul registration: " + networkAlias + ":" + internalGrpcPort);
        } else {
            // Containers not on same network - use host IP
            consulAccessibleHost = getDockerHostIp();
            consulAccessiblePort = externalGrpcPort;
            System.out.println("Using host IP for Consul registration: " + consulAccessibleHost + ":" + externalGrpcPort);
        }
        
        return Map.of(
            "containerized", "true",
            "containerId", containerId,
            "containerName", containerName,
            "internalGrpcPort", String.valueOf(internalGrpcPort),
            "internalHttpPort", String.valueOf(internalHttpPort),
            "consulHost", consulAccessibleHost,
            "consulPort", String.valueOf(consulAccessiblePort)
        );
    }
    
    private String getDockerHostIp() {
        // Try to get the host IP that Docker containers can use to reach services on the host
        // This works for Linux where containers can reach the host via the docker0 bridge
        try {
            // First try the DOCKER_HOST environment variable
            String dockerHost = System.getenv("DOCKER_HOST");
            if (dockerHost != null && dockerHost.contains("://")) {
                String host = dockerHost.substring(dockerHost.indexOf("://") + 3);
                if (host.contains(":")) {
                    host = host.substring(0, host.indexOf(":"));
                }
                return host;
            }
            
            // For GitHub Actions and most CI environments, use the host's IP
            // On Linux, we can use the gateway IP
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", "ip route | grep default | awk '{print $3}'");
            Process process = pb.start();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String gatewayIp = reader.readLine();
                if (gatewayIp != null && !gatewayIp.isEmpty()) {
                    LOG.infof("Using gateway IP for Docker host: %s", gatewayIp);
                    return gatewayIp;
                }
            }
            
            // Fallback to localhost if we can't determine the host IP
            LOG.warn("Could not determine Docker host IP, falling back to localhost");
            return "localhost";
        } catch (Exception e) {
            LOG.warnf("Error determining Docker host IP: %s", e.getMessage());
            return "localhost";
        }
    }
    
    @BeforeEach
    void setup() {
        // Create channel for external health checks
        healthCheckChannel = ManagedChannelBuilder
                .forAddress("localhost", externalGrpcPort)
                .usePlaintext()
                .build();
    }
    
    @AfterEach
    void cleanup() {
        if (healthCheckChannel != null && !healthCheckChannel.isShutdown()) {
            healthCheckChannel.shutdown();
        }
    }
    
    // Helper method to check if service exists in Consul
    private boolean isServiceRegisteredInConsul(String serviceId) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            String url = String.format("http://%s:%s/v1/agent/service/%s", consulHost, consulPort, serviceId);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            System.err.println("Error checking Consul: " + e.getMessage());
            return false;
        }
    }
    
    // Additional container-specific test to demonstrate access patterns
    @Test
    void demonstrateContainerAccessPatterns() {
        System.out.println("\n=== Container Access Patterns ===");
        System.out.println("1. External Access (for testing/validation):");
        System.out.println("   - Host: localhost");
        System.out.println("   - gRPC Port: " + externalGrpcPort + " (mapped)");
        System.out.println("   - HTTP Port: " + externalHttpPort + " (mapped)");
        System.out.println("   - Used by: External clients, test frameworks");
        
        System.out.println("\n2. Internal Access (for Consul):");
        System.out.println("   - Host: " + containerName.substring(1) + " (container name)");
        System.out.println("   - gRPC Port: " + internalGrpcPort + " (internal)");
        System.out.println("   - HTTP Port: " + internalHttpPort + " (internal)");
        System.out.println("   - Used by: Consul health checks, other containers");
        
        System.out.println("\n3. Registration Strategy:");
        System.out.println("   - Validate using external port (we can reach it)");
        System.out.println("   - Register using internal port (Consul can reach it)");
        System.out.println("   - Store both in metadata for debugging");
        
        // Verify we can do gRPC health checks via external port
        HealthGrpc.HealthBlockingStub healthStub = HealthGrpc.newBlockingStub(healthCheckChannel);
        HealthCheckResponse grpcResponse = healthStub.check(HealthCheckRequest.newBuilder().build());
        assertThat(grpcResponse.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        
        // Also verify HTTP health endpoint
        RestAssured.given()
            .baseUri("http://localhost:" + externalHttpPort)
            .when()
            .get("/q/health")
            .then()
            .statusCode(200)
            .body("status", is("UP"));
            
        System.out.println("\n✓ Both gRPC and HTTP health checks confirmed working");
    }
    
    @Override
    @Test
    void testSuccessfulModuleRegistration() {
        // Call the parent test
        super.testSuccessfulModuleRegistration();
        
        // Additional verification: check if the service is actually in Consul
        // Extract the module ID from the response
        Response response = RestAssured.given()
            .baseUri(getBaseUrl())
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", getWorkingModuleHost(),
                "port", getWorkingModulePort(),
                "clusterName", getValidClusterName(),
                "serviceType", "PipeStepProcessor",
                "metadata", getRegistrationMetadata()
            ))
            .when()
            .post("/api/v1/modules/register")
            .then()
            .extract()
            .response();
            
        if (response.statusCode() == 200) {
            String moduleId = response.jsonPath().getString("moduleId");
            assertThat(moduleId).isNotNull();
            
            // Verify the service is actually registered in Consul
            boolean isInConsul = isServiceRegisteredInConsul(moduleId);
            assertThat(isInConsul)
                .as("Service '%s' should be registered in Consul", moduleId)
                .isTrue();
                
            System.out.println("✓ Verified service '" + moduleId + "' is registered in Consul");
        }
    }
}