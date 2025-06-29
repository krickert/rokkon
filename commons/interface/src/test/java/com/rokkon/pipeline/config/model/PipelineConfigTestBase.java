package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Base test class for PipelineConfig serialization/deserialization.
 * Tests pipeline configuration with its steps.
 */
public abstract class PipelineConfigTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testValidPipelineConfig() {
        // Create pipeline steps
        Map<String, PipelineStepConfig> steps = new HashMap<>();
        
        PipelineStepConfig.ProcessorInfo processorInfo1 = new PipelineStepConfig.ProcessorInfo("parser-service", null);
        PipelineStepConfig.ProcessorInfo processorInfo2 = new PipelineStepConfig.ProcessorInfo(null, "chunkerBean");
        
        PipelineStepConfig step1 = new PipelineStepConfig(
                "parser-step",
                StepType.INITIAL_PIPELINE,
                processorInfo1
        );
        
        PipelineStepConfig step2 = new PipelineStepConfig(
                "chunker-step",
                StepType.PIPELINE,
                processorInfo2
        );
        
        steps.put("step1", step1);
        steps.put("step2", step2);
        
        PipelineConfig config = new PipelineConfig("document-processing", steps);
        
        assertThat(config.name()).isEqualTo("document-processing");
        assertThat(config.pipelineSteps()).hasSize(2);
        assertThat(config.pipelineSteps().get("step1").stepName()).isEqualTo("parser-step");
        assertThat(config.pipelineSteps().get("step2").stepName()).isEqualTo("chunker-step");
    }

    @Test
    public void testSerializationDeserialization() throws Exception {
        // Create a pipeline config
        Map<String, PipelineStepConfig> steps = new HashMap<>();
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo("test-service", null);
        PipelineStepConfig.JsonConfigOptions configOptions = new PipelineStepConfig.JsonConfigOptions(Map.of("config", "value"));
        
        PipelineStepConfig step = new PipelineStepConfig(
                "test-step",
                StepType.PIPELINE,
                processorInfo,
                configOptions
        );
        
        steps.put("step1", step);
        
        PipelineConfig original = new PipelineConfig("test-pipeline", steps);
        
        // Serialize
        String json = getObjectMapper().writeValueAsString(original);
        
        // Verify JSON structure
        assertThat(json).contains("\"name\":\"test-pipeline\"");
        assertThat(json).contains("\"pipelineSteps\"");
        assertThat(json).contains("\"step1\"");
        assertThat(json).contains("\"stepName\":\"test-step\"");
        
        // Deserialize
        PipelineConfig deserialized = getObjectMapper().readValue(json, PipelineConfig.class);
        
        assertThat(deserialized.name()).isEqualTo(original.name());
        assertThat(deserialized.pipelineSteps()).hasSize(1);
        assertThat(deserialized.pipelineSteps().get("step1").stepName()).isEqualTo("test-step");
    }

    @Test
    public void testEmptyPipelineSteps() {
        PipelineConfig config = new PipelineConfig("empty-pipeline", null);
        assertThat(config.pipelineSteps()).isEmpty();
        
        PipelineConfig config2 = new PipelineConfig("empty-pipeline2", Map.of());
        assertThat(config2.pipelineSteps()).isEmpty();
    }

    @Test
    public void testInvalidName() {
        assertThatThrownBy(() -> new PipelineConfig(null, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PipelineConfig name cannot be null or blank.");
            
        assertThatThrownBy(() -> new PipelineConfig("", Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PipelineConfig name cannot be null or blank.");
            
        assertThatThrownBy(() -> new PipelineConfig("   ", Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PipelineConfig name cannot be null or blank.");
    }

    @Test
    public void testImmutability() {
        Map<String, PipelineStepConfig> mutableSteps = new HashMap<>();
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo("module1-service", null);
        PipelineStepConfig step = new PipelineStepConfig(
                "step1",
                StepType.PIPELINE,
                processorInfo
        );
        mutableSteps.put("step1", step);
        
        PipelineConfig config = new PipelineConfig("immutable-test", mutableSteps);
        
        // Try to modify the original map
        mutableSteps.put("step2", step);
        
        // Config should not be affected
        assertThat(config.pipelineSteps()).hasSize(1);
        
        // Try to modify the returned map
        assertThatThrownBy(() -> config.pipelineSteps().put("step3", step))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testComplexSerialization() throws Exception {
        // Create a more complex pipeline with multiple steps
        Map<String, PipelineStepConfig> steps = new HashMap<>();
        
        // Parser step with Kafka output
        PipelineStepConfig.OutputTarget parserOutput = new PipelineStepConfig.OutputTarget(
                "chunker",
                TransportType.KAFKA,
                null,
                new KafkaTransportConfig("parsed-docs", null, null, null, null, null)
        );
        
        steps.put("parser", new PipelineStepConfig(
                "document-parser",
                StepType.INITIAL_PIPELINE,
                "Parser Step",
                null,
                new PipelineStepConfig.JsonConfigOptions(Map.of("format", "pdf", "maxSize", "10MB")),
                null,
                Map.of("default", parserOutput),
                3,
                1000L,
                30000L,
                2.0,
                60000L,
                new PipelineStepConfig.ProcessorInfo("parser-service", null)
        ));
        
        // Chunker step with gRPC output
        PipelineStepConfig.OutputTarget chunkerOutput = new PipelineStepConfig.OutputTarget(
                "vectorizer",
                TransportType.GRPC,
                new GrpcTransportConfig("vectorizer-service", Map.of("host", "localhost", "port", "9090")),
                null
        );
        
        steps.put("chunker", new PipelineStepConfig(
                "text-chunker",
                StepType.PIPELINE,
                "Chunker Step",
                null,
                new PipelineStepConfig.JsonConfigOptions(Map.of("chunkSize", "1000", "overlap", "100")),
                List.of(new KafkaInputDefinition(List.of("parsed-docs"), "chunker-group", Map.of())),
                Map.of("default", chunkerOutput),
                3,
                1000L,
                30000L,
                2.0,
                60000L,
                new PipelineStepConfig.ProcessorInfo("chunker-service", null)
        ));
        
        // Vectorizer step with internal processor
        PipelineStepConfig.OutputTarget vectorizerOutput = new PipelineStepConfig.OutputTarget(
                "sink",
                TransportType.KAFKA,
                null,
                new KafkaTransportConfig("indexed-docs", null, null, null, null, null)
        );
        
        steps.put("vectorizer", new PipelineStepConfig(
                "embedding-generator",
                StepType.PIPELINE,
                "Vectorizer Step",
                null,
                new PipelineStepConfig.JsonConfigOptions(Map.of("model", "bert-base", "dimensions", "768")),
                null,
                Map.of("default", vectorizerOutput),
                3,
                1000L,
                30000L,
                2.0,
                120000L,
                new PipelineStepConfig.ProcessorInfo(null, "vectorizerBean")
        ));
        
        // Sink step
        steps.put("sink", new PipelineStepConfig(
                "elasticsearch-sink",
                StepType.SINK,
                "Elasticsearch Sink",
                null,
                new PipelineStepConfig.JsonConfigOptions(Map.of("index", "documents", "type", "_doc")),
                List.of(new KafkaInputDefinition(List.of("indexed-docs"), "sink-group", Map.of())),
                null,
                3,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo("sink-service", null)
        ));
        
        PipelineConfig pipeline = new PipelineConfig("document-indexing-pipeline", steps);
        
        // Serialize
        String json = getObjectMapper().writeValueAsString(pipeline);
        
        // Deserialize
        PipelineConfig deserialized = getObjectMapper().readValue(json, PipelineConfig.class);
        
        // Verify all steps are preserved
        assertThat(deserialized.name()).isEqualTo("document-indexing-pipeline");
        assertThat(deserialized.pipelineSteps()).hasSize(4);
        assertThat(deserialized.pipelineSteps().keySet())
            .containsExactlyInAnyOrder("parser", "chunker", "vectorizer", "sink");
        
        // Verify specific step details
        PipelineStepConfig parserStep = deserialized.pipelineSteps().get("parser");
        assertThat(parserStep.stepName()).isEqualTo("document-parser");
        assertThat(parserStep.stepType()).isEqualTo(StepType.INITIAL_PIPELINE);
        assertThat(parserStep.customConfig().configParams().get("format")).isEqualTo("pdf");
    }

    @Test
    public void testJsonPropertyAnnotations() throws Exception {
        // Create minimal pipeline
        PipelineConfig config = new PipelineConfig("test", Map.of());
        
        String json = getObjectMapper().writeValueAsString(config);
        
        // Verify that JsonProperty annotations are respected
        assertThat(json).contains("\"name\"");
        assertThat(json).contains("\"pipelineSteps\"");
    }

    @Test
    public void testDeserializationFromJson() throws Exception {
        String json = """
            {
                "name": "json-pipeline",
                "pipelineSteps": {
                    "step1": {
                        "stepName": "json-step",
                        "processorInfo": {
                            "grpcServiceName": "json-service"
                        },
                        "stepType": "PIPELINE",
                        "customConfig": {
                            "configParams": {
                                "key": "value"
                            }
                        }
                    }
                }
            }
            """;
        
        PipelineConfig config = getObjectMapper().readValue(json, PipelineConfig.class);
        
        assertThat(config.name()).isEqualTo("json-pipeline");
        assertThat(config.pipelineSteps()).hasSize(1);
        assertThat(config.pipelineSteps().get("step1").stepName()).isEqualTo("json-step");
        assertThat(config.pipelineSteps().get("step1").stepType()).isEqualTo(StepType.PIPELINE);
    }

    @Test
    public void testNullValueInStepsMap() {
        Map<String, PipelineStepConfig> stepsWithNull = new HashMap<>();
        stepsWithNull.put("step1", null);
        
        // Map.copyOf() should throw NPE for null values
        assertThatThrownBy(() -> new PipelineConfig("test", stepsWithNull))
            .isInstanceOf(NullPointerException.class);
    }
}