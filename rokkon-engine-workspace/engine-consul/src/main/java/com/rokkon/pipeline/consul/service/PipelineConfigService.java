package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.validation.CompositeValidator;
import com.rokkon.pipeline.validation.ValidationResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Service for managing pipeline configurations in Consul KV store.
 * Provides CRUD operations with validation.
 */
@ApplicationScoped
public class PipelineConfigService {
    
    private static final Logger LOG = LoggerFactory.getLogger(PipelineConfigService.class);
    private static final String KV_PREFIX = "rokkon-clusters";
    
    @Inject
    ObjectMapper objectMapper;
    
    @Inject
    CompositeValidator<PipelineConfig> validator;
    
    @Inject
    ClusterService clusterService;
    
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String consulHost;
    
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    String consulPort;
    
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    /**
     * Creates a new pipeline configuration in Consul.
     */
    public CompletionStage<ValidationResult> createPipeline(String clusterName, String pipelineId, 
                                                           PipelineConfig config) {
        LOG.info("Creating pipeline '{}' in cluster '{}'", pipelineId, clusterName);
        
        // Validate the configuration
        ValidationResult validationResult = validator.validate(config);
        if (!validationResult.valid()) {
            return CompletableFuture.completedFuture(validationResult);
        }
        
        // Check if pipeline already exists
        return getPipeline(clusterName, pipelineId)
                .thenCompose(existing -> {
                    if (existing.isPresent()) {
                        return CompletableFuture.completedFuture(
                                new ValidationResult(false, 
                                        List.of("Pipeline '" + pipelineId + "' already exists"), 
                                        List.of()));
                    }
                    
                    return storePipelineInConsul(clusterName, pipelineId, config);
                });
    }
    
