package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.service.ModuleWhitelistService;
import com.rokkon.pipeline.consul.test.ConsulTestResource;
import com.rokkon.pipeline.validation.CompositeValidator;
import com.rokkon.pipeline.config.model.PipelineConfig;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Integration test for ModuleWhitelistService.
 * Runs with full Quarkus application in prod mode.
 * Uses real Consul instance and all real services.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
class ModuleWhitelistServiceIT extends ModuleWhitelistServiceTestBase {
    
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String consulHost;
    
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    String consulPort;
    
    private ModuleWhitelistService whitelistService;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    @Inject
    Instance<CompositeValidator<PipelineConfig>> validatorInstance;
    
    @BeforeEach
    void setUpIT() {
        // Create service instance manually for integration test
        // In prod mode, we need to instantiate services ourselves
        ObjectMapper objectMapper = new ObjectMapper();
        ClusterServiceImpl clusterService = new ClusterServiceImpl();
        PipelineConfigServiceImpl pipelineConfigService = new PipelineConfigServiceImpl();
        
        // Use reflection to set fields since we're in IT mode
        setField(clusterService, "consulHost", consulHost);
        setField(clusterService, "consulPort", consulPort);
        setField(clusterService, "objectMapper", objectMapper);
        
        setField(pipelineConfigService, "consulHost", consulHost);
        setField(pipelineConfigService, "consulPort", consulPort);
        setField(pipelineConfigService, "objectMapper", objectMapper);
        setField(pipelineConfigService, "clusterService", clusterService);
        
        // Create composite validator
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
    }
    
    @Override
    protected String getConsulHost() {
        return consulHost;
    }
    
    @Override
    protected String getConsulPort() {
        return consulPort;
    }
    
    @Override
    protected ModuleWhitelistService getWhitelistService() {
        return whitelistService;
    }
    
    @Override
    protected Uni<Boolean> registerModuleInConsul(String moduleName, String host, int port) {
        // Register module in Consul as a gRPC service
        String serviceJson = String.format("""
            {
                "ID": "%s-it-test",
                "Name": "%s",
                "Tags": ["grpc", "integration-test"],
                "Address": "%s",
                "Port": %d,
                "Check": {
                    "Name": "Module Health Check",
                    "GRPC": "%s:%d",
                    "GRPCUseTLS": false,
                    "Interval": "30s",
                    "Timeout": "5s"
                }
            }
            """, moduleName, moduleName, host, port, host, port);
        
        String url = String.format("http://%s:%s/v1/agent/service/register", consulHost, consulPort);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(serviceJson))
            .build();
        
        return Uni.createFrom().completionStage(
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        )
        .map(response -> response.statusCode() == 200);
    }
    
    /**
     * Helper method to set fields via reflection for IT tests.
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