package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for PipelineGraphConfig that contains all test logic.
 * Extended by both unit tests and integration tests.
 */
public abstract class PipelineGraphConfigTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testEmptyGraphSerialization() throws Exception {
        PipelineGraphConfig emptyGraph = new PipelineGraphConfig(null);
        
        String json = getObjectMapper().writeValueAsString(emptyGraph);
        assertThat(json).contains("\"pipelines\":{}");
        
        // Test round trip
        PipelineGraphConfig deserialized = getObjectMapper().readValue(json, PipelineGraphConfig.class);
        assertThat(deserialized.pipelines()).isEmpty();
    }

    @Test
    public void testSinglePipelineSerialization() throws Exception {
        // Create a simple pipeline
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo("test-service", null);
        PipelineStepConfig step = new PipelineStepConfig("test-step", StepType.SINK, processorInfo);
        
        PipelineConfig pipeline = new PipelineConfig("test-pipeline", Map.of("test-step", step));
        PipelineGraphConfig graph = new PipelineGraphConfig(Map.of("test-pipeline", pipeline));
        
        String json = getObjectMapper().writeValueAsString(graph);
        
        // Verify structure
        assertThat(json).contains("\"pipelines\"");
        assertThat(json).contains("\"test-pipeline\"");
        assertThat(json).contains("\"name\":\"test-pipeline\"");
        assertThat(json).contains("\"pipelineSteps\"");
        
        // Test round trip
        PipelineGraphConfig deserialized = getObjectMapper().readValue(json, PipelineGraphConfig.class);
        assertThat(deserialized.pipelines()).hasSize(1);
        assertThat(deserialized.getPipelineConfig("test-pipeline")).isNotNull();
        assertThat(deserialized.getPipelineConfig("test-pipeline").name()).isEqualTo("test-pipeline");
    }

    @Test
    public void testMultiplePipelinesSerialization() throws Exception {
        // Create multiple pipelines
        PipelineStepConfig.ProcessorInfo processor1 = new PipelineStepConfig.ProcessorInfo("service1", null);
        PipelineStepConfig step1 = new PipelineStepConfig("step1", StepType.PIPELINE, processor1);
        PipelineConfig pipeline1 = new PipelineConfig("pipeline1", Map.of("step1", step1));
        
        PipelineStepConfig.ProcessorInfo processor2 = new PipelineStepConfig.ProcessorInfo("service2", null);
        PipelineStepConfig step2 = new PipelineStepConfig("step2", StepType.SINK, processor2);
        PipelineConfig pipeline2 = new PipelineConfig("pipeline2", Map.of("step2", step2));
        
        PipelineGraphConfig graph = new PipelineGraphConfig(Map.of(
            "pipeline1", pipeline1,
            "pipeline2", pipeline2
        ));
        
        String json = getObjectMapper().writeValueAsString(graph);
        PipelineGraphConfig deserialized = getObjectMapper().readValue(json, PipelineGraphConfig.class);
        
        assertThat(deserialized.pipelines()).hasSize(2);
        assertThat(deserialized.getPipelineConfig("pipeline1")).isNotNull();
        assertThat(deserialized.getPipelineConfig("pipeline2")).isNotNull();
        assertThat(deserialized.getPipelineConfig("nonexistent")).isNull();
    }

    @Test
    public void testDeserializationFromJson() throws Exception {
        String json = """
            {
                "pipelines": {
                    "document-processing": {
                        "name": "document-processing",
                        "pipelineSteps": {
                            "chunker": {
                                "stepName": "chunker",
                                "stepType": "PIPELINE",
                                "processorInfo": {
                                    "grpcServiceName": "chunker-service"
                                }
                            },
                            "embedder": {
                                "stepName": "embedder",
                                "stepType": "SINK",
                                "processorInfo": {
                                    "grpcServiceName": "embedder-service"
                                }
                            }
                        }
                    },
                    "real-time-analysis": {
                        "name": "real-time-analysis",
                        "pipelineSteps": {
                            "analyzer": {
                                "stepName": "analyzer",
                                "stepType": "SINK",
                                "processorInfo": {
                                    "internalProcessorBeanName": "analyzerBean"
                                }
                            }
                        }
                    }
                }
            }
            """;
        
        PipelineGraphConfig graph = getObjectMapper().readValue(json, PipelineGraphConfig.class);
        
        assertThat(graph.pipelines()).hasSize(2);
        
        // Check first pipeline
        PipelineConfig docProcessing = graph.getPipelineConfig("document-processing");
        assertThat(docProcessing).isNotNull();
        assertThat(docProcessing.name()).isEqualTo("document-processing");
        assertThat(docProcessing.pipelineSteps()).hasSize(2);
        assertThat(docProcessing.pipelineSteps().get("chunker").stepType()).isEqualTo(StepType.PIPELINE);
        assertThat(docProcessing.pipelineSteps().get("embedder").stepType()).isEqualTo(StepType.SINK);
        
        // Check second pipeline
        PipelineConfig realTimeAnalysis = graph.getPipelineConfig("real-time-analysis");
        assertThat(realTimeAnalysis).isNotNull();
        assertThat(realTimeAnalysis.pipelineSteps()).hasSize(1);
        assertThat(realTimeAnalysis.pipelineSteps().get("analyzer").processorInfo().internalProcessorBeanName())
            .isEqualTo("analyzerBean");
    }

    @Test
    public void testImmutability() {
        Map<String, PipelineConfig> mutableMap = new java.util.HashMap<>();
        PipelineStepConfig.ProcessorInfo processor = new PipelineStepConfig.ProcessorInfo("service", null);
        PipelineStepConfig step = new PipelineStepConfig("step", StepType.SINK, processor);
        PipelineConfig pipeline = new PipelineConfig("pipeline", Map.of("step", step));
        mutableMap.put("pipeline", pipeline);
        
        PipelineGraphConfig graph = new PipelineGraphConfig(mutableMap);
        
        // Original map modification should not affect the graph
        mutableMap.put("another", pipeline);
        assertThat(graph.pipelines()).hasSize(1);
        
        // Returned map should be immutable
        assertThrows(UnsupportedOperationException.class, () -> 
            graph.pipelines().put("new", pipeline)
        );
    }

    @Test
    public void testNullHandling() throws Exception {
        // Test with null map
        PipelineGraphConfig graphWithNull = new PipelineGraphConfig(null);
        assertThat(graphWithNull.pipelines()).isEmpty();
        
        String json = getObjectMapper().writeValueAsString(graphWithNull);
        PipelineGraphConfig deserialized = getObjectMapper().readValue(json, PipelineGraphConfig.class);
        assertThat(deserialized.pipelines()).isEmpty();
    }

    @Test
    public void testComplexGraphRoundTrip() throws Exception {
        // Create a complex graph with multiple pipelines and steps
        PipelineGraphConfig originalGraph = createComplexGraph();
        
        // Serialize to JSON
        String json = getObjectMapper().writeValueAsString(originalGraph);
        
        // Deserialize back
        PipelineGraphConfig deserializedGraph = getObjectMapper().readValue(json, PipelineGraphConfig.class);
        
        // Verify the graphs are equivalent
        assertThat(deserializedGraph.pipelines()).hasSameSizeAs(originalGraph.pipelines());
        
        for (String pipelineName : originalGraph.pipelines().keySet()) {
            PipelineConfig original = originalGraph.getPipelineConfig(pipelineName);
            PipelineConfig deserialized = deserializedGraph.getPipelineConfig(pipelineName);
            
            assertThat(deserialized).isNotNull();
            assertThat(deserialized.name()).isEqualTo(original.name());
            assertThat(deserialized.pipelineSteps()).hasSameSizeAs(original.pipelineSteps());
        }
    }
    
    private PipelineGraphConfig createComplexGraph() {
        // Pipeline 1: Document processing with chunking and embedding
        PipelineStepConfig.ProcessorInfo chunkerProcessor = new PipelineStepConfig.ProcessorInfo("chunker-service", null);
        PipelineStepConfig.ProcessorInfo embedderProcessor = new PipelineStepConfig.ProcessorInfo("embedder-service", null);
        
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig("embedder-service", Map.of("timeout", "30s"));
        PipelineStepConfig.OutputTarget chunkerOutput = new PipelineStepConfig.OutputTarget(
            "embedder", TransportType.GRPC, grpcTransport, null);
        
        PipelineStepConfig chunkerStep = new PipelineStepConfig(
            "chunker", StepType.INITIAL_PIPELINE, "Chunks documents",
            null, null, Collections.emptyList(), Map.of("output", chunkerOutput),
            3, 1000L, 30000L, 2.0, null, chunkerProcessor);
            
        PipelineStepConfig embedderStep = new PipelineStepConfig(
            "embedder", StepType.SINK, "Creates embeddings",
            null, null, Collections.emptyList(), Collections.emptyMap(),
            3, 1000L, 30000L, 2.0, null, embedderProcessor);
            
        PipelineConfig docProcessing = new PipelineConfig("document-processing", Map.of(
            "chunker", chunkerStep,
            "embedder", embedderStep
        ));
        
        // Pipeline 2: Real-time analysis
        PipelineStepConfig.ProcessorInfo analyzerProcessor = new PipelineStepConfig.ProcessorInfo(null, "analyzerBean");
        PipelineStepConfig analyzerStep = new PipelineStepConfig(
            "analyzer", StepType.SINK, analyzerProcessor);
        PipelineConfig realTimeAnalysis = new PipelineConfig("real-time-analysis", Map.of(
            "analyzer", analyzerStep
        ));
        
        return new PipelineGraphConfig(Map.of(
            "document-processing", docProcessing,
            "real-time-analysis", realTimeAnalysis
        ));
    }
}