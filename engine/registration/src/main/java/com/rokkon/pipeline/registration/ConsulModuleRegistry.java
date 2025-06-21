package com.rokkon.pipeline.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.search.grpc.ModuleInfo;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ConsulModuleRegistry {
    private static final Logger LOG = Logger.getLogger(ConsulModuleRegistry.class);
    
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String consulHost;
    
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    String consulPort;
    
    @Inject
    ObjectMapper objectMapper;
    
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    public record ConsulServiceRegistration(
        String ID,
        String Name,
        List<String> Tags,
        String Address,
        int Port,
        ConsulHealthCheck Check,
        Map<String, String> Meta
    ) {}
    
    public record ConsulHealthCheck(
        String GRPC,
        String Interval,
        String Timeout
    ) {}
    
    public Uni<String> registerService(ModuleInfo moduleInfo) {
        LOG.infof("🔗 Registering service with Consul: %s at %s:%d", 
                moduleInfo.getServiceName(), moduleInfo.getHost(), moduleInfo.getPort());
                
        return Uni.createFrom().completionStage(() -> {
            try {
                String consulServiceId = "grpc-" + moduleInfo.getServiceId();
                
                // Create metadata including schema if present
                Map<String, String> metadata = new java.util.HashMap<>(moduleInfo.getMetadataMap());
                metadata.put("registeredAt", java.time.Instant.now().toString());
                
                // Create Consul service registration with gRPC health check
                ConsulServiceRegistration registration = new ConsulServiceRegistration(
                    consulServiceId,
                    moduleInfo.getServiceName(),
                    List.of("grpc", "pipeline-module"),
                    moduleInfo.getHost(),
                    moduleInfo.getPort(),
                    new ConsulHealthCheck(
                        moduleInfo.getHost() + ":" + moduleInfo.getPort(),
                        "10s",
                        "5s"
                    ),
                    metadata
                );
                
                String json = objectMapper.writeValueAsString(registration);
                
                String url = getConsulUrl() + "/v1/agent/service/register";
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();
                    
                return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            LOG.infof("✅ Service registered successfully with Consul: %s -> %s", 
                                    moduleInfo.getServiceName(), consulServiceId);
                            return consulServiceId;
                        } else {
                            LOG.errorf("❌ Failed to register service: %d - %s", 
                                     response.statusCode(), response.body());
                            throw new RuntimeException("Consul registration failed: " + response.body());
                        }
                    });
                    
            } catch (Exception e) {
                LOG.errorf(e, "❌ Error preparing Consul registration for: %s", moduleInfo.getServiceName());
                throw new RuntimeException("Failed to register service", e);
            }
        });
    }
    
    public Uni<Boolean> unregisterService(String consulServiceId) {
        LOG.infof("🗑️ Unregistering service from Consul: %s", consulServiceId);
        
        String url = getConsulUrl() + "/v1/agent/service/deregister/" + consulServiceId;
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build();
            
        return Uni.createFrom().completionStage(
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        ).map(response -> {
            if (response.statusCode() == 200) {
                LOG.infof("✅ Service unregistered successfully from Consul: %s", consulServiceId);
                return true;
            } else {
                LOG.errorf("❌ Failed to unregister service: %d - %s", 
                         response.statusCode(), response.body());
                return false;
            }
        });
    }
    
    public Uni<Boolean> checkServiceHealth(String consulServiceId) {
        LOG.debugf("🔍 Checking service health in Consul: %s", consulServiceId);
        
        String url = getConsulUrl() + "/v1/health/service/" + consulServiceId;
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
            
        return Uni.createFrom().completionStage(
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        ).map(response -> {
            if (response.statusCode() == 200) {
                // Parse JSON to check health status
                try {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> services = objectMapper.readValue(response.body(), List.class);
                    
                    if (services.isEmpty()) {
                        LOG.warnf("⚠️ Service not found in Consul: %s", consulServiceId);
                        return false;
                    }
                    
                    // Check if any checks are passing
                    for (Map<String, Object> service : services) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> checks = (List<Map<String, Object>>) service.get("Checks");
                        if (checks != null) {
                            boolean allHealthy = checks.stream()
                                .allMatch(check -> "passing".equals(check.get("Status")));
                            if (allHealthy) {
                                LOG.debugf("✅ Service healthy in Consul: %s", consulServiceId);
                                return true;
                            }
                        }
                    }
                    
                    LOG.warnf("⚠️ Service unhealthy in Consul: %s", consulServiceId);
                    return false;
                    
                } catch (Exception e) {
                    LOG.errorf(e, "❌ Error parsing health response for: %s", consulServiceId);
                    return false;
                }
            } else {
                LOG.errorf("❌ Failed to check service health: %d - %s", 
                         response.statusCode(), response.body());
                return false;
            }
        });
    }
    
    private String getConsulUrl() {
        return "http://" + consulHost + ":" + consulPort;
    }
    
    /**
     * Get existing module registration from Consul
     * Returns Optional.empty() if module is not registered
     */
    public Uni<java.util.Optional<ModuleInfo>> getExistingModule(String moduleName) {
        String consulUrl = getConsulUrl() + "/v1/catalog/service/" + moduleName;
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(consulUrl))
            .GET()
            .build();
            
        return Uni.createFrom().completionStage(() -> 
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        ).map(response -> {
            if (response.statusCode() == 200) {
                try {
                    // Parse the service nodes from Consul
                    List<Map<String, Object>> services = objectMapper.readValue(
                        response.body(), 
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
                    );
                    
                    if (services.isEmpty()) {
                        LOG.debugf("Module %s not found in Consul", moduleName);
                        return java.util.Optional.<ModuleInfo>empty();
                    }
                    
                    // Get the first service instance
                    Map<String, Object> service = services.get(0);
                    
                    // Check for schema in service metadata/tags
                    String schemaDefinition = null;
                    if (service.containsKey("ServiceMeta")) {
                        Map<String, String> meta = (Map<String, String>) service.get("ServiceMeta");
                        schemaDefinition = meta.get("schema");
                    }
                    
                    // Reconstruct ModuleInfo from Consul data
                    ModuleInfo.Builder builder = ModuleInfo.newBuilder()
                        .setServiceName(moduleName)
                        .setServiceId((String) service.get("ServiceID"))
                        .setHost((String) service.get("ServiceAddress"))
                        .setPort((Integer) service.get("ServicePort"));
                        
                    if (schemaDefinition != null) {
                        builder.putMetadata("schema", schemaDefinition);
                    }
                    
                    return java.util.Optional.of(builder.build());
                    
                } catch (Exception e) {
                    LOG.errorf(e, "Failed to parse existing module data from Consul");
                    return java.util.Optional.<ModuleInfo>empty();
                }
            } else if (response.statusCode() == 404) {
                LOG.debugf("Module %s not found in Consul (404)", moduleName);
                return java.util.Optional.<ModuleInfo>empty();
            } else {
                LOG.errorf("Failed to check existing module: %d - %s", 
                         response.statusCode(), response.body());
                return java.util.Optional.<ModuleInfo>empty();
            }
        }).onFailure().recoverWithItem(throwable -> {
            LOG.errorf(throwable, "Error checking existing module in Consul");
            return java.util.Optional.empty();
        });
    }
}