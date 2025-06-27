package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Unit test for PipelineModuleConfiguration using Quarkus-configured ObjectMapper.
 */
@QuarkusTest
public class PipelineModuleConfigurationTest extends PipelineModuleConfigurationTestBase {

    @Inject
    ObjectMapper objectMapper;

    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}