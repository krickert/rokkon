package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class RetryConfigValidatorTestBase {

    protected abstract RetryConfigValidator getValidator();

    @Test
    void testNullPipelineConfiguration() {
        ValidationResult result = getValidator().validate(null);
        
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("Pipeline configuration or steps cannot be null");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void testValidRetryConfiguration() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", 
            StepType.PIPELINE, 
            "Test step",
            null, null, null,
            Map.of("default", output),
            3,        // maxRetries
            1000L,    // retryBackoffMs
            60000L,   // stepTimeoutMs
            null, null,
            processorInfo
        );

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step)
        );

        ValidationResult result = getValidator().validate(config);
        
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void testExcessiveMaxRetries() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", 
            StepType.PIPELINE, 
            "Test step",
            null, null, null,
            Map.of("default", output),
            150,      // exceeds max
            1000L,
            60000L,
            null, null,
            processorInfo
        );

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step)
        );

        ValidationResult result = getValidator().validate(config);
        
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly(
            "Step 'test-step' retry config: maxRetries exceeds maximum allowed value of 100 (was 150)"
        );
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void testHighMaxRetriesWarning() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", 
            StepType.PIPELINE, 
            "Test step",
            null, null, null,
            Map.of("default", output),
            20,       // triggers warning
            1000L,
            60000L,
            null, null,
            processorInfo
        );

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step)
        );

        ValidationResult result = getValidator().validate(config);
        
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).containsExactly(
            "Step 'test-step' retry config: high number of retry attempts (20) may cause processing delays"
        );
    }

    @Test
    void testExcessiveRetryBackoff() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", 
            StepType.PIPELINE, 
            "Test step",
            null, null, null,
            Map.of("default", output),
            3,
            4000000L,   // exceeds max (over 1 hour)
            60000L,
            null, null,
            processorInfo
        );

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step)
        );

        ValidationResult result = getValidator().validate(config);
        
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactlyInAnyOrder(
            "Step 'test-step' retry config: retryBackoffMs exceeds maximum allowed value of 3600000 ms (was 4000000)",
            "Step 'test-step' retry config: initial retry backoff (4000000 ms) cannot exceed max retry backoff (60000 ms)"
        );
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void testHighBackoffWarning() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", 
            StepType.PIPELINE, 
            "Test step",
            null, null, null,
            Map.of("default", output),
            3,
            400000L,  // high backoff (400s)
            3600000L,
            null, null,
            processorInfo
        );

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step)
        );

        ValidationResult result = getValidator().validate(config);
        
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).containsExactly(
            "Step 'test-step' retry config: high retry backoff (400000 ms) may cause significant processing delays"
        );
    }

    @Test
    void testExcessiveTimeout() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", 
            StepType.PIPELINE, 
            "Test step",
            null, null, null,
            Map.of("default", output),
            3,
            1000L,
            30000L,
            null, 
            4000000L,    // stepTimeoutMs - exceeds max timeout (over 1 hour)
            processorInfo
        );

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step)
        );

        ValidationResult result = getValidator().validate(config);
        
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly(
            "Step 'test-step' retry config: stepTimeoutMs exceeds maximum allowed value of 3600000 ms (was 4000000)"
        );
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void testHighTimeoutWarning() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", 
            StepType.PIPELINE, 
            "Test step",
            null, null, null,
            Map.of("default", output),
            3,           // maxRetries
            1000L,       // retryBackoffMs
            30000L,      // maxRetryBackoffMs
            null,        // retryBackoffMultiplier
            700000L,     // stepTimeoutMs: high timeout (11+ minutes)
            processorInfo
        );

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step)
        );

        ValidationResult result = getValidator().validate(config);
        
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).containsExactly(
            "Step 'test-step' retry config: high step timeout (700000 ms) may cause processing delays"
        );
    }

    @Test
    void testZeroRetriesWithBackoffWarning() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", 
            StepType.PIPELINE, 
            "Test step",
            null, null, null,
            Map.of("default", output),
            0,        // maxRetries = 0
            1000L,    // backoff defined but unused
            60000L,
            null, null,
            processorInfo
        );

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step)
        );

        ValidationResult result = getValidator().validate(config);
        
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).containsExactly(
            "Step 'test-step' retry config: retryBackoffMs defined but maxRetries is 0 (retries disabled)"
        );
    }

    @Test
    void testRetryTimeExceedsTimeout() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", 
            StepType.PIPELINE, 
            "Test step",
            null, null, null,
            Map.of("default", output),
            10,       // maxRetries: 10 retries
            7000L,    // retryBackoffMs: 7s backoff * 10 = 70s total
            30000L,   // maxRetryBackoffMs
            null,     // retryBackoffMultiplier
            60000L,   // stepTimeoutMs: 60s timeout
            processorInfo
        );

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step)
        );

        ValidationResult result = getValidator().validate(config);
        
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).containsExactly(
            "Step 'test-step' retry config: total retry time (70000 ms) may exceed step timeout (60000 ms)"
        );
    }

    @Test
    void testMaxRetryBackoffValidation() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", 
            StepType.PIPELINE, 
            "Test step",
            null, null, null,
            Map.of("default", output),
            3,        // maxRetries
            10000L,   // retryBackoffMs (10s)
            5000L,    // maxRetryBackoffMs (5s - less than initial)
            2.0,      // retryBackoffMultiplier
            60000L,   // stepTimeoutMs 
            processorInfo
        );

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step)
        );

        ValidationResult result = getValidator().validate(config);
        
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly(
            "Step 'test-step' retry config: initial retry backoff (10000 ms) cannot exceed max retry backoff (5000 ms)"
        );
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void testValidatorPriorityAndName() {
        assertThat(getValidator().getPriority()).isEqualTo(70);
        assertThat(getValidator().getValidatorName()).isEqualTo("RetryConfigValidator");
    }
}