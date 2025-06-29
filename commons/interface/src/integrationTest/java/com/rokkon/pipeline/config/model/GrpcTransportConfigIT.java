package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Integration tests for GrpcTransportConfig.
 * 
 * This test uses a default ObjectMapper for integration testing.
 */
@QuarkusIntegrationTest
public class GrpcTransportConfigIT extends GrpcTransportConfigTestBase {

    private final ObjectMapper objectMapper;
    
    public GrpcTransportConfigIT() {
        // For integration tests, use a default ObjectMapper
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}