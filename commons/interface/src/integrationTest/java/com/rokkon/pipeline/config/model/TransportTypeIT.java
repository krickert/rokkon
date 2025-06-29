package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Integration tests for TransportType enum.
 */
@QuarkusIntegrationTest
public class TransportTypeIT extends TransportTypeTestBase {

    private final ObjectMapper objectMapper;
    
    public TransportTypeIT() {
        this.objectMapper = new ObjectMapper();
        // Apply same configuration as JsonOrderingCustomizer
        this.objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        this.objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }
    
    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}