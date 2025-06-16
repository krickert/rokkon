package com.rokkon.search.config.pipeline.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class TransportTypeTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testSerializeKafka() throws Exception {
        String json = objectMapper.writeValueAsString(TransportType.KAFKA);
        assertEquals("\"KAFKA\"", json);
    }

    @Test
    public void testSerializeGrpc() throws Exception {
        String json = objectMapper.writeValueAsString(TransportType.GRPC);
        assertEquals("\"GRPC\"", json);
    }

    @Test
    public void testSerializeInternal() throws Exception {
        String json = objectMapper.writeValueAsString(TransportType.INTERNAL);
        assertEquals("\"INTERNAL\"", json);
    }

    @Test
    public void testDeserializeKafka() throws Exception {
        TransportType type = objectMapper.readValue("\"KAFKA\"", TransportType.class);
        assertEquals(TransportType.KAFKA, type);
    }

    @Test
    public void testDeserializeGrpc() throws Exception {
        TransportType type = objectMapper.readValue("\"GRPC\"", TransportType.class);
        assertEquals(TransportType.GRPC, type);
    }

    @Test
    public void testDeserializeInternal() throws Exception {
        TransportType type = objectMapper.readValue("\"INTERNAL\"", TransportType.class);
        assertEquals(TransportType.INTERNAL, type);
    }

    @Test
    public void testAllValues() {
        TransportType[] values = TransportType.values();
        assertEquals(3, values.length);
        assertArrayEquals(new TransportType[]{TransportType.KAFKA, TransportType.GRPC, TransportType.INTERNAL}, values);
    }
}