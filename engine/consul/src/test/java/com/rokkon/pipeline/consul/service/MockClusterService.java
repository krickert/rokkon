package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.model.Cluster;
import com.rokkon.pipeline.config.model.ClusterMetadata;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.validation.ValidationResult;
import com.rokkon.pipeline.validation.ValidationResultFactory;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of ClusterService for testing.
 * Provides in-memory storage of clusters.
 */
@Alternative
@ApplicationScoped
public class MockClusterService implements ClusterService {
    private static final Logger LOG = Logger.getLogger(MockClusterService.class);
    
    private final Map<String, ClusterMetadata> clusters = new ConcurrentHashMap<>();
    
    @Override
    public Uni<ValidationResult> createCluster(String clusterName) {
        LOG.infof("Mock createCluster: %s", clusterName);
        
        if (clusterName == null || clusterName.trim().isEmpty()) {
            return Uni.createFrom().item(
                ValidationResultFactory.failure("Cluster name cannot be empty")
            );
        }
        
        if (clusters.containsKey(clusterName)) {
            return Uni.createFrom().item(
                ValidationResultFactory.failure("Cluster '" + clusterName + "' already exists")
            );
        }
        
        // Create cluster metadata
        ClusterMetadata metadata = new ClusterMetadata(
            clusterName,
            Instant.now(),
            null, // defaultPipeline
            Map.of(
                "status", "active",
                "created", Instant.now().toString()
            )
        );
        
        clusters.put(clusterName, metadata);
        
        return Uni.createFrom().item(ValidationResultFactory.success());
    }
    
    @Override
    public Uni<Optional<ClusterMetadata>> getCluster(String clusterName) {
        LOG.infof("Mock getCluster: %s", clusterName);
        
        ClusterMetadata metadata = clusters.get(clusterName);
        return Uni.createFrom().item(Optional.ofNullable(metadata));
    }
    
    @Override
    public Uni<Boolean> clusterExists(String clusterName) {
        LOG.infof("Mock clusterExists: %s", clusterName);
        
        return Uni.createFrom().item(clusters.containsKey(clusterName));
    }
    
    @Override
    public Uni<ValidationResult> deleteCluster(String clusterName) {
        LOG.infof("Mock deleteCluster: %s", clusterName);
        
        if (!clusters.containsKey(clusterName)) {
            return Uni.createFrom().item(
                ValidationResultFactory.failure("Cluster '" + clusterName + "' does not exist")
            );
        }
        
        clusters.remove(clusterName);
        
        return Uni.createFrom().item(ValidationResultFactory.success());
    }
    
    @Override
    public Uni<List<Cluster>> listClusters() {
        LOG.infof("Mock listClusters");
        
        List<Cluster> clusterList = new ArrayList<>();
        for (ClusterMetadata metadata : clusters.values()) {
            // Create a simple Cluster object from metadata
            Cluster cluster = new Cluster(
                metadata.name(),
                metadata.createdAt().toString(),
                metadata
            );
            clusterList.add(cluster);
        }
        
        return Uni.createFrom().item(clusterList);
    }
    
    // Helper method for testing
    public void clear() {
        clusters.clear();
    }
}