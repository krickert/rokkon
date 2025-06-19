package com.rokkon.search.config.pipeline.service;

import com.rokkon.search.config.pipeline.model.*;
import com.rokkon.search.config.pipeline.service.validation.ValidationResult;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConfigValidationService to verify validation logic works correctly.
 */
@QuarkusTest
public class ConfigValidationServiceTest {
    
    @Inject
    ConfigValidationService validationService;
    
    @Test
    void testValidPipelineValidation() throws Exception {
        // Given: A valid pipeline configuration
        PipelineConfig config = createValidPipelineConfig();
        
        // When: Validating the pipeline
        ValidationResult result = validationService.validatePipelineStructure("test-pipeline", config)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        // Then: Validation should pass
        assertTrue(result.valid(), "Valid pipeline should pass validation. Errors: " + result.errors());
        assertTrue(result.errors().isEmpty(), "Should have no errors");
        
        System.out.println("✅ Valid pipeline validation successful");
    }
    
    @Test
    void testInvalidPipelineValidation() throws Exception {
        // Given: An invalid pipeline configuration
        PipelineConfig config = createInvalidPipelineConfig();
        
        // When: Validating the pipeline
        ValidationResult result = validationService.validatePipelineStructure("test-pipeline", config)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        // Then: Validation should fail
        assertFalse(result.valid(), "Invalid pipeline should fail validation");
        assertFalse(result.errors().isEmpty(), "Should have validation errors");
        
        System.out.println("✅ Invalid pipeline validation failed correctly. Errors: " + result.errors());
    }
    
    @Test
    void testServiceNameValidation() {
        // Test valid service names
        ValidationResult valid1 = validationService.validateServiceName("my-service");
        assertTrue(valid1.valid(), "Valid service name should pass");
        
        ValidationResult valid2 = validationService.validateServiceName("service123");
        assertTrue(valid2.valid(), "Valid service name with numbers should pass");
        
        // Test invalid service names
        ValidationResult invalid1 = validationService.validateServiceName("Invalid-Service-Name!");
        assertFalse(invalid1.valid(), "Service name with invalid characters should fail");
        
        ValidationResult invalid2 = validationService.validateServiceName("");
        assertFalse(invalid2.valid(), "Empty service name should fail");
        
        ValidationResult invalid3 = validationService.validateServiceName(null);
        assertFalse(invalid3.valid(), "Null service name should fail");
        
        System.out.println("✅ Service name validation working correctly");
    }
    
    @Test
    void testKafkaTopicValidation() {
        // Test valid topic names
        ValidationResult valid1 = validationService.validateKafkaTopic("my-topic");
        assertTrue(valid1.valid(), "Valid topic name should pass");
        
        ValidationResult valid2 = validationService.validateKafkaTopic("topic_with_underscores");
        assertTrue(valid2.valid(), "Topic name with underscores should pass");
        
        ValidationResult valid3 = validationService.validateKafkaTopic("topic.with.dots");
        assertTrue(valid3.valid(), "Topic name with dots should pass");
        
        // Test invalid topic names
        ValidationResult invalid1 = validationService.validateKafkaTopic("..");
        assertFalse(invalid1.valid(), "Topic name '..' should fail");
        
        ValidationResult invalid2 = validationService.validateKafkaTopic("");
        assertFalse(invalid2.valid(), "Empty topic name should fail");
        
        ValidationResult invalid3 = validationService.validateKafkaTopic(null);
        assertFalse(invalid3.valid(), "Null topic name should fail");
        
        System.out.println("✅ Kafka topic validation working correctly");
    }
    
    // Helper methods
    
    private PipelineConfig createValidPipelineConfig() {
        // Create chunker step (initial pipeline step)
        PipelineStepConfig.ProcessorInfo chunkerProcessor = new PipelineStepConfig.ProcessorInfo(
                "chunker-service", null);
        
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
                "embedder-service", Map.of("timeout", "30s"));
        
        PipelineStepConfig.OutputTarget chunkerOutput = new PipelineStepConfig.OutputTarget(
                "embedder-step", TransportType.GRPC, grpcTransport, null);
        
        PipelineStepConfig chunkerStep = new PipelineStepConfig(
                "chunker-step", StepType.INITIAL_PIPELINE, "Document chunking step",
                "chunker-schema", null, List.of(),
                Map.of("primary", chunkerOutput), 3, 1000L, 30000L, 2.0, 25000L,
                chunkerProcessor);
        
        // Create embedder step (sink step)
        PipelineStepConfig.ProcessorInfo embedderProcessor = new PipelineStepConfig.ProcessorInfo(
                "embedder-service", null);
        
        PipelineStepConfig embedderStep = new PipelineStepConfig(
                "embedder-step", StepType.SINK, "Embedding generation step",
                "embedder-schema", null, List.of(), Map.of(), 2, 2000L, 45000L, 1.5, 60000L,
                embedderProcessor);
        
        return new PipelineConfig("test-document-processing", Map.of(
                "chunker-step", chunkerStep,
                "embedder-step", embedderStep
        ));
    }
    
    private PipelineConfig createInvalidPipelineConfig() {
        // Create a step that fails business validation
        PipelineStepConfig invalidStep = new PipelineStepConfig(
                "", // Empty step name (will fail business validation)
                StepType.PIPELINE,
                "Invalid step",
                null,
                null,
                List.of(),
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null // Missing processor info (will fail business validation)
        );
        
        return new PipelineConfig("invalid-pipeline", Map.of(
                "invalid-step", invalidStep
        ));
    }
}