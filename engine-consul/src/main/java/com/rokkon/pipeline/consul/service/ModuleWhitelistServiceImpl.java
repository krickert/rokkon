package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.consul.model.ModuleWhitelistRequest;
import com.rokkon.pipeline.consul.model.ModuleWhitelistResponse;
import com.rokkon.pipeline.validation.CompositeValidator;
import com.rokkon.pipeline.validation.ValidationResult;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service implementation for managing module whitelisting in clusters.
 * Handles adding modules to the PipelineModuleMap after verifying they exist in Consul.
 */
@ApplicationScoped
public class ModuleWhitelistServiceImpl implements ModuleWhitelistService {
    
    private static final Logger LOG = LoggerFactory.getLogger(ModuleWhitelistServiceImpl.class);
    private static final String KV_PREFIX = "rokkon-clusters";
    
    @Inject
    ObjectMapper objectMapper;
    
    @Inject
    CompositeValidator<PipelineConfig> pipelineValidator;
    
    @Inject
    ClusterService clusterService;
    
    @Inject
    PipelineConfigService pipelineConfigService;
    
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String consulHost;
    
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    String consulPort;
    
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    /**
     * Adds a module to the cluster's whitelist (PipelineModuleMap).
     * 
     * @param clusterName The cluster to add the module to
     * @param request The module whitelist request
     * @return Response indicating success or failure
     */
    public Uni<ModuleWhitelistResponse> whitelistModule(String clusterName, ModuleWhitelistRequest request) {
        LOG.info("Whitelisting module '{}' for cluster '{}'", request.grpcServiceName(), clusterName);
        
        // Step 1: Verify the module exists in Consul
        return verifyModuleExistsInConsul(request.grpcServiceName())
            .flatMap(exists -> {
                if (!exists) {
                    LOG.warn("Module '{}' not found in Consul - it must be registered at least once", 
                             request.grpcServiceName());
                    return Uni.createFrom().item(ModuleWhitelistResponse.failure(
                        "Module '" + request.grpcServiceName() + 
                        "' not found in Consul. Module must be registered at least once before whitelisting."
                    ));
                }
                
                // Step 2: Load the current cluster configuration
                return loadClusterConfig(clusterName)
                    .flatMap(clusterConfig -> {
                if (clusterConfig == null) {
                    return Uni.createFrom().item(ModuleWhitelistResponse.failure(
                        "Cluster '" + clusterName + "' not found"
                    ));
                }
                
                // Step 3: Update the PipelineModuleMap
                PipelineModuleMap currentModuleMap = clusterConfig.pipelineModuleMap();
                if (currentModuleMap == null) {
                    currentModuleMap = new PipelineModuleMap(new HashMap<>());
                }
                
                // Check if already whitelisted
                if (currentModuleMap.availableModules().containsKey(request.grpcServiceName())) {
                    LOG.info("Module '{}' is already whitelisted for cluster '{}'", 
                             request.grpcServiceName(), clusterName);
                    return Uni.createFrom().item(ModuleWhitelistResponse.success(
                        "Module '" + request.grpcServiceName() + "' is already whitelisted"
                    ));
                }
                
                // Create new module configuration
                PipelineModuleConfiguration moduleConfig = new PipelineModuleConfiguration(
                    request.implementationName(),
                    request.grpcServiceName(), // Using gRPC service name as implementation ID
                    request.customConfigSchemaReference(),
                    request.customConfig()
                );
                
                // Create updated module map
                Map<String, PipelineModuleConfiguration> updatedModules = 
                    new HashMap<>(currentModuleMap.availableModules());
                updatedModules.put(request.grpcServiceName(), moduleConfig);
                PipelineModuleMap updatedModuleMap = new PipelineModuleMap(updatedModules);
                
                // Step 4: Validate all pipelines with the new module map
                return validatePipelinesWithModuleMap(clusterConfig, updatedModuleMap)
                    .flatMap(validationResult -> {
                        if (!validationResult.valid()) {
                            return Uni.createFrom().item(ModuleWhitelistResponse.failure(
                                "Whitelisting module would cause validation errors",
                                validationResult.errors(),
                                validationResult.warnings()
                            ));
                        }
                        
                        // Step 5: Update cluster config with new module map
                        // Also update allowedGrpcServices to include the new module
                        Set<String> updatedAllowedGrpcServices = new HashSet<>(
                            clusterConfig.allowedGrpcServices() != null ? 
                            clusterConfig.allowedGrpcServices() : Set.of()
                        );
                        updatedAllowedGrpcServices.add(request.grpcServiceName());
                        
                        PipelineClusterConfig updatedClusterConfig = new PipelineClusterConfig(
                            clusterConfig.clusterName(),
                            clusterConfig.pipelineGraphConfig(),
                            updatedModuleMap,
                            clusterConfig.defaultPipelineName(),
                            clusterConfig.allowedKafkaTopics(),
                            updatedAllowedGrpcServices
                        );
                        
                        // Step 6: Save to Consul
                        return saveClusterConfig(clusterName, updatedClusterConfig)
                            .map(saved -> {
                                if (saved) {
                                    LOG.info("Successfully whitelisted module '{}' for cluster '{}'", 
                                             request.grpcServiceName(), clusterName);
                                    return ModuleWhitelistResponse.success(
                                        "Module '" + request.grpcServiceName() + 
                                        "' successfully whitelisted for cluster '" + clusterName + "'"
                                    );
                                } else {
                                    return ModuleWhitelistResponse.failure(
                                        "Failed to save updated cluster configuration"
                                    );
                                }
                            });
                    });
                });
            });
    }
    
    /**
     * Removes a module from the cluster's whitelist.
     */
    public Uni<ModuleWhitelistResponse> removeModuleFromWhitelist(String clusterName, String grpcServiceName) {
        LOG.info("Removing module '{}' from cluster '{}' whitelist", grpcServiceName, clusterName);
        
        return loadClusterConfig(clusterName)
            .chain(clusterConfig -> {
                if (clusterConfig == null) {
                    return Uni.createFrom().item(ModuleWhitelistResponse.failure(
                        "Cluster '" + clusterName + "' not found"
                    ));
                }
                
                PipelineModuleMap currentModuleMap = clusterConfig.pipelineModuleMap();
                if (currentModuleMap == null || 
                    !currentModuleMap.availableModules().containsKey(grpcServiceName)) {
                    return Uni.createFrom().item(ModuleWhitelistResponse.success(
                        "Module '" + grpcServiceName + "' is not whitelisted"
                    ));
                }
                
                // Check if module is in use
                boolean moduleInUse = isModuleInUse(clusterConfig, grpcServiceName);
                if (moduleInUse) {
                    return Uni.createFrom().item(ModuleWhitelistResponse.failure(
                        "Cannot remove module '" + grpcServiceName + 
                        "' from whitelist - it is currently used in pipeline configurations"
                    ));
                }
                
                // Remove from module map
                Map<String, PipelineModuleConfiguration> updatedModules = 
                    new HashMap<>(currentModuleMap.availableModules());
                updatedModules.remove(grpcServiceName);
                PipelineModuleMap updatedModuleMap = new PipelineModuleMap(updatedModules);
                
                // Update cluster config
                // Also remove from allowedGrpcServices
                Set<String> updatedAllowedGrpcServices = new HashSet<>(
                    clusterConfig.allowedGrpcServices() != null ? 
                    clusterConfig.allowedGrpcServices() : Set.of()
                );
                updatedAllowedGrpcServices.remove(grpcServiceName);
                
                PipelineClusterConfig updatedClusterConfig = new PipelineClusterConfig(
                    clusterConfig.clusterName(),
                    clusterConfig.pipelineGraphConfig(),
                    updatedModuleMap,
                    clusterConfig.defaultPipelineName(),
                    clusterConfig.allowedKafkaTopics(),
                    updatedAllowedGrpcServices
                );
                
                return saveClusterConfig(clusterName, updatedClusterConfig)
                    .map(saved -> {
                        if (saved) {
                            LOG.info("Successfully removed module '{}' from cluster '{}' whitelist", 
                                     grpcServiceName, clusterName);
                            return ModuleWhitelistResponse.success(
                                "Module '" + grpcServiceName + 
                                "' removed from cluster '" + clusterName + "' whitelist"
                            );
                        } else {
                            return ModuleWhitelistResponse.failure(
                                "Failed to save updated cluster configuration"
                            );
                        }
                    });
            });
    }
    
    /**
     * Lists all whitelisted modules for a cluster.
     */
    public Uni<List<PipelineModuleConfiguration>> listWhitelistedModules(String clusterName) {
        return loadClusterConfig(clusterName)
            .map(clusterConfig -> {
                if (clusterConfig == null || clusterConfig.pipelineModuleMap() == null) {
                    return List.of();
                }
                return new ArrayList<>(clusterConfig.pipelineModuleMap().availableModules().values());
            });
    }
    
    /**
     * Verifies that a module exists in Consul (has been registered at least once).
     */
    private Uni<Boolean> verifyModuleExistsInConsul(String grpcServiceName) {
        return Uni.createFrom().item(() -> {
            try {
                String url = String.format("http://%s:%s/v1/catalog/service/%s", 
                                          consulHost, consulPort, grpcServiceName);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                // If we get a 200, check if the array has any services
                if (response.statusCode() == 200) {
                    String body = response.body();
                    // Check if the response is an empty array "[]" or null
                    if (body == null || body.equals("null") || body.equals("[]")) {
                        return false;
                    }
                    // Parse the JSON to check if there are any services
                    return body.trim().length() > 2; // More than just "[]"
                }
                return false;
            } catch (Exception e) {
                LOG.warn("Failed to verify module in Consul: {}", grpcServiceName, e);
                return false;
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }
    
    /**
     * Validates all pipelines in the cluster with the updated module map.
     */
    private Uni<ValidationResult> validatePipelinesWithModuleMap(
            PipelineClusterConfig clusterConfig, 
            PipelineModuleMap moduleMap) {
        
        PipelineGraphConfig graphConfig = clusterConfig.pipelineGraphConfig();
        if (graphConfig == null || graphConfig.pipelines().isEmpty()) {
            return Uni.createFrom().item(new ValidationResult(true, List.of(), List.of()));
        }
        
        List<String> allErrors = new ArrayList<>();
        List<String> allWarnings = new ArrayList<>();
        
        // Validate each pipeline
        for (var entry : graphConfig.pipelines().entrySet()) {
            String pipelineId = entry.getKey();
            PipelineConfig pipeline = entry.getValue();
            
            // Check if all gRPC services used are in the whitelist
            List<String> errors = validatePipelineAgainstWhitelist(pipelineId, pipeline, moduleMap);
            allErrors.addAll(errors);
            
            // Also run standard pipeline validation
            ValidationResult pipelineValidation = pipelineValidator.validate(pipeline);
            allErrors.addAll(pipelineValidation.errors());
            allWarnings.addAll(pipelineValidation.warnings());
        }
        
        return Uni.createFrom().item(
            new ValidationResult(allErrors.isEmpty(), allErrors, allWarnings)
        );
    }
    
    /**
     * Validates that all gRPC services used in a pipeline are whitelisted.
     */
    private List<String> validatePipelineAgainstWhitelist(
            String pipelineId, 
            PipelineConfig pipeline, 
            PipelineModuleMap moduleMap) {
        
        List<String> errors = new ArrayList<>();
        Set<String> whitelistedModules = moduleMap.availableModules().keySet();
        
        for (var stepEntry : pipeline.pipelineSteps().entrySet()) {
            String stepId = stepEntry.getKey();
            PipelineStepConfig step = stepEntry.getValue();
            
            if (step.processorInfo() != null && 
                step.processorInfo().grpcServiceName() != null &&
                !step.processorInfo().grpcServiceName().isBlank()) {
                
                String grpcServiceName = step.processorInfo().grpcServiceName();
                if (!whitelistedModules.contains(grpcServiceName)) {
                    errors.add(String.format(
                        "Pipeline '%s', Step '%s': gRPC service '%s' is not whitelisted", 
                        pipelineId, stepId, grpcServiceName
                    ));
                }
            }
        }
        
        return errors;
    }
    
    /**
     * Checks if a module is currently used in any pipeline.
     */
    private boolean isModuleInUse(PipelineClusterConfig clusterConfig, String grpcServiceName) {
        PipelineGraphConfig graphConfig = clusterConfig.pipelineGraphConfig();
        if (graphConfig == null) {
            return false;
        }
        
        return graphConfig.pipelines().values().stream()
            .flatMap(pipeline -> pipeline.pipelineSteps().values().stream())
            .filter(step -> step.processorInfo() != null)
            .anyMatch(step -> grpcServiceName.equals(step.processorInfo().grpcServiceName()));
    }
    
    /**
     * Loads cluster configuration from Consul.
     */
    private Uni<PipelineClusterConfig> loadClusterConfig(String clusterName) {
        return Uni.createFrom().item(() -> {
            try {
                String key = String.format("%s/%s/config", KV_PREFIX, clusterName);
                String url = String.format("http://%s:%s/v1/kv/%s?raw", consulHost, consulPort, key);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return objectMapper.readValue(response.body(), PipelineClusterConfig.class);
                }
                return null;
            } catch (Exception e) {
                LOG.error("Failed to load cluster config", e);
                return null;
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }
    
    /**
     * Saves cluster configuration to Consul.
     */
    private Uni<Boolean> saveClusterConfig(String clusterName, PipelineClusterConfig config) {
        return Uni.createFrom().item(() -> {
            try {
                String key = String.format("%s/%s/config", KV_PREFIX, clusterName);
                String url = String.format("http://%s:%s/v1/kv/%s", consulHost, consulPort, key);
                
                String json = objectMapper.writeValueAsString(config);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                return response.statusCode() == 200;
            } catch (Exception e) {
                LOG.error("Failed to save cluster config", e);
                return false;
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }
}