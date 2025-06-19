package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

/**
 * Unit tests for TransportType enum using Quarkus-injected ObjectMapper.
 */
@QuarkusTest
public class TransportTypeTest extends TransportTypeTestBase {

    @Inject
    ObjectMapper objectMapper;
    
    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}