package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.consul.model.Cluster;
import com.rokkon.pipeline.validation.ValidationResult;
import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing clusters in Consul.
 */
public interface ClusterService {
    
    /**
     * Creates a new cluster.
     */
    Uni<ValidationResult> createCluster(String clusterName);
    
    /**
     * Gets cluster metadata.
     */
    Uni<Optional<ClusterMetadata>> getCluster(String clusterName);
    
    /**
     * Checks if a cluster exists.
     */
    Uni<Boolean> clusterExists(String clusterName);
    
    /**
     * Deletes a cluster.
     */
    Uni<ValidationResult> deleteCluster(String clusterName);
    
    /**
     * Lists all clusters.
     */
    Uni<List<Cluster>> listClusters();
}