    /**
     * Updates an existing pipeline configuration.
     */
    public CompletionStage<ValidationResult> updatePipeline(String clusterName, String pipelineId,
                                                           PipelineConfig config) {
        LOG.info("Updating pipeline '{}' in cluster '{}'", pipelineId, clusterName);
        
        // Validate the configuration
        ValidationResult validationResult = validator.validate(config);
        if (!validationResult.valid()) {
            return CompletableFuture.completedFuture(validationResult);
        }
        
        // Check if pipeline exists
        return getPipeline(clusterName, pipelineId)
                .thenCompose(existing -> {
                    if (existing.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new ValidationResult(false, 
                                        List.of("Pipeline '" + pipelineId + "' not found"), 
                                        List.of()));
                    }
                    
                    return storePipelineInConsul(clusterName, pipelineId, config);
                });
    }
    
    /**
     * Deletes a pipeline configuration.
     */
    public CompletionStage<ValidationResult> deletePipeline(String clusterName, String pipelineId) {
        LOG.info("Deleting pipeline '{}' from cluster '{}'", pipelineId, clusterName);
        
        return getPipeline(clusterName, pipelineId)
                .thenCompose(existing -> {
                    if (existing.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new ValidationResult(false, 
                                        List.of("Pipeline '" + pipelineId + "' not found"), 
                                        List.of()));
                    }
                    
                    return deletePipelineFromConsul(clusterName, pipelineId);
                });
    }
    
    /**
     * Retrieves a pipeline configuration.
     */
    public CompletionStage<Optional<PipelineConfig>> getPipeline(String clusterName, String pipelineId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String key = buildPipelineKey(clusterName, pipelineId);
                String url = getConsulBaseUrl() + "/v1/kv/" + key + "?raw";
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    PipelineConfig config = objectMapper.readValue(response.body(), PipelineConfig.class);
                    return Optional.of(config);
                } else if (response.statusCode() == 404) {
                    return Optional.empty();
                } else {
                    LOG.error("Failed to retrieve pipeline. Status: {}", response.statusCode());
                    return Optional.empty();
                }
            } catch (Exception e) {
                LOG.error("Error retrieving pipeline '{}': {}", pipelineId, e.getMessage(), e);
                return Optional.empty();
            }
        });
    }
    
    /**
     * Lists all pipelines in a cluster.
     */
    public CompletionStage<Map<String, PipelineConfig>> listPipelines(String clusterName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prefix = buildClusterPrefix(clusterName) + "pipelines/";
                String url = getConsulBaseUrl() + "/v1/kv/" + prefix + "?keys";
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    String[] keys = objectMapper.readValue(response.body(), String[].class);
                    Map<String, PipelineConfig> pipelines = new HashMap<>();
                    
                    for (String key : keys) {
                        String pipelineId = extractPipelineIdFromKey(key);
                        if (pipelineId != null && key.endsWith("/config")) {
                            Optional<PipelineConfig> config = getPipeline(clusterName, pipelineId)
                                    .toCompletableFuture().join();
                            config.ifPresent(c -> pipelines.put(pipelineId, c));
                        }
                    }
                    
                    return pipelines;
                } else if (response.statusCode() == 404) {
                    return Collections.emptyMap();
                } else {
                    LOG.error("Failed to list pipelines. Status: {}", response.statusCode());
                    return Collections.emptyMap();
                }
            } catch (Exception e) {
                LOG.error("Error listing pipelines: {}", e.getMessage(), e);
                return Collections.emptyMap();
            }
        });
    }
    
    private CompletionStage<ValidationResult> storePipelineInConsul(String clusterName, String pipelineId, 
                                                                    PipelineConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String configJson = objectMapper.writeValueAsString(config);
                String key = buildPipelineKey(clusterName, pipelineId);
                String url = getConsulBaseUrl() + "/v1/kv/" + key;
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .PUT(HttpRequest.BodyPublishers.ofString(configJson))
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    LOG.info("Successfully stored pipeline '{}' in Consul", pipelineId);
                    return new ValidationResult(true, List.of(), List.of());
                } else {
                    LOG.error("Failed to store pipeline. Status: {}", response.statusCode());
                    return new ValidationResult(false, 
                            List.of("Failed to store pipeline in Consul"), 
                            List.of());
                }
            } catch (Exception e) {
                LOG.error("Error storing pipeline: {}", e.getMessage(), e);
                return new ValidationResult(false, 
                        List.of("Error storing pipeline: " + e.getMessage()), 
                        List.of());
            }
        });
    }
    
    private CompletionStage<ValidationResult> deletePipelineFromConsul(String clusterName, String pipelineId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String key = buildPipelineKey(clusterName, pipelineId);
                String url = getConsulBaseUrl() + "/v1/kv/" + key;
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .DELETE()
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    LOG.info("Successfully deleted pipeline '{}' from Consul", pipelineId);
                    return new ValidationResult(true, List.of(), List.of());
                } else {
                    LOG.error("Failed to delete pipeline. Status: {}", response.statusCode());
                    return new ValidationResult(false, 
                            List.of("Failed to delete pipeline from Consul"), 
                            List.of());
                }
            } catch (Exception e) {
                LOG.error("Error deleting pipeline: {}", e.getMessage(), e);
                return new ValidationResult(false, 
                        List.of("Error deleting pipeline: " + e.getMessage()), 
                        List.of());
            }
        });
    }
    
    private String buildPipelineKey(String clusterName, String pipelineId) {
        return KV_PREFIX + "/" + clusterName + "/pipelines/" + pipelineId + "/config";
    }
    
    private String buildClusterPrefix(String clusterName) {
        return KV_PREFIX + "/" + clusterName + "/";
    }
    
    private String extractPipelineIdFromKey(String key) {
        // Extract from: rokkon-clusters/{cluster}/pipelines/{pipelineId}/config
        String[] parts = key.split("/");
        if (parts.length >= 5 && "pipelines".equals(parts[2])) {
            return parts[3];
        }
        return null;
    }
    
    private String getConsulBaseUrl() {
        return "http://" + consulHost + ":" + consulPort;
    }
}