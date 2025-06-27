package com.rokkon.pipeline.config.model;

import java.time.Instant;
import java.util.Map;

/**
 * Metadata for a cluster.
 */
public record ClusterMetadata(
        String name,
        Instant createdAt,
        String defaultPipeline,
        Map<String, Object> metadata
) { }
