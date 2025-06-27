package com.rokkon.pipeline.config.model;

import com.rokkon.pipeline.config.model.ClusterMetadata;
import java.time.Instant;
import java.util.Map;

/**
 * Represents a Rokkon cluster.
 */
public record Cluster(
    String name,
    String createdAt,
    ClusterMetadata metadata
) {
    
    /**
     * Creates an empty cluster with just a name for testing purposes.
     * Uses current timestamp and empty metadata.
     */
    public static Cluster emptyCluster(String name) {
        return new Cluster(
            name,
            Instant.now().toString(),
            new ClusterMetadata(name, Instant.now(), null, Map.of())
        );
    }
    
    /**
     * Creates a test cluster with basic metadata.
     */
    public static Cluster testCluster(String name, Map<String, Object> metadata) {
        return new Cluster(
            name,
            Instant.now().toString(),
            new ClusterMetadata(name, Instant.now(), null, metadata)
        );
    }
}