package com.rokkon.search.config.pipeline.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class StepTypeTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testSerializePipeline() throws Exception {
        String json = objectMapper.writeValueAsString(StepType.PIPELINE);
        assertEquals("\"PIPELINE\"", json);
    }

    @Test
    public void testSerializeInitialPipeline() throws Exception {
        String json = objectMapper.writeValueAsString(StepType.INITIAL_PIPELINE);
        assertEquals("\"INITIAL_PIPELINE\"", json);
    }

    @Test
    public void testSerializeSink() throws Exception {
        String json = objectMapper.writeValueAsString(StepType.SINK);
        assertEquals("\"SINK\"", json);
    }

    @Test
    public void testDeserializePipeline() throws Exception {
        StepType type = objectMapper.readValue("\"PIPELINE\"", StepType.class);
        assertEquals(StepType.PIPELINE, type);
    }

    @Test
    public void testDeserializeInitialPipeline() throws Exception {
        StepType type = objectMapper.readValue("\"INITIAL_PIPELINE\"", StepType.class);
        assertEquals(StepType.INITIAL_PIPELINE, type);
    }

    @Test
    public void testDeserializeSink() throws Exception {
        StepType type = objectMapper.readValue("\"SINK\"", StepType.class);
        assertEquals(StepType.SINK, type);
    }

    @Test
    public void testAllValues() {
        StepType[] values = StepType.values();
        assertEquals(3, values.length);
        assertArrayEquals(new StepType[]{StepType.PIPELINE, StepType.INITIAL_PIPELINE, StepType.SINK}, values);
    }
}