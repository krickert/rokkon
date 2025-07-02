package com.rokkon.pipeline.engine.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Central configuration for the engine config prefix.
 * This allows changing from "rokkon" to "pipeline" or any other prefix
 * by setting the CONFIG_PREFIX environment variable or config property.
 */
@ApplicationScoped
public class EngineConfigPrefix {
    
    @ConfigProperty(name = "config.prefix", defaultValue = "pipeline")
    String configPrefix;
    
    /**
     * Get the config prefix to use for all engine configuration properties.
     * @return The config prefix (e.g., "pipeline", "rokkon", etc.)
     */
    public String getPrefix() {
        return configPrefix;
    }
    
    /**
     * Get a fully qualified config property name with the prefix.
     * @param property The property name without prefix (e.g., "cluster.name")
     * @return The full property name (e.g., "pipeline.cluster.name")
     */
    public String getPropertyName(String property) {
        return configPrefix + "." + property;
    }
}