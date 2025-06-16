package com.rokkon.search.engine.registration;

import com.rokkon.search.sdk.ServiceRegistrationData;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

/**
 * Service for registering modules with Consul via HTTP API.
 * 
 * This handles the manual registration of validated modules,
 * including proper gRPC health check configuration.
 */
@ApplicationScoped
public class ConsulModuleRegistry {
    
    private static final Logger LOG = LoggerFactory.getLogger(ConsulModuleRegistry.class);
    
    @ConfigProperty(name = "quarkus.consul.agent.host-port", defaultValue = "localhost:8500")
    String consulHostPort;
    
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    /**
     * Register a PipeStepProcessor module with Consul
     */
    public String registerPipeStepProcessor(
            String moduleName,
            String host, 
            int port,
            ServiceRegistrationData metadata,
            Map<String, String> additionalMetadata) throws Exception {
        
        String serviceId = "pipe-module-" + moduleName + "-" + UUID.randomUUID().toString().substring(0, 8);
        
        LOG.info("Registering module with Consul: {} -> {}", moduleName, serviceId);
        
        // Create Consul service registration JSON
        String registrationJson = createServiceRegistrationJson(
            serviceId,
            moduleName, 
            host,
            port,
            metadata,
            additionalMetadata
        );
        
        // Register with Consul via HTTP API
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + consulHostPort + "/v1/agent/service/register"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(registrationJson))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            LOG.info("✅ Module successfully registered with Consul: {}", serviceId);
            return serviceId;
        } else {
            throw new RuntimeException("Consul registration failed: " + response.statusCode() + " - " + response.body());
        }
    }
    
    /**
     * Create Consul service registration JSON with gRPC health checks
     */
    private String createServiceRegistrationJson(
            String serviceId,
            String moduleName,
            String host,
            int port, 
            ServiceRegistrationData metadata,
            Map<String, String> additionalMetadata) {
        
        // Build the JSON manually for precise control
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"ID\": \"").append(serviceId).append("\",\n");
        json.append("  \"Name\": \"").append(moduleName).append("\",\n");
        json.append("  \"Tags\": [\"pipe-step-processor\", \"grpc\", \"rokkon-module\"],\n");
        json.append("  \"Address\": \"").append(host).append("\",\n");
        json.append("  \"Port\": ").append(port).append(",\n");
        json.append("  \"Meta\": {\n");
        json.append("    \"module_name\": \"").append(metadata.getModuleName()).append("\",\n");
        json.append("    \"grpc_service\": \"com.rokkon.search.model.PipeStepProcessor\",\n");
        json.append("    \"engine_managed\": \"true\"\n");
        
        // Add additional metadata
        for (Map.Entry<String, String> entry : additionalMetadata.entrySet()) {
            json.append("    ,\"").append(entry.getKey()).append("\": \"").append(entry.getValue()).append("\"\n");
        }
        
        json.append("  },\n");
        
        // Configure gRPC health check
        json.append("  \"Check\": {\n");
        json.append("    \"GRPC\": \"").append(host).append(":").append(port).append("\",\n");
        json.append("    \"GRPCUseTLS\": false,\n");
        json.append("    \"Interval\": \"10s\",\n");
        json.append("    \"Timeout\": \"5s\",\n");
        json.append("    \"DeregisterCriticalServiceAfter\": \"30s\"\n");
        json.append("  }\n");
        json.append("}\n");
        
        LOG.debug("Consul registration JSON: {}", json.toString());
        return json.toString();
    }
    
    /**
     * Deregister a service from Consul
     */
    public void deregisterService(String serviceId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + consulHostPort + "/v1/agent/service/deregister/" + serviceId))
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                LOG.info("✅ Service deregistered from Consul: {}", serviceId);
            } else {
                LOG.warn("Failed to deregister service from Consul: {} - {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            LOG.error("Error deregistering service from Consul: {}", serviceId, e);
        }
    }
}