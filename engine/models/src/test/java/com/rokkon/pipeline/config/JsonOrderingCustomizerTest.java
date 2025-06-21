package com.rokkon.pipeline.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

/**
 * Unit tests for JsonOrderingCustomizer using Quarkus-injected ObjectMapper.
 * Verifies that our customizer is properly applied in the Quarkus environment.
 */
@QuarkusTest
public class JsonOrderingCustomizerTest extends JsonOrderingCustomizerTestBase {

    @Inject
    ObjectMapper objectMapper;
    
    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}