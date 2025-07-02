package com.rokkon.pipeline.jackson;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.jackson.ObjectMapperCustomizer;

import jakarta.inject.Singleton;

/**
 * Ensures consistent JSON ordering and data-scientist-friendly property naming.
 * Critical for:
 * - Schema validation when data scientists create "logically equivalent" but differently ordered schemas
 * - Consistent property naming that data scientists prefer (snake_case, not camelCase)
 * 
 * This customizer is automatically discovered and applied by Quarkus to all ObjectMapper instances
 * throughout the application.
 */
@Singleton
public class JsonOrderingCustomizer implements ObjectMapperCustomizer {
    
    @Override
    public void customize(ObjectMapper objectMapper) {
        // Ensure JavaTimeModule is registered for proper Instant serialization
        objectMapper.registerModule(new JavaTimeModule());
        
        // Sort all properties alphabetically for consistent output
        objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        
        // Sort map entries by keys (important for Map<String, PipelineConfig> etc)
        objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        
        // Use snake_case for property names (data scientists hate camelCase!)
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        
        // Keep existing behaviors for dates/times
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false);
    }
}