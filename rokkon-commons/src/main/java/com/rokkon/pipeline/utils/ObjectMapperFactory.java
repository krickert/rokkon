package com.rokkon.pipeline.utils;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Factory for creating ObjectMapper instances with consistent configuration.
 * This is primarily for use in tests and standalone applications that don't have
 * access to Quarkus CDI injection.
 * 
 * In Quarkus applications, use @Inject ObjectMapper instead to get the
 * properly configured instance.
 */
public class ObjectMapperFactory {
    
    /**
     * Creates an ObjectMapper with the same configuration as JsonOrderingCustomizer.
     * This ensures consistent JSON serialization across the entire application.
     * 
     * Configuration includes:
     * - Properties sorted alphabetically
     * - Map entries sorted by keys
     * - Property names in snake_case (data scientist friendly!)
     * - Dates written as ISO-8601 strings (not timestamps)
     * - Durations written as ISO-8601 strings (not timestamps)
     * 
     * @return A configured ObjectMapper instance
     */
    public static ObjectMapper createConfiguredMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Apply same configuration as JsonOrderingCustomizer
        // Sort all properties alphabetically for consistent output
        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        
        // Sort map entries by keys (important for Map<String, PipelineConfig> etc)
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        
        // Use snake_case for property names (data scientists hate camelCase!)
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        
        // Keep existing behaviors - match Quarkus defaults
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false);
        
        return mapper;
    }
    
    /**
     * Creates an ObjectMapper with minimal configuration for testing edge cases.
     * This does NOT include the ordering configuration.
     * 
     * @return A minimally configured ObjectMapper instance
     */
    public static ObjectMapper createMinimalMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Only apply Quarkus defaults, no ordering
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false);
        
        return mapper;
    }
}