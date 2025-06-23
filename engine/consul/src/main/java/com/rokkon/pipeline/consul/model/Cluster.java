package com.rokkon.pipeline.consul.model;

/**
 * Represents a Rokkon cluster.
 */
public record Cluster(
    String name,
    String createdAt,
    ClusterMetadata metadata
) {}