package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Advanced test cases for PipelineConfig covering fan-in/fan-out scenarios
 * and various transport configurations.
 */
public abstract class PipelineConfigAdvancedTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testFanOutConfiguration() throws Exception {
        // Create a pipeline where one step outputs to multiple targets
        Map<String, PipelineStepConfig> steps = new HashMap<>();
        
        // Parser outputs to both chunker and metadata-extractor (fan-out)
        PipelineStepConfig.OutputTarget toChunker = new PipelineStepConfig.OutputTarget(
                "chunker",
                TransportType.KAFKA,
                null,
                new KafkaTransportConfig("parsed-docs", "pipedocId", "snappy", 32768, 20, Map.of("max.request.size", "20971520"))
        );
        
        PipelineStepConfig.OutputTarget toMetadataExtractor = new PipelineStepConfig.OutputTarget(
                "metadata-extractor",
                TransportType.GRPC,
                new GrpcTransportConfig("metadata-service", Map.of("retry", "3", "timeout", "30000")),
                null
        );
        
        steps.put("parser", new PipelineStepConfig(
                "document-parser",
                StepType.INITIAL_PIPELINE,
                "Parses documents and fans out to multiple processors",
                null,
                new PipelineStepConfig.JsonConfigOptions(Map.of("format", "pdf")),
                null,
                Map.of("content", toChunker, "metadata", toMetadataExtractor),
                3,
                1000L,
                30000L,
                2.0,
                60000L,
                new PipelineStepConfig.ProcessorInfo("parser-service", null)
        ));
        
        // Add downstream steps
        steps.put("chunker", new PipelineStepConfig(
                "text-chunker",
                StepType.PIPELINE,
                new PipelineStepConfig.ProcessorInfo("chunker-service", null)
        ));
        
        steps.put("metadata-extractor", new PipelineStepConfig(
                "metadata-processor",
                StepType.PIPELINE,
                new PipelineStepConfig.ProcessorInfo(null, "metadataBean")
        ));
        
        PipelineConfig config = new PipelineConfig("fan-out-pipeline", steps);
        
        // Serialize and deserialize
        String json = getObjectMapper().writeValueAsString(config);
        PipelineConfig deserialized = getObjectMapper().readValue(json, PipelineConfig.class);
        
        // Verify fan-out configuration
        PipelineStepConfig parserStep = deserialized.pipelineSteps().get("parser");
        assertThat(parserStep.outputs()).hasSize(2);
        assertThat(parserStep.outputs().keySet()).containsExactlyInAnyOrder("content", "metadata");
        
        // Verify Kafka output
        PipelineStepConfig.OutputTarget contentOutput = parserStep.outputs().get("content");
        assertThat(contentOutput.transportType()).isEqualTo(TransportType.KAFKA);
        assertThat(contentOutput.kafkaTransport().topic()).isEqualTo("parsed-docs");
        assertThat(contentOutput.kafkaTransport().partitionKeyField()).isEqualTo("pipedocId");
        assertThat(contentOutput.kafkaTransport().compressionType()).isEqualTo("snappy");
        
        // Verify gRPC output
        PipelineStepConfig.OutputTarget metadataOutput = parserStep.outputs().get("metadata");
        assertThat(metadataOutput.transportType()).isEqualTo(TransportType.GRPC);
        assertThat(metadataOutput.grpcTransport().serviceName()).isEqualTo("metadata-service");
        assertThat(metadataOutput.grpcTransport().grpcClientProperties()).containsEntry("retry", "3");
    }

    @Test
    public void testFanInConfiguration() throws Exception {
        // Create a pipeline where multiple steps feed into one (fan-in)
        Map<String, PipelineStepConfig> steps = new HashMap<>();
        
        // Text extractor outputs to enricher
        PipelineStepConfig.OutputTarget textToEnricher = new PipelineStepConfig.OutputTarget(
                "enricher",
                TransportType.KAFKA,
                null,
                new KafkaTransportConfig("enrichment-input", null, null, null, null, Map.of("client.id", "text-producer"))
        );
        
        steps.put("text-extractor", new PipelineStepConfig(
                "text-extraction",
                StepType.INITIAL_PIPELINE,
                "Extracts text from documents",
                null,
                null,
                null,
                Map.of("default", textToEnricher),
                3,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo("text-service", null)
        ));
        
        // Metadata extractor also outputs to enricher
        PipelineStepConfig.OutputTarget metadataToEnricher = new PipelineStepConfig.OutputTarget(
                "enricher",
                TransportType.KAFKA,
                null,
                new KafkaTransportConfig("enrichment-input", null, null, null, null, Map.of("client.id", "metadata-producer"))
        );
        
        steps.put("metadata-extractor", new PipelineStepConfig(
                "metadata-extraction",
                StepType.INITIAL_PIPELINE,
                "Extracts metadata from documents",
                null,
                null,
                null,
                Map.of("default", metadataToEnricher),
                3,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo("metadata-service", null)
        ));
        
        // Enricher receives from multiple sources (fan-in)
        steps.put("enricher", new PipelineStepConfig(
                "document-enricher",
                StepType.PIPELINE,
                "Enriches documents with data from multiple sources",
                null,
                new PipelineStepConfig.JsonConfigOptions(Map.of("merge-strategy", "combine")),
                List.of(new KafkaInputDefinition(
                    List.of("enrichment-input"),
                    "enricher-group",
                    Map.of("auto.offset.reset", "earliest")
                )),
                null,
                5,
                2000L,
                60000L,
                2.5,
                120000L,
                new PipelineStepConfig.ProcessorInfo("enricher-service", null)
        ));
        
        PipelineConfig config = new PipelineConfig("fan-in-pipeline", steps);
        
        // Serialize and deserialize
        String json = getObjectMapper().writeValueAsString(config);
        PipelineConfig deserialized = getObjectMapper().readValue(json, PipelineConfig.class);
        
        // Verify fan-in configuration
        PipelineStepConfig enricherStep = deserialized.pipelineSteps().get("enricher");
        assertThat(enricherStep.kafkaInputs()).hasSize(1);
        assertThat(enricherStep.kafkaInputs().get(0).listenTopics()).containsExactly("enrichment-input");
        assertThat(enricherStep.kafkaInputs().get(0).consumerGroupId()).isEqualTo("enricher-group");
        
        // Verify both extractors point to enricher
        PipelineStepConfig textExtractor = deserialized.pipelineSteps().get("text-extractor");
        assertThat(textExtractor.outputs().get("default").targetStepName()).isEqualTo("enricher");
        
        PipelineStepConfig metadataExtractor = deserialized.pipelineSteps().get("metadata-extractor");
        assertThat(metadataExtractor.outputs().get("default").targetStepName()).isEqualTo("enricher");
    }

    @Test
    public void testMixedTransportTypes() throws Exception {
        // Test a pipeline with various transport configurations
        Map<String, PipelineStepConfig> steps = new HashMap<>();
        
        // Step with internal transport
        PipelineStepConfig.OutputTarget internalOutput = new PipelineStepConfig.OutputTarget(
                "processor",
                TransportType.INTERNAL,
                null,
                null
        );
        
        steps.put("loader", new PipelineStepConfig(
                "data-loader",
                StepType.INITIAL_PIPELINE,
                "Loads data using internal transport",
                null,
                null,
                null,
                Map.of("default", internalOutput),
                0,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo(null, "loaderBean")
        ));
        
        // Step with multiple Kafka inputs
        List<KafkaInputDefinition> multipleInputs = List.of(
                new KafkaInputDefinition(
                    List.of("topic1", "topic2"),
                    "consumer-group-1",
                    Map.of("max.poll.records", "500")
                ),
                new KafkaInputDefinition(
                    List.of("topic3"),
                    null, // Let the engine generate the consumer group
                    Map.of("fetch.min.bytes", "1024")
                )
        );
        
        steps.put("aggregator", new PipelineStepConfig(
                "data-aggregator",
                StepType.PIPELINE,
                "Aggregates data from multiple Kafka topics",
                null,
                null,
                multipleInputs,
                null,
                3,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo("aggregator-service", null)
        ));
        
        PipelineConfig config = new PipelineConfig("mixed-transport-pipeline", steps);
        
        // Serialize and deserialize
        String json = getObjectMapper().writeValueAsString(config);
        PipelineConfig deserialized = getObjectMapper().readValue(json, PipelineConfig.class);
        
        // Verify internal transport
        PipelineStepConfig loaderStep = deserialized.pipelineSteps().get("loader");
        assertThat(loaderStep.outputs().get("default").transportType()).isEqualTo(TransportType.INTERNAL);
        assertThat(loaderStep.processorInfo().internalProcessorBeanName()).isEqualTo("loaderBean");
        
        // Verify multiple Kafka inputs
        PipelineStepConfig aggregatorStep = deserialized.pipelineSteps().get("aggregator");
        assertThat(aggregatorStep.kafkaInputs()).hasSize(2);
        
        KafkaInputDefinition firstInput = aggregatorStep.kafkaInputs().get(0);
        assertThat(firstInput.listenTopics()).containsExactly("topic1", "topic2");
        assertThat(firstInput.consumerGroupId()).isEqualTo("consumer-group-1");
        
        KafkaInputDefinition secondInput = aggregatorStep.kafkaInputs().get(1);
        assertThat(secondInput.listenTopics()).containsExactly("topic3");
        assertThat(secondInput.consumerGroupId()).isNull();
    }

    @Test
    public void testComplexDataFlowPattern() throws Exception {
        // Test a diamond pattern: A -> B,C -> D
        Map<String, PipelineStepConfig> steps = new HashMap<>();
        
        // A outputs to both B and C
        PipelineStepConfig.OutputTarget aToB = new PipelineStepConfig.OutputTarget(
                "step-b",
                TransportType.GRPC,
                new GrpcTransportConfig("service-b", null),
                null
        );
        
        PipelineStepConfig.OutputTarget aToC = new PipelineStepConfig.OutputTarget(
                "step-c",
                TransportType.GRPC,
                new GrpcTransportConfig("service-c", null),
                null
        );
        
        steps.put("step-a", new PipelineStepConfig(
                "initial-processor",
                StepType.INITIAL_PIPELINE,
                "Initial step that splits processing",
                null,
                null,
                null,
                Map.of("path1", aToB, "path2", aToC),
                3,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo("service-a", null)
        ));
        
        // B outputs to D
        PipelineStepConfig.OutputTarget bToD = new PipelineStepConfig.OutputTarget(
                "step-d",
                TransportType.KAFKA,
                null,
                new KafkaTransportConfig("merge-topic", null, null, null, null, null)
        );
        
        steps.put("step-b", new PipelineStepConfig(
                "path1-processor",
                StepType.PIPELINE,
                "Processes path 1",
                null,
                null,
                null,
                Map.of("default", bToD),
                3,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo("service-b", null)
        ));
        
        // C outputs to D
        PipelineStepConfig.OutputTarget cToD = new PipelineStepConfig.OutputTarget(
                "step-d",
                TransportType.KAFKA,
                null,
                new KafkaTransportConfig("merge-topic", null, null, null, null, null)
        );
        
        steps.put("step-c", new PipelineStepConfig(
                "path2-processor",
                StepType.PIPELINE,
                "Processes path 2",
                null,
                null,
                null,
                Map.of("default", cToD),
                3,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo("service-c", null)
        ));
        
        // D receives from both B and C
        steps.put("step-d", new PipelineStepConfig(
                "merge-processor",
                StepType.SINK,
                "Merges results from both paths",
                null,
                null,
                List.of(new KafkaInputDefinition(
                    List.of("merge-topic"),
                    "merge-group",
                    null
                )),
                null,
                3,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo("service-d", null)
        ));
        
        PipelineConfig config = new PipelineConfig("diamond-pattern-pipeline", steps);
        
        // Serialize and deserialize
        String json = getObjectMapper().writeValueAsString(config);
        PipelineConfig deserialized = getObjectMapper().readValue(json, PipelineConfig.class);
        
        // Verify the diamond pattern
        assertThat(deserialized.pipelineSteps()).hasSize(4);
        
        // Verify A outputs to both B and C
        PipelineStepConfig stepA = deserialized.pipelineSteps().get("step-a");
        assertThat(stepA.outputs()).hasSize(2);
        assertThat(stepA.outputs().get("path1").targetStepName()).isEqualTo("step-b");
        assertThat(stepA.outputs().get("path2").targetStepName()).isEqualTo("step-c");
        
        // Verify both B and C output to D
        PipelineStepConfig stepB = deserialized.pipelineSteps().get("step-b");
        assertThat(stepB.outputs().get("default").targetStepName()).isEqualTo("step-d");
        
        PipelineStepConfig stepC = deserialized.pipelineSteps().get("step-c");
        assertThat(stepC.outputs().get("default").targetStepName()).isEqualTo("step-d");
        
        // Verify D is configured as a sink with Kafka input
        PipelineStepConfig stepD = deserialized.pipelineSteps().get("step-d");
        assertThat(stepD.stepType()).isEqualTo(StepType.SINK);
        assertThat(stepD.kafkaInputs()).hasSize(1);
        assertThat(stepD.kafkaInputs().get(0).listenTopics()).containsExactly("merge-topic");
    }
}