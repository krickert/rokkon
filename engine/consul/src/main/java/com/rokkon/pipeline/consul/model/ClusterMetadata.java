package com.rokkon.pipeline.consul.model;

import java.util.Map;

/**
 * Metadata for a Rokkon cluster.
 */
public record ClusterMetadata(
    Map<String, String> metadata
) {
    public ClusterMetadata {
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}