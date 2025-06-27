package com.rokkon.pipeline.config.model;

import com.rokkon.pipeline.config.model.ClusterMetadata;

/**
 * Represents a Rokkon cluster.
 */
public record Cluster(
    String name,
    String createdAt,
    ClusterMetadata metadata
) {}