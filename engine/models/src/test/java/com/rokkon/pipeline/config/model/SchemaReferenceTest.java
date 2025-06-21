package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

/**
 * Unit tests for SchemaReference using Quarkus-injected ObjectMapper.
 * All test logic is in SchemaReferenceTestBase.
 */
@QuarkusTest
public class SchemaReferenceTest extends SchemaReferenceTestBase {

    @Inject
    ObjectMapper objectMapper;
    
    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}