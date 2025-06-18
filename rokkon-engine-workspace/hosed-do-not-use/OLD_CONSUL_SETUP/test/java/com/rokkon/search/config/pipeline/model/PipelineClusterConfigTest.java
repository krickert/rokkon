package com.rokkon.search.config.pipeline.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class PipelineClusterConfigTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testCompleteClusterConfigSerialization() throws Exception {
        // Build a complete cluster configuration
        
        // 1. Create modules
        SchemaReference chunkerSchema = new SchemaReference("chunker-schema", 1);
        SchemaReference embedderSchema = new SchemaReference("embedder-schema", 2);
        
        PipelineModuleConfiguration chunkerModule = new PipelineModuleConfiguration(
            "Text Chunker Service", "chunker-service", chunkerSchema, 
            Map.of("defaultChunkSize", 1000));
            
        PipelineModuleConfiguration embedderModule = new PipelineModuleConfiguration(
            "OpenAI Embedder", "embedder-openai", embedderSchema,
            Map.of("model", "text-embedding-ada-002"));
            
        PipelineModuleMap moduleMap = new PipelineModuleMap(Map.of(
            "chunker-service", chunkerModule,
            "embedder-openai", embedderModule
        ));

        // 2. Create pipeline steps
        JsonNode chunkerJsonConfig = JsonNodeFactory.instance.objectNode()
            .put("chunkSize", 500)
            .put("overlap", 50);
            
        PipelineStepConfig.JsonConfigOptions chunkerConfig = new PipelineStepConfig.JsonConfigOptions(
            chunkerJsonConfig, Map.of("timeout", "30s"));
            
        PipelineStepConfig.ProcessorInfo chunkerProcessor = new PipelineStepConfig.ProcessorInfo(
            "chunker-service", null);
            
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "embedder-openai", Map.of("timeout", "60s"));
            
        PipelineStepConfig.OutputTarget chunkerOutput = new PipelineStepConfig.OutputTarget(
            "embedder-step", TransportType.GRPC, grpcTransport, null);

        PipelineStepConfig chunkerStep = new PipelineStepConfig(
            "chunker-step", StepType.PIPELINE, "Document chunking step",
            "chunker-schema", chunkerConfig, List.of(),
            Map.of("primary", chunkerOutput), 3, 1000L, 30000L, 2.0, 25000L,
            chunkerProcessor);

        // Embedder step (sink)
        PipelineStepConfig.ProcessorInfo embedderProcessor = new PipelineStepConfig.ProcessorInfo(
            "embedder-openai", null);
            
        PipelineStepConfig embedderStep = new PipelineStepConfig(
            "embedder-step", StepType.SINK, "Embedding generation step",
            "embedder-schema", null, List.of(), Map.of(), 2, 2000L, 45000L, 1.5, 60000L,
            embedderProcessor);

        // 3. Create pipeline
        PipelineConfig pipeline = new PipelineConfig("document-processing", Map.of(
            "chunker-step", chunkerStep,
            "embedder-step", embedderStep
        ));

        // 4. Create pipeline graph
        PipelineGraphConfig pipelineGraph = new PipelineGraphConfig(Map.of(
            "document-processing", pipeline
        ));

        // 5. Create cluster config
        PipelineClusterConfig clusterConfig = new PipelineClusterConfig(
            "production-cluster",
            pipelineGraph,
            moduleMap,
            "document-processing",
            Set.of("input-documents", "processed-chunks", "embeddings-output"),
            Set.of("chunker-service", "embedder-openai", "opensearch-sink")
        );

        // Serialize
        String json = objectMapper.writeValueAsString(clusterConfig);

        // Verify major components are present
        assertTrue(json.contains("\"clusterName\":\"production-cluster\""));
        assertTrue(json.contains("\"defaultPipelineName\":\"document-processing\""));
        assertTrue(json.contains("\"Text Chunker Service\""));
        assertTrue(json.contains("\"OpenAI Embedder\""));
        assertTrue(json.contains("\"chunker-step\""));
        assertTrue(json.contains("\"embedder-step\""));
        assertTrue(json.contains("\"chunkSize\":500"));
        assertTrue(json.contains("\"input-documents\""));
        assertTrue(json.contains("\"chunker-service\""));

        // Test deserialization
        PipelineClusterConfig deserialized = objectMapper.readValue(json, PipelineClusterConfig.class);
        
        assertEquals("production-cluster", deserialized.clusterName());
        assertEquals("document-processing", deserialized.defaultPipelineName());
        assertEquals(2, deserialized.pipelineModuleMap().availableModules().size());
        assertEquals(1, deserialized.pipelineGraphConfig().pipelines().size());
        assertEquals(3, deserialized.allowedKafkaTopics().size());
        assertEquals(3, deserialized.allowedGrpcServices().size());
    }

    @Test
    public void testDeserializationFromOriginalFormat() throws Exception {
        // Test with JSON similar to the original test configuration
        String json = """
            {
                "clusterName": "test-cluster",
                "pipelineGraphConfig": {
                    "pipelines": {
                        "pipeline1": {
                            "name": "pipeline1",
                            "pipelineSteps": {
                                "step1": {
                                    "stepName": "step1",
                                    "stepType": "PIPELINE",
                                    "description": "First step",
                                    "customConfig": {
                                        "jsonConfig": {"key": "value", "threshold": 0.75}
                                    },
                                    "processorInfo": {
                                        "grpcServiceName": "test-module-1"
                                    }
                                }
                            }
                        }
                    }
                },
                "pipelineModuleMap": {
                    "availableModules": {
                        "test-module-1": {
                            "implementationName": "Test Module 1",
                            "implementationId": "test-module-1",
                            "customConfigSchemaReference": {
                                "subject": "test-module-1-schema",
                                "version": 1
                            }
                        }
                    }
                },
                "allowedKafkaTopics": [
                    "test-input-topic-1",
                    "test-output-topic-1"
                ],
                "allowedGrpcServices": [
                    "test-module-1"
                ]
            }
            """;

        PipelineClusterConfig config = objectMapper.readValue(json, PipelineClusterConfig.class);

        assertEquals("test-cluster", config.clusterName());
        assertNotNull(config.pipelineGraphConfig());
        assertNotNull(config.pipelineModuleMap());
        
        // Check pipeline
        PipelineConfig pipeline1 = config.pipelineGraphConfig().pipelines().get("pipeline1");
        assertNotNull(pipeline1);
        assertEquals("pipeline1", pipeline1.name());
        
        // Check step
        PipelineStepConfig step1 = pipeline1.pipelineSteps().get("step1");
        assertNotNull(step1);
        assertEquals("step1", step1.stepName());
        assertEquals(StepType.PIPELINE, step1.stepType());
        assertEquals("test-module-1", step1.processorInfo().grpcServiceName());
        
        // Check module
        PipelineModuleConfiguration module1 = config.pipelineModuleMap().availableModules().get("test-module-1");
        assertNotNull(module1);
        assertEquals("Test Module 1", module1.implementationName());
        
        // Check whitelists
        assertTrue(config.allowedKafkaTopics().contains("test-input-topic-1"));
        assertTrue(config.allowedGrpcServices().contains("test-module-1"));
    }

    @Test
    public void testMinimalConfiguration() throws Exception {
        PipelineClusterConfig minimalConfig = new PipelineClusterConfig(
            "minimal-cluster", null, null, null, null, null);

        String json = objectMapper.writeValueAsString(minimalConfig);
        assertTrue(json.contains("\"clusterName\":\"minimal-cluster\""));

        PipelineClusterConfig deserialized = objectMapper.readValue(json, PipelineClusterConfig.class);
        assertEquals("minimal-cluster", deserialized.clusterName());
        assertNull(deserialized.pipelineGraphConfig());
        assertNull(deserialized.pipelineModuleMap());
        assertNull(deserialized.defaultPipelineName());
        assertTrue(deserialized.allowedKafkaTopics().isEmpty());
        assertTrue(deserialized.allowedGrpcServices().isEmpty());
    }

    @Test
    public void testValidation() {
        // Valid cases
        assertDoesNotThrow(() -> new PipelineClusterConfig(
            "valid-cluster", null, null, null, Set.of("topic1"), Set.of("service1")));

        // Invalid cluster name
        assertThrows(IllegalArgumentException.class, () -> new PipelineClusterConfig(
            null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new PipelineClusterConfig(
            "", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new PipelineClusterConfig(
            "   ", null, null, null, null, null));

        // Invalid Kafka topics - note: Set.of() doesn't allow null values, so we need a different approach
        // We'll test the validation logic by creating a HashSet and converting to handle nulls
        assertThrows(IllegalArgumentException.class, () -> {
            Set<String> invalidTopics = new java.util.HashSet<>();
            invalidTopics.add("valid");
            invalidTopics.add("");
            new PipelineClusterConfig("cluster", null, null, null, invalidTopics, null);
        });

        // Invalid gRPC services  
        assertThrows(IllegalArgumentException.class, () -> {
            Set<String> invalidServices = new java.util.HashSet<>();
            invalidServices.add("valid");
            invalidServices.add("");
            new PipelineClusterConfig("cluster", null, null, null, null, invalidServices);
        });
    }

    @Test
    public void testImmutability() {
        Set<String> topics = Set.of("topic1");
        Set<String> services = Set.of("service1");
        
        PipelineClusterConfig config = new PipelineClusterConfig(
            "test-cluster", null, null, null, topics, services);

        // Sets should be immutable
        assertThrows(UnsupportedOperationException.class,
            () -> config.allowedKafkaTopics().add("new-topic"));
        assertThrows(UnsupportedOperationException.class,
            () -> config.allowedGrpcServices().add("new-service"));
    }
}