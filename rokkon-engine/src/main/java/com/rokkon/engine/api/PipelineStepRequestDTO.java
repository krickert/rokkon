package com.rokkon.engine.api;

import java.util.Map;

public record PipelineStepRequestDTO(
    String name,    // Step name
    String module,  // Module identifier (e.g., "test-module")
    Map<String, Object> config // Step-specific configuration
) {}
