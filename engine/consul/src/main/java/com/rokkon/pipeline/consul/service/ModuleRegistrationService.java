package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.service.ModuleWhitelistService;
import com.rokkon.pipeline.consul.model.ModuleWhitelistRequest;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Service for registering modules with Consul.
 * This is the engine-side service that receives registration requests
 * from module CLI tools and registers the modules in Consul.
 */
@ApplicationScoped
public class ModuleRegistrationService {
    private static final Logger LOG = Logger.getLogger(ModuleRegistrationService.class);
    
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String consulHost;
    
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    String consulPort;
    
    @Inject
    ModuleWhitelistService moduleWhitelistService;
    
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    public record ModuleRegistrationRequest(
        String moduleName,
        String implementationId,
        String host,
        int port,
        Map<String, String> metadata
    ) {}
    
    public record ModuleRegistrationResponse(
        boolean success,
        String message,
        String moduleId
    ) {}
    
    /**
     * Registers a module with Consul.
     * This method:
     * 1. Validates the module is whitelisted
     * 2. Performs a health check on the module
     * 3. Registers the module as a service in Consul
     * 
     * @param clusterName the cluster to register in
     * @param request the registration request
     * @return registration response
     */
    public Uni<ModuleRegistrationResponse> registerModule(String clusterName, ModuleRegistrationRequest request) {
        LOG.infof("Registering module %s (%s) at %s:%d in cluster %s", 
                  request.moduleName, request.implementationId, request.host, request.port, clusterName);
        
        // First check if the module is whitelisted
        return moduleWhitelistService.listWhitelistedModules(clusterName)
            .flatMap(whitelistedModules -> {
                boolean isWhitelisted = whitelistedModules.stream()
                    .anyMatch(m -> m.implementationId().equals(request.implementationId));
                
                if (!isWhitelisted) {
                    // Try to whitelist it
                    LOG.infof("Module %s not whitelisted, attempting to whitelist", request.implementationId);
                    
                    ModuleWhitelistRequest whitelistRequest = new ModuleWhitelistRequest(
                        request.moduleName,
                        request.implementationId,
                        null, // No schema for now
                        request.metadata == null ? null : new java.util.HashMap<>(request.metadata)
                    );
                    
                    return moduleWhitelistService.whitelistModule(clusterName, whitelistRequest)
                        .flatMap(whitelistResponse -> {
                            if (!whitelistResponse.success()) {
                                return Uni.createFrom().item(new ModuleRegistrationResponse(
                                    false,
                                    "Failed to whitelist module: " + whitelistResponse.message(),
                                    null
                                ));
                            }
                            
                            // Continue with registration
                            return performHealthCheck(request)
                                .flatMap(healthy -> {
                                    if (!healthy) {
                                        return Uni.createFrom().item(new ModuleRegistrationResponse(
                                            false,
                                            "Module health check failed",
                                            null
                                        ));
                                    }
                                    
                                    return registerInConsul(request);
                                });
                        });
                }
                
                // Module is already whitelisted, proceed with registration
                return performHealthCheck(request)
                    .flatMap(healthy -> {
                        if (!healthy) {
                            return Uni.createFrom().item(new ModuleRegistrationResponse(
                                false,
                                "Module health check failed",
                                null
                            ));
                        }
                        
                        return registerInConsul(request);
                    });
            });
    }
    
    /**
     * Performs a gRPC health check on the module.
     * TODO: Implement actual gRPC health check
     */
    private Uni<Boolean> performHealthCheck(ModuleRegistrationRequest request) {
        LOG.infof("Performing health check on %s at %s:%d", 
                  request.implementationId, request.host, request.port);
        
        // TODO: Implement actual gRPC health check
        // For now, just return true
        return Uni.createFrom().item(true);
    }
    
    /**
     * Registers the module as a service in Consul.
     */
    private Uni<ModuleRegistrationResponse> registerInConsul(ModuleRegistrationRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                // Create the service registration JSON
                String serviceJson = String.format("""
                    {
                        "ID": "%s-%s-%d",
                        "Name": "%s",
                        "Tags": ["rokkon-module", "grpc"],
                        "Address": "%s",
                        "Port": %d,
                        "Meta": %s,
                        "Check": {
                            "GRPC": "%s:%d",
                            "GRPCUseTLS": false,
                            "Interval": "10s",
                            "Timeout": "5s"
                        }
                    }
                    """,
                    request.implementationId, request.host, request.port,
                    request.implementationId,
                    request.host,
                    request.port,
                    convertMetadataToJson(request.metadata),
                    request.host, request.port
                );
                
                String url = getConsulUrl() + "/v1/agent/service/register";
                
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(serviceJson))
                    .timeout(Duration.ofSeconds(10))
                    .build();
                
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    String moduleId = request.implementationId + "-" + request.host + "-" + request.port;
                    LOG.infof("Successfully registered module %s with ID %s", request.implementationId, moduleId);
                    
                    return new ModuleRegistrationResponse(
                        true,
                        "Module registered successfully",
                        moduleId
                    );
                } else {
                    LOG.errorf("Failed to register module in Consul: %d - %s", 
                              response.statusCode(), response.body());
                    
                    return new ModuleRegistrationResponse(
                        false,
                        "Failed to register in Consul: " + response.statusCode(),
                        null
                    );
                }
            } catch (Exception e) {
                LOG.error("Error registering module in Consul", e);
                return new ModuleRegistrationResponse(
                    false,
                    "Error registering module: " + e.getMessage(),
                    null
                );
            }
        });
    }
    
    /**
     * Deregisters a module from Consul.
     */
    public Uni<Boolean> deregisterModule(String moduleId) {
        return Uni.createFrom().item(() -> {
            try {
                String url = getConsulUrl() + "/v1/agent/service/deregister/" + moduleId;
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(10))
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    LOG.infof("Successfully deregistered module %s", moduleId);
                    return true;
                } else {
                    LOG.errorf("Failed to deregister module: %d - %s", 
                              response.statusCode(), response.body());
                    return false;
                }
            } catch (Exception e) {
                LOG.error("Error deregistering module", e);
                return false;
            }
        });
    }
    
    private String getConsulUrl() {
        return "http://" + consulHost + ":" + consulPort;
    }
    
    private String convertMetadataToJson(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":\"")
                .append(entry.getValue()).append("\"");
            first = false;
        }
        json.append("}");
        return json.toString();
    }
}