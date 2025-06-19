package com.rokkon.pipeline.config;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.jackson.ObjectMapperCustomizer;

import jakarta.inject.Singleton;

/**
 * Ensures consistent JSON ordering for schema validation and comparison.
 * Critical for detecting when data scientists create "logically equivalent" but differently ordered schemas.
 */
@Singleton
public class JsonOrderingCustomizer implements ObjectMapperCustomizer {
    
    @Override
    public void customize(ObjectMapper objectMapper) {
        // Sort all properties alphabetically for consistent output
        objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        
        // Sort map entries by keys (important for Map<String, PipelineConfig> etc)
        objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        
        // Keep existing behaviors
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false);
    }
}