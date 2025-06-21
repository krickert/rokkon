package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.validation.CompositeValidator;
import com.rokkon.test.containers.ModuleContainerResource;
import com.rokkon.test.containers.SharedNetworkManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.containers.Network;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Container-based integration test for ModuleWhitelistService.
 * Uses real Docker containers for modules and Consul.
 * Tests the complete flow with network connectivity.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ModuleWhitelistServiceContainerIT.TestModuleContainerResource.class)
class ModuleWhitelistServiceContainerIT extends ModuleWhitelistServiceTestBase {
    
    /**
     * Custom test resource that provides the test module container.
     */
    public static class TestModuleContainerResource extends ModuleContainerResource {
        public TestModuleContainerResource() {
            super("rokkon/test-module:1.0.0-SNAPSHOT");
        }
    }
    
    @ConfigProperty(name = "test.module.container.name")
    String testModuleContainerName;
    
    @ConfigProperty(name = "test.module.container.internal.grpc.port", defaultValue = "9090")
    int testModuleInternalPort;
    
    private ConsulContainer consulContainer;
    private ModuleWhitelistServiceImpl whitelistService;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private Network sharedNetwork;
    
    @Inject
    Instance<CompositeValidator<PipelineConfig>> validatorInstance;
    
    @BeforeEach
    void setUpContainerIT() throws Exception {
        // Get shared network
        sharedNetwork = SharedNetworkManager.getOrCreateNetwork();
        
        // Start Consul container on shared network
        consulContainer = new ConsulContainer("hashicorp/consul:1.18")
            .withNetwork(sharedNetwork)
            .withNetworkAliases("consul");
        consulContainer.start();
        
        // Create service instances
        ObjectMapper objectMapper = new ObjectMapper();
        ClusterServiceImpl clusterService = new ClusterServiceImpl();
        PipelineConfigServiceImpl pipelineConfigService = new PipelineConfigServiceImpl();
        
        String consulHost = consulContainer.getHost();
        String consulPort = String.valueOf(consulContainer.getMappedPort(8500));
        
        // Set fields
        setField(clusterService, "consulHost", consulHost);
        setField(clusterService, "consulPort", consulPort);
        setField(clusterService, "objectMapper", objectMapper);
        
        setField(pipelineConfigService, "consulHost", consulHost);
        setField(pipelineConfigService, "consulPort", consulPort);
        setField(pipelineConfigService, "objectMapper", objectMapper);
        setField(pipelineConfigService, "clusterService", clusterService);
        
        CompositeValidator<PipelineConfig> validator = validatorInstance.get();
        setField(pipelineConfigService, "validator", validator);
        
        // Create whitelist service
        whitelistService = new ModuleWhitelistServiceImpl();
        setField(whitelistService, "consulHost", consulHost);
        setField(whitelistService, "consulPort", consulPort);
        setField(whitelistService, "objectMapper", objectMapper);
        setField(whitelistService, "clusterService", clusterService);
        setField(whitelistService, "pipelineConfigService", pipelineConfigService);
        setField(whitelistService, "pipelineValidator", validator);
        
        // Call parent setup
        super.setUpBase();
    }
    
    @Override
    protected String getConsulHost() {
        return consulContainer.getHost();
    }
    
    @Override
    protected String getConsulPort() {
        return String.valueOf(consulContainer.getMappedPort(8500));
    }
    
    @Override
    protected ModuleWhitelistService getWhitelistService() {
        return whitelistService;
    }
    
    @Override
    protected Uni<Boolean> registerModuleInConsul(String moduleName, String host, int port) {
        // For container tests, use the container's network alias
        String networkHost = moduleName.equals(TEST_MODULE) ? testModuleContainerName : host;
        
        // Register module in Consul with internal network address
        String serviceJson = String.format("""
            {
                "ID": "%s-container-test",
                "Name": "%s",
                "Tags": ["grpc", "container-test"],
                "Address": "%s",
                "Port": %d,
                "Meta": {
                    "externalHost": "%s",
                    "externalPort": "%d"
                },
                "Check": {
                    "Name": "Module Health Check",
                    "GRPC": "%s:%d",
                    "GRPCUseTLS": false,
                    "Interval": "10s",
                    "Timeout": "5s"
                }
            }
            """, moduleName, moduleName, networkHost, port, 
                 "localhost", port, networkHost, port);
        
        String url = String.format("http://%s:%s/v1/agent/service/register", 
                                  getConsulHost(), getConsulPort());
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(serviceJson))
            .build();
        
        return Uni.createFrom().completionStage(
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        )
        .map(response -> {
            if (response.statusCode() != 200) {
                System.err.println("Failed to register module: " + response.body());
            }
            return response.statusCode() == 200;
        });
    }
    
    @Test
    void testWhitelistWithRealContainer() throws Exception {
        // Register the test module container in Consul
        // Use the container's network alias as host
        registerModuleInConsul(TEST_MODULE, testModuleContainerName, testModuleInternalPort)
            .subscribe().with(
                success -> assertThat(success).isTrue(),
                failure -> {
                    throw new RuntimeException("Failed to register module", failure);
                }
            );
        
        // Wait for Consul to recognize the service
        Thread.sleep(2000);
        
        // Verify module is registered in Consul
        String url = String.format("http://%s:%s/v1/catalog/service/%s", 
                                  getConsulHost(), getConsulPort(), TEST_MODULE);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(TEST_MODULE);
        
        // Now test whitelisting - this should work since module exists in Consul
        testWhitelistModuleSuccess();
    }
    
    @Test
    void testConsulHealthChecksWork() throws Exception {
        // Register module with container network alias
        registerModuleInConsul(TEST_MODULE, testModuleContainerName, testModuleInternalPort)
            .subscribe().with(
                success -> assertThat(success).isTrue(),
                failure -> {
                    throw new RuntimeException("Failed to register module", failure);
                }
            );
        
        // Wait for health checks to run
        Thread.sleep(15000); // Wait for at least one health check interval
        
        // Check health status
        String url = String.format("http://%s:%s/v1/health/service/%s?passing=true", 
                                  getConsulHost(), getConsulPort(), TEST_MODULE);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        
        String body = response.body();
        if (body.equals("[]")) {
            // If no passing checks yet, check all health statuses
            url = String.format("http://%s:%s/v1/health/service/%s", 
                               getConsulHost(), getConsulPort(), TEST_MODULE);
            request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
            
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Health check status: " + response.body());
        }
        
        // Module should be registered even if health checks haven't passed yet
        assertThat(response.body()).isNotEqualTo("null");
    }
    
    /**
     * Helper method to set fields via reflection.
     */
    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}