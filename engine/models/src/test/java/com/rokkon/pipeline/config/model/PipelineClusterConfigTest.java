package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Unit test for PipelineClusterConfig using Quarkus CDI.
 */
@QuarkusTest
public class PipelineClusterConfigTest extends PipelineClusterConfigTestBase {

    @Inject
    ObjectMapper objectMapper;

    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}