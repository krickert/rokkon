package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;

import jakarta.inject.Inject;

/**
 * Base class for JSON serialization/deserialization tests.
 * Provides common functionality for testing model classes.
 */
@QuarkusTest
public abstract class AbstractJsonSerdeTest<T> {
    
    @Inject
    protected ObjectMapper objectMapper;
    
    protected abstract Class<T> getModelClass();
    
    /**
     * Performs a round-trip serialization test.
     * Serializes the object to JSON and deserializes it back.
     */
    protected T roundTrip(T original) throws Exception {
        String json = objectMapper.writeValueAsString(original);
        return objectMapper.readValue(json, getModelClass());
    }
    
    /**
     * Serializes object to JSON string.
     */
    protected String toJson(T object) throws Exception {
        return objectMapper.writeValueAsString(object);
    }
    
    /**
     * Deserializes JSON string to object.
     */
    protected T fromJson(String json) throws Exception {
        return objectMapper.readValue(json, getModelClass());
    }
    
    /**
     * Pretty prints JSON for debugging.
     */
    protected String toPrettyJson(T object) throws Exception {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
    }
}