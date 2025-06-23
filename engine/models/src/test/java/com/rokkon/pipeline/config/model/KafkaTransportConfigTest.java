package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Unit test for KafkaTransportConfig using Quarkus CDI.
 */
@QuarkusTest
public class KafkaTransportConfigTest extends KafkaTransportConfigTestBase {

    @Inject
    ObjectMapper objectMapper;

    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}