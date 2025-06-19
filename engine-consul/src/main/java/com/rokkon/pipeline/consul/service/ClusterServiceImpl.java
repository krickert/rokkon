package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.consul.model.Cluster;
import com.rokkon.pipeline.validation.ValidationResult;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class ClusterServiceImpl implements ClusterService {
    private static final Logger LOG = Logger.getLogger(ClusterServiceImpl.class);
    private static final String CLUSTERS_PREFIX = "rokkon-clusters";
    
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String consulHost;
    
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    String consulPort;
    
    @Inject
    ObjectMapper objectMapper;
    
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    public Uni<ValidationResult> createCluster(String clusterName) {
        LOG.infof("Creating cluster: %s", clusterName);
        
        // Validate cluster name
        if (clusterName == null || clusterName.trim().isEmpty()) {
            return Uni.createFrom().item(
                ValidationResult.failure("Cluster name cannot be empty")
            );
        }
        
        // Check if cluster already exists
        return clusterExists(clusterName)
            .flatMap(exists -> {
                if (exists) {
                    return Uni.createFrom().item(
                        ValidationResult.failure("Cluster '" + clusterName + "' already exists")
                    );
                }
                
                // Create cluster metadata
                ClusterMetadata metadata = new ClusterMetadata(
                    clusterName,
                    Instant.now(),
                    null,
                    Map.of("status", "active", "version", "1.0")
                );
                
                // Store metadata first
                return storeClusterMetadata(clusterName, metadata)
                    .flatMap(result -> {
                        if (!result.valid()) {
                            return Uni.createFrom().item(result);
                        }
                        
                        // Create initial PipelineClusterConfig
                        return createInitialClusterConfig(clusterName);
                    });
            });
    }
    
    public Uni<Optional<ClusterMetadata>> getCluster(String clusterName) {
        return Uni.createFrom().item(() -> {
            try {
                String key = buildClusterKey(clusterName) + "/metadata";
                String url = getConsulUrl() + "/v1/kv/" + key + "?raw";
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
                    
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 404) {
                    return Optional.<ClusterMetadata>empty();
                }
                
                if (response.statusCode() != 200) {
                    LOG.errorf("Failed to get cluster: %d - %s", response.statusCode(), response.body());
                    return Optional.<ClusterMetadata>empty();
                }
                
                ClusterMetadata metadata = objectMapper.readValue(response.body(), ClusterMetadata.class);
                return Optional.of(metadata);
            } catch (Exception e) {
                LOG.error("Failed to get cluster metadata", e);
                return Optional.<ClusterMetadata>empty();
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }
    
    public Uni<Boolean> clusterExists(String clusterName) {
        return getCluster(clusterName).map(Optional::isPresent);
    }
    
    public Uni<ValidationResult> deleteCluster(String clusterName) {
        return Uni.createFrom().item(() -> {
            try {
                String key = buildClusterKey(clusterName);
                String url = getConsulUrl() + "/v1/kv/" + key + "?recurse";
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .DELETE()
                    .build();
                    
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    LOG.infof("Deleted cluster: %s", clusterName);
                    return ValidationResult.success();
                } else {
                    return ValidationResult.failure("Failed to delete cluster: " + response.statusCode());
                }
            } catch (Exception e) {
                LOG.error("Failed to delete cluster", e);
                return ValidationResult.failure("Failed to delete cluster: " + e.getMessage());
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }
    
    public Uni<List<Cluster>> listClusters() {
        return Uni.createFrom().item(() -> {
            try {
                String url = getConsulUrl() + "/v1/kv/" + CLUSTERS_PREFIX + "/?keys";
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
                    
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 404) {
                    // No clusters exist yet
                    return new ArrayList<Cluster>();
                }
                
                if (response.statusCode() != 200) {
                    LOG.errorf("Failed to list clusters: %d - %s", response.statusCode(), response.body());
                    return new ArrayList<Cluster>();
                }
                
                // Parse the JSON array of keys
                List<String> keys = objectMapper.readValue(response.body(), 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                
                List<Cluster> clusters = new ArrayList<>();
                
                // Extract cluster names from keys
                for (String key : keys) {
                    // Keys are in format: rokkon-clusters/{cluster-name}/...
                    String[] parts = key.split("/");
                    if (parts.length >= 2 && !clusters.stream().anyMatch(c -> c.name().equals(parts[1]))) {
                        // Get the cluster metadata
                        Optional<ClusterMetadata> metadata = getCluster(parts[1]).await().indefinitely();
                        if (metadata.isPresent()) {
                            // Convert Map<String, Object> to Map<String, String>
                            Map<String, String> metadataStrings = new java.util.HashMap<>();
                            metadata.get().metadata().forEach((k, v) -> metadataStrings.put(k, String.valueOf(v)));
                            
                            clusters.add(new Cluster(
                                parts[1],
                                metadata.get().createdAt().toString(),
                                new com.rokkon.pipeline.consul.model.ClusterMetadata(metadataStrings)
                            ));
                        }
                    }
                }
                
                return clusters;
            } catch (Exception e) {
                LOG.error("Failed to list clusters", e);
                return new ArrayList<Cluster>();
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }
    
    private Uni<ValidationResult> storeClusterMetadata(String clusterName, ClusterMetadata metadata) {
        return Uni.createFrom().item(() -> {
            try {
                String json = objectMapper.writeValueAsString(metadata);
                String key = buildClusterKey(clusterName) + "/metadata";
                String url = getConsulUrl() + "/v1/kv/" + key;
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();
                    
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    LOG.infof("Created cluster: %s", clusterName);
                    return ValidationResult.success();
                } else {
                    return ValidationResult.failure("Failed to create cluster: " + response.statusCode());
                }
            } catch (Exception e) {
                LOG.error("Failed to store cluster metadata", e);
                return ValidationResult.failure("Failed to store cluster metadata: " + e.getMessage());
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }
    
    private String buildClusterKey(String clusterName) {
        return CLUSTERS_PREFIX + "/" + clusterName;
    }
    
    private String getConsulUrl() {
        return "http://" + consulHost + ":" + consulPort;
    }
    
    private Uni<ValidationResult> createInitialClusterConfig(String clusterName) {
        return Uni.createFrom().item(() -> {
            try {
                // Create initial empty PipelineClusterConfig
                PipelineGraphConfig emptyGraph = new PipelineGraphConfig(Map.of());
                PipelineModuleMap emptyModuleMap = new PipelineModuleMap(Map.of());
                
                PipelineClusterConfig initialConfig = new PipelineClusterConfig(
                    clusterName,
                    emptyGraph,
                    emptyModuleMap,
                    null,  // no default pipeline
                    Set.of(),  // no allowed Kafka topics yet
                    Set.of()   // no allowed gRPC services yet
                );
                
                String json = objectMapper.writeValueAsString(initialConfig);
                String key = buildClusterKey(clusterName) + "/config";
                String url = getConsulUrl() + "/v1/kv/" + key;
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();
                    
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    LOG.infof("Created initial config for cluster: %s", clusterName);
                    return ValidationResult.success();
                } else {
                    return ValidationResult.failure("Failed to create cluster config: " + response.statusCode());
                }
            } catch (Exception e) {
                LOG.error("Failed to create initial cluster config", e);
                return ValidationResult.failure("Failed to create cluster config: " + e.getMessage());
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }
}