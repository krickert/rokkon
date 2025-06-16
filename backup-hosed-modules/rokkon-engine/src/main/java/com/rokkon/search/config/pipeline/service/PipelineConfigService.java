package com.rokkon.search.config.pipeline.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rokkon.search.config.pipeline.model.*;
import com.rokkon.search.config.pipeline.service.events.ConfigChangeEvent;
import com.rokkon.search.config.pipeline.service.validation.ValidationResult;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Main service for CRUD operations on pipeline configurations.
 * Implements per-pipeline storage in Consul with comprehensive validation.
 */
@ApplicationScoped
@RegisterForReflection
public class PipelineConfigService {
    
    private static final Logger LOG = LoggerFactory.getLogger(PipelineConfigService.class);
    
    private final ObjectMapper objectMapper;
    private final ConfigValidationService validationService;
    private final SchemaValidationService schemaValidationService;
    private final Event<ConfigChangeEvent> configChangeEvent;
    private final HttpClient httpClient;
    
    // Consul configuration
    private final String consulHost;
    private final String consulPort;
    private final String consulBaseUrl;
    
    @Inject
    public PipelineConfigService(
            ObjectMapper objectMapper,
            ConfigValidationService validationService,
            SchemaValidationService schemaValidationService,
            Event<ConfigChangeEvent> configChangeEvent,
            @org.eclipse.microprofile.config.inject.ConfigProperty(name = "quarkus.consul-config.agent.host-port") 
            Optional<String> consulConfig) {
        
        this.objectMapper = objectMapper;
        this.validationService = validationService;
        this.schemaValidationService = schemaValidationService;
        this.configChangeEvent = configChangeEvent;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        // Parse Consul configuration
        if (consulConfig.isPresent()) {
            String[] hostPort = consulConfig.get().split(":");
            this.consulHost = hostPort[0];
            this.consulPort = hostPort.length > 1 ? hostPort[1] : "8500";
        } else {
            this.consulHost = "localhost";
            this.consulPort = "8500";
        }
        this.consulBaseUrl = "http://" + consulHost + ":" + consulPort;
        
        LOG.info("PipelineConfigService initialized with Consul at: {}", consulBaseUrl);
    }
    
    /**
     * Creates a new pipeline in the specified cluster.
     */
    public CompletionStage<ValidationResult> createPipeline(String clusterName, String pipelineId, 
                                                           PipelineConfig config, String initiatedBy) {
        LOG.info("Creating pipeline '{}' in cluster '{}'", pipelineId, clusterName);
        
        return validatePipelineForCreation(clusterName, pipelineId, config)
                .thenCompose(validationResult -> {
                    if (!validationResult.valid()) {
                        return CompletableFuture.completedFuture(validationResult);
                    }
                    
                    return storePipelineInConsul(clusterName, pipelineId, config)
                            .thenApply(success -> {
                                if (success) {
                                    // Fire creation event
                                    configChangeEvent.fire(ConfigChangeEvent.pipelineCreated(
                                            clusterName, pipelineId, config, initiatedBy));
                                    
                                    LOG.info("Successfully created pipeline '{}' in cluster '{}'", pipelineId, clusterName);
                                    return ValidationResult.success();
                                } else {
                                    return ValidationResult.failure("Failed to store pipeline in Consul");
                                }
                            });
                });
    }
    
