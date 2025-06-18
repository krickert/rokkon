package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.consul.model.Cluster;
import com.rokkon.pipeline.consul.model.ClusterMetadata;
import com.rokkon.pipeline.validation.ValidationResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ClusterService {
    private static final Logger LOG = Logger.getLogger(ClusterService.class);
    private static final String CLUSTERS_PREFIX = "rokkon-clusters";
    
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String consulHost;
    
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    String consulPort;
    
    @Inject
    ObjectMapper objectMapper;
    
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    public record ClusterMetadata(
        String name,
        Instant createdAt,
        String defaultPipeline,
        Map<String, Object> metadata
    ) {}
    
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
                
                // Store in Consul
                return storeClusterMetadata(clusterName, metadata);
            });
    }
    
    public Uni<Optional<ClusterMetadata>> getCluster(String clusterName) {
        String key = buildClusterKey(clusterName) + "/metadata";
        String url = getConsulUrl() + "/v1/kv/" + key + "?raw";
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
            
        return Uni.createFrom().completionStage(
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        ).map(response -> {
            if (response.statusCode() == 404) {
                return Optional.<ClusterMetadata>empty();
            }
            
            if (response.statusCode() != 200) {
                LOG.errorf("Failed to get cluster: %d - %s", response.statusCode(), response.body());
                return Optional.<ClusterMetadata>empty();
            }
            
            try {
                ClusterMetadata metadata = objectMapper.readValue(response.body(), ClusterMetadata.class);
                return Optional.of(metadata);
            } catch (Exception e) {
                LOG.error("Failed to parse cluster metadata", e);
                return Optional.<ClusterMetadata>empty();
            }
        });
    }
    
    public Uni<Boolean> clusterExists(String clusterName) {
        return getCluster(clusterName).map(Optional::isPresent);
    }
    
    public Uni<ValidationResult> deleteCluster(String clusterName) {
        String key = buildClusterKey(clusterName);
        String url = getConsulUrl() + "/v1/kv/" + key + "?recurse";
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .DELETE()
            .build();
            
        return Uni.createFrom().completionStage(
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        ).map(response -> {
            if (response.statusCode() == 200) {
                LOG.infof("Deleted cluster: %s", clusterName);
                return ValidationResult.success();
            } else {
                return ValidationResult.failure("Failed to delete cluster: " + response.statusCode());
            }
        });
    }
    
    private Uni<ValidationResult> storeClusterMetadata(String clusterName, ClusterMetadata metadata) {
        try {
            String json = objectMapper.writeValueAsString(metadata);
            String key = buildClusterKey(clusterName) + "/metadata";
            String url = getConsulUrl() + "/v1/kv/" + key;
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
                
            return Uni.createFrom().completionStage(
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            ).map(response -> {
                if (response.statusCode() == 200) {
                    LOG.infof("Created cluster: %s", clusterName);
                    return ValidationResult.success();
                } else {
                    return ValidationResult.failure("Failed to create cluster: " + response.statusCode());
                }
            });
        } catch (Exception e) {
            LOG.error("Failed to serialize cluster metadata", e);
            return Uni.createFrom().item(
                ValidationResult.failure("Failed to serialize cluster metadata: " + e.getMessage())
            );
        }
    }
    
    private String buildClusterKey(String clusterName) {
        return CLUSTERS_PREFIX + "/" + clusterName;
    }
    
    private String getConsulUrl() {
        return "http://" + consulHost + ":" + consulPort;
    }
}