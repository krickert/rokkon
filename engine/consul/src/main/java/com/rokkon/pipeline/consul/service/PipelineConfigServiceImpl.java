package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.config.service.PipelineConfigService;
import com.rokkon.pipeline.validation.CompositeValidator;
import com.rokkon.pipeline.validation.ValidationResult;
import com.rokkon.pipeline.validation.ValidationResultFactory;
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

/**
 * Service implementation for managing pipeline configurations in Consul KV store.
 * Provides CRUD operations with validation.
 */
@ApplicationScoped
public class PipelineConfigServiceImpl implements PipelineConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineConfigServiceImpl.class);
    
    @ConfigProperty(name = "rokkon.consul.kv-prefix", defaultValue = "rokkon")
    String kvPrefix;

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
    public Uni<ValidationResult> createPipeline(String clusterName, String pipelineId,
                                                PipelineConfig config) {
        LOG.info("Creating pipeline '{}' in cluster '{}'", pipelineId, clusterName);

        // Validate the configuration
        ValidationResult validationResult = validator.validate(config);
        if (!validationResult.valid()) {
            return Uni.createFrom().item(validationResult);
        }

        // Check if pipeline already exists
        return getPipeline(clusterName, pipelineId)
                .flatMap(existing -> {
                    if (existing.isPresent()) {
                        return Uni.createFrom().item(
                                ValidationResultFactory.failure(
                                        "Pipeline '" + pipelineId + "' already exists"));
                    }

                    return storePipelineInConsul(clusterName, pipelineId, config);
                });
    }

    /**
     * Updates an existing pipeline configuration.
     */
    public Uni<ValidationResult> updatePipeline(String clusterName, String pipelineId,
                                                PipelineConfig config) {
        LOG.info("Updating pipeline '{}' in cluster '{}'", pipelineId, clusterName);

        // Validate the configuration
        ValidationResult validationResult = validator.validate(config);
        if (!validationResult.valid()) {
            return Uni.createFrom().item(validationResult);
        }

        // Check if pipeline exists
        return getPipeline(clusterName, pipelineId)
                .flatMap(existing -> {
                    if (existing.isEmpty()) {
                        return Uni.createFrom().item(
                                ValidationResultFactory.failure(
                                        "Pipeline '" + pipelineId + "' not found"));
                    }

                    return storePipelineInConsul(clusterName, pipelineId, config);
                });
    }

    /**
     * Deletes a pipeline configuration.
     */
    public Uni<ValidationResult> deletePipeline(String clusterName, String pipelineId) {
        LOG.info("Deleting pipeline '{}' from cluster '{}'", pipelineId, clusterName);

        return getPipeline(clusterName, pipelineId)
                .flatMap(existing -> {
                    if (existing.isEmpty()) {
                        return Uni.createFrom().item(
                                ValidationResultFactory.failure(
                                        "Pipeline '" + pipelineId + "' not found"));
                    }

                    return deletePipelineFromConsul(clusterName, pipelineId);
                });
    }

    /**
     * Retrieves a pipeline configuration.
     */
    public Uni<Optional<PipelineConfig>> getPipeline(String clusterName, String pipelineId) {
        return Uni.createFrom().<Optional<PipelineConfig>>item(() -> {
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
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    /**
     * Lists all pipelines in a cluster.
     */
    public Uni<Map<String, PipelineConfig>> listPipelines(String clusterName) {
        return Uni.createFrom().item(() -> {
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
                    return keys;
                } else if (response.statusCode() == 404) {
                    return new String[0];
                } else {
                    LOG.error("Failed to list pipelines. Status: {}", response.statusCode());
                    return new String[0];
                }
            } catch (Exception e) {
                LOG.error("Error listing pipelines: {}", e.getMessage(), e);
                return new String[0];
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor())
        .flatMap(keys -> {
            List<Uni<Map.Entry<String, PipelineConfig>>> unis = new ArrayList<>();
            
            for (String key : keys) {
                String pipelineId = extractPipelineIdFromKey(key);
                if (pipelineId != null && key.endsWith("/config")) {
                    unis.add(
                        getPipeline(clusterName, pipelineId)
                            .map(configOpt -> configOpt
                                .map(config -> Map.entry(pipelineId, config))
                                .orElse(null))
                    );
                }
            }
            
            return Uni.combine().all().unis(unis)
                    .with(list -> {
                        Map<String, PipelineConfig> pipelines = new HashMap<>();
                        for (Object entry : list) {
                            if (entry != null) {
                                Map.Entry<String, PipelineConfig> e = (Map.Entry<String, PipelineConfig>) entry;
                                pipelines.put(e.getKey(), e.getValue());
                            }
                        }
                        return pipelines;
                    });
        });
    }

    private Uni<ValidationResult> storePipelineInConsul(String clusterName, String pipelineId,
                                                        PipelineConfig config) {
        return Uni.createFrom().item(() -> {
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
                    return ValidationResultFactory.success();
                } else {
                    LOG.error("Failed to store pipeline. Status: {}", response.statusCode());
                    return ValidationResultFactory.failure(
                            "Failed to store pipeline in Consul");
                }
            } catch (Exception e) {
                LOG.error("Error storing pipeline: {}", e.getMessage(), e);
                return ValidationResultFactory.failure(
                        "Error storing pipeline: " + e.getMessage());
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    private Uni<ValidationResult> deletePipelineFromConsul(String clusterName, String pipelineId) {
        return Uni.createFrom().item(() -> {
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
                    return ValidationResultFactory.success();
                } else {
                    LOG.error("Failed to delete pipeline. Status: {}", response.statusCode());
                    return ValidationResultFactory.failure(
                            "Failed to delete pipeline from Consul");
                }
            } catch (Exception e) {
                LOG.error("Error deleting pipeline: {}", e.getMessage(), e);
                return ValidationResultFactory.failure(
                        "Error deleting pipeline: " + e.getMessage());
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    private String buildPipelineKey(String clusterName, String pipelineId) {
        return kvPrefix + "/clusters/" + clusterName + "/pipelines/" + pipelineId + "/config";
    }

    private String buildClusterPrefix(String clusterName) {
        return kvPrefix + "/clusters/" + clusterName + "/";
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