    /**
     * Updates an existing pipeline.
     */
    public CompletionStage<ValidationResult> updatePipeline(String clusterName, String pipelineId,
                                                           PipelineConfig newConfig, String initiatedBy) {
        LOG.info("Updating pipeline '{}' in cluster '{}'", pipelineId, clusterName);
        
        return getPipeline(clusterName, pipelineId)
                .thenCompose(oldConfig -> {
                    if (oldConfig.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                ValidationResult.failure("Pipeline '" + pipelineId + "' not found"));
                    }
                    
                    return validatePipelineForUpdate(clusterName, pipelineId, newConfig, oldConfig.get())
                            .thenCompose(validationResult -> {
                                if (!validationResult.valid()) {
                                    return CompletableFuture.completedFuture(validationResult);
                                }
                                
                                return storePipelineInConsul(clusterName, pipelineId, newConfig)
                                        .thenApply(success -> {
                                            if (success) {
                                                // Fire update event
                                                configChangeEvent.fire(ConfigChangeEvent.pipelineUpdated(
                                                        clusterName, pipelineId, newConfig, oldConfig.get(), initiatedBy));
                                                
                                                LOG.info("Successfully updated pipeline '{}' in cluster '{}'", pipelineId, clusterName);
                                                return ValidationResult.success();
                                            } else {
                                                return ValidationResult.failure("Failed to update pipeline in Consul");
                                            }
                                        });
                            });
                });
    }
    
    /**
     * Deletes a pipeline.
     */
    public CompletionStage<ValidationResult> deletePipeline(String clusterName, String pipelineId, String initiatedBy) {
        LOG.info("Deleting pipeline '{}' from cluster '{}'", pipelineId, clusterName);
        
        return getPipeline(clusterName, pipelineId)
                .thenCompose(oldConfig -> {
                    if (oldConfig.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                ValidationResult.failure("Pipeline '" + pipelineId + "' not found"));
                    }
                    
                    return deletePipelineFromConsul(clusterName, pipelineId)
                            .thenApply(success -> {
                                if (success) {
                                    // Fire deletion event
                                    configChangeEvent.fire(ConfigChangeEvent.pipelineDeleted(
                                            clusterName, pipelineId, oldConfig.get(), initiatedBy));
                                    
                                    LOG.info("Successfully deleted pipeline '{}' from cluster '{}'", pipelineId, clusterName);
                                    return ValidationResult.success();
                                } else {
                                    return ValidationResult.failure("Failed to delete pipeline from Consul");
                                }
                            });
                });
    }
    
    /**
     * Gets a pipeline configuration.
     */
    public CompletionStage<Optional<PipelineConfig>> getPipeline(String clusterName, String pipelineId) {
        return retrievePipelineFromConsul(clusterName, pipelineId);
    }
    
    /**
     * Lists all pipelines in a cluster.
     */
    public CompletionStage<Map<String, PipelineConfig>> listPipelines(String clusterName) {
        return listPipelinesFromConsul(clusterName);
    }
    
    /**
     * Analyzes the impact of deleting a pipeline or module.
     */
    public CompletionStage<DependencyImpactAnalysis> analyzeDeletionImpact(String clusterName, String targetId, 
                                                                           DependencyType targetType) {
        LOG.info("Analyzing deletion impact for {} '{}' in cluster '{}'", targetType, targetId, clusterName);
        
        return listPipelines(clusterName)
                .thenApply(pipelines -> {
                    List<String> affectedPipelines = new ArrayList<>();
                    List<String> orphanedTopics = new ArrayList<>();
                    List<String> orphanedServices = new ArrayList<>();
                    
                    for (var entry : pipelines.entrySet()) {
                        String pipelineId = entry.getKey();
                        PipelineConfig pipeline = entry.getValue();
                        
                        if (targetType == DependencyType.PIPELINE && targetId.equals(pipelineId)) {
                            continue; // Skip the target pipeline itself
                        }
                        
                        boolean isAffected = checkPipelineAffectedByDeletion(pipeline, targetId, targetType);
                        if (isAffected) {
                            affectedPipelines.add(pipelineId);
                        }
                    }
                    
                    return new DependencyImpactAnalysis(
                            targetId,
                            targetType,
                            affectedPipelines,
                            orphanedTopics,
                            orphanedServices,
                            affectedPipelines.isEmpty()
                    );
                });
    }
    
    /**
     * Canonicalizes JSON for consistent ordering and hashing.
     * Based on the pattern from the old ConsulModuleRegistrationService.
     */
    public String canonicalizeJson(String jsonString) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonString);
        return canonicalizeJsonNode(node);
    }
    
    private String canonicalizeJsonNode(JsonNode node) throws JsonProcessingException {
        if (node.isObject()) {
            ObjectNode sortedNode = objectMapper.createObjectNode();
            List<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldNames::add);
            Collections.sort(fieldNames);
            
            for (String fieldName : fieldNames) {
                JsonNode childNode = node.get(fieldName);
                if (childNode.isObject() || childNode.isArray()) {
                    sortedNode.set(fieldName, objectMapper.readTree(canonicalizeJsonNode(childNode)));
                } else {
                    sortedNode.set(fieldName, childNode);
                }
            }
            return objectMapper.writeValueAsString(sortedNode);
        }
        
        return objectMapper.writeValueAsString(node);
    }
    
    /**
     * Generates an MD5 digest for configuration versioning.
     */
    public String generateConfigDigest(String configJson) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(configJson.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }
    
    // Private helper methods
    
    private CompletionStage<ValidationResult> validatePipelineForCreation(String clusterName, String pipelineId, 
                                                                         PipelineConfig config) {
        return validationService.validatePipelineStructure(pipelineId, config)
                .thenCompose(structuralResult -> {
                    if (!structuralResult.valid()) {
                        return CompletableFuture.completedFuture(structuralResult);
                    }
                    
                    // JSON Schema validation
                    try {
                        String configJson = objectMapper.writeValueAsString(config);
                        return schemaValidationService.validatePipelineStepConfig(configJson)
                                .thenApply(schemaResult -> structuralResult.combine(schemaResult));
                    } catch (JsonProcessingException e) {
                        return CompletableFuture.completedFuture(
                                ValidationResult.failure("Failed to serialize pipeline config: " + e.getMessage()));
                    }
                });
    }
    
    private CompletionStage<ValidationResult> validatePipelineForUpdate(String clusterName, String pipelineId,
                                                                       PipelineConfig newConfig, PipelineConfig oldConfig) {
        // Same validation as creation for now, but could add update-specific checks
        return validatePipelineForCreation(clusterName, pipelineId, newConfig);
    }
    
    private CompletionStage<Boolean> storePipelineInConsul(String clusterName, String pipelineId, PipelineConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String configJson = canonicalizeJson(objectMapper.writeValueAsString(config));
                String consulKey = buildPipelineKey(clusterName, pipelineId);
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(consulBaseUrl + "/v1/kv/" + consulKey))
                        .PUT(HttpRequest.BodyPublishers.ofString(configJson))
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                boolean success = response.statusCode() == 200;
                
                if (success) {
                    LOG.debug("Stored pipeline '{}' in Consul at key '{}'", pipelineId, consulKey);
                } else {
                    LOG.error("Failed to store pipeline '{}' in Consul. Status: {}, Body: {}", 
                            pipelineId, response.statusCode(), response.body());
                }
                
                return success;
            } catch (Exception e) {
                LOG.error("Error storing pipeline '{}' in Consul: {}", pipelineId, e.getMessage(), e);
                return false;
            }
        });
    }
    
    private CompletionStage<Optional<PipelineConfig>> retrievePipelineFromConsul(String clusterName, String pipelineId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String consulKey = buildPipelineKey(clusterName, pipelineId);
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(consulBaseUrl + "/v1/kv/" + consulKey + "?raw"))
                        .GET()
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    PipelineConfig config = objectMapper.readValue(response.body(), PipelineConfig.class);
                    return Optional.of(config);
                } else if (response.statusCode() == 404) {
                    return Optional.<PipelineConfig>empty();
                } else {
                    LOG.error("Failed to retrieve pipeline '{}' from Consul. Status: {}", pipelineId, response.statusCode());
                    return Optional.<PipelineConfig>empty();
                }
            } catch (Exception e) {
                LOG.error("Error retrieving pipeline '{}' from Consul: {}", pipelineId, e.getMessage(), e);
                return Optional.<PipelineConfig>empty();
            }
        });
    }
    
    private CompletionStage<Boolean> deletePipelineFromConsul(String clusterName, String pipelineId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String consulKey = buildPipelineKey(clusterName, pipelineId);
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(consulBaseUrl + "/v1/kv/" + consulKey))
                        .DELETE()
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                boolean success = response.statusCode() == 200;
                
                if (success) {
                    LOG.debug("Deleted pipeline '{}' from Consul at key '{}'", pipelineId, consulKey);
                } else {
                    LOG.error("Failed to delete pipeline '{}' from Consul. Status: {}", pipelineId, response.statusCode());
                }
                
                return success;
            } catch (Exception e) {
                LOG.error("Error deleting pipeline '{}' from Consul: {}", pipelineId, e.getMessage(), e);
                return false;
            }
        });
    }
    
    private CompletionStage<Map<String, PipelineConfig>> listPipelinesFromConsul(String clusterName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String consulPrefix = buildClusterPrefix(clusterName) + "pipelines/";
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(consulBaseUrl + "/v1/kv/" + consulPrefix + "?keys"))
                        .GET()
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    String[] keys = objectMapper.readValue(response.body(), String[].class);
                    Map<String, PipelineConfig> pipelines = new HashMap<>();
                    
                    for (String key : keys) {
                        String pipelineId = extractPipelineIdFromKey(key);
                        if (pipelineId != null) {
                            Optional<PipelineConfig> config = retrievePipelineFromConsul(clusterName, pipelineId).toCompletableFuture().join();
                            config.ifPresent(c -> pipelines.put(pipelineId, c));
                        }
                    }
                    
                    return pipelines;
                } else if (response.statusCode() == 404) {
                    return new HashMap<String, PipelineConfig>();
                } else {
                    LOG.error("Failed to list pipelines from Consul. Status: {}", response.statusCode());
                    return new HashMap<String, PipelineConfig>();
                }
            } catch (Exception e) {
                LOG.error("Error listing pipelines from Consul: {}", e.getMessage(), e);
                return new HashMap<String, PipelineConfig>();
            }
        });
    }
    
    private boolean checkPipelineAffectedByDeletion(PipelineConfig pipeline, String targetId, DependencyType targetType) {
        if (pipeline.pipelineSteps() == null) {
            return false;
        }
        
        for (PipelineStepConfig step : pipeline.pipelineSteps().values()) {
            if (targetType == DependencyType.MODULE && step.processorInfo() != null && 
                targetId.equals(step.processorInfo().grpcServiceName())) {
                return true;
            }
            
            if (step.outputs() != null) {
                for (var output : step.outputs().values()) {
                    if (output.grpcTransport() != null && 
                        targetId.equals(output.grpcTransport().serviceName())) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    private String buildPipelineKey(String clusterName, String pipelineId) {
        return buildClusterPrefix(clusterName) + "pipelines/" + pipelineId + "/config";
    }
    
    private String buildClusterPrefix(String clusterName) {
        return "rokkon-clusters/" + clusterName + "/";
    }
    
    private String extractPipelineIdFromKey(String key) {
        // Extract from: rokkon-clusters/{cluster}/pipelines/{pipelineId}/config
        String[] parts = key.split("/");
        if (parts.length >= 4 && "pipelines".equals(parts[2])) {
            return parts[3];
        }
        return null;
    }
    
    // Supporting classes
    
    public record DependencyImpactAnalysis(
            String targetId,
            DependencyType targetType,
            List<String> affectedPipelines,
            List<String> orphanedTopics,
            List<String> orphanedServices,
            boolean canSafelyDelete
    ) {}
    
    public enum DependencyType {
        PIPELINE, MODULE, SCHEMA
    }
}