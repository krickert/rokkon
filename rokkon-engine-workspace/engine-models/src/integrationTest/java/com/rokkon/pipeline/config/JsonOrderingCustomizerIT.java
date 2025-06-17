package com.rokkon.pipeline.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Integration tests for JsonOrderingCustomizer.
 * Verifies that we can achieve the same ordering behavior without Quarkus injection.
 */
@QuarkusIntegrationTest
public class JsonOrderingCustomizerIT extends JsonOrderingCustomizerTestBase {

    private final ObjectMapper objectMapper;
    
    public JsonOrderingCustomizerIT() {
        this.objectMapper = new ObjectMapper();
        // Apply same configuration as JsonOrderingCustomizer
        this.objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        this.objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        this.objectMapper.configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false);
    }
    
    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}