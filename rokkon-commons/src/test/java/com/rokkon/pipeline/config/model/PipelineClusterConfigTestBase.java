package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Base test class for PipelineClusterConfig serialization/deserialization.
 * Tests the top-level cluster configuration that contains all pipeline configurations.
 */
public abstract class PipelineClusterConfigTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testValidClusterConfig() {
        // Create pipeline graph config
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
                Map.of("document-pipeline", createTestPipelineConfig())
        );
        
        // Create module map
        PipelineModuleMap moduleMap = new PipelineModuleMap(
                Map.of("parser-module", createTestModuleConfig())
        );
        
        PipelineClusterConfig config = new PipelineClusterConfig(
                "production-cluster",
                graphConfig,
                moduleMap,
                "document-pipeline",
                Set.of("custom-topic-1", "custom-topic-2"),
                Set.of("external-service-1", "external-service-2")
        );
        
        assertThat(config.clusterName()).isEqualTo("production-cluster");
        assertThat(config.defaultPipelineName()).isEqualTo("document-pipeline");
        assertThat(config.allowedKafkaTopics()).containsExactlyInAnyOrder("custom-topic-1", "custom-topic-2");
        assertThat(config.allowedGrpcServices()).containsExactlyInAnyOrder("external-service-1", "external-service-2");
    }

    @Test
    public void testMinimalClusterConfig() {
        // Only cluster name is required
        PipelineClusterConfig config = new PipelineClusterConfig(
                "minimal-cluster",
                null,
                null,
                null,
                null,
                null
        );
        
        assertThat(config.clusterName()).isEqualTo("minimal-cluster");
        assertThat(config.pipelineGraphConfig()).isNull();
        assertThat(config.pipelineModuleMap()).isNull();
        assertThat(config.defaultPipelineName()).isNull();
        assertThat(config.allowedKafkaTopics()).isEmpty();
        assertThat(config.allowedGrpcServices()).isEmpty();
    }

    @Test
    public void testClusterNameValidation() {
        assertThatThrownBy(() -> new PipelineClusterConfig(null, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PipelineClusterConfig clusterName cannot be null or blank.");
            
        assertThatThrownBy(() -> new PipelineClusterConfig("", null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PipelineClusterConfig clusterName cannot be null or blank.");
            
        assertThatThrownBy(() -> new PipelineClusterConfig("   ", null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PipelineClusterConfig clusterName cannot be null or blank.");
    }

    @Test
    public void testAllowedTopicsValidation() {
        // Set.of() throws NullPointerException for null elements
        // This is expected Java behavior - Set.of() does not accept null values
        assertThatThrownBy(() -> new PipelineClusterConfig(
                "test-cluster",
                null,
                null,
                null,
                Set.of("valid-topic", null, "another-topic"),
                null
        ))
            .isInstanceOf(NullPointerException.class);
            
        // Test with HashSet to properly test our validation
        Set<String> topicsWithNull = new java.util.HashSet<>();
        topicsWithNull.add("valid-topic");
        topicsWithNull.add(null);
        topicsWithNull.add("another-topic");
        
        assertThatThrownBy(() -> new PipelineClusterConfig(
                "test-cluster",
                null,
                null,
                null,
                topicsWithNull,
                null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("allowedKafkaTopics cannot contain null or blank strings.");
            
        // Test blank string validation
        assertThatThrownBy(() -> new PipelineClusterConfig(
                "test-cluster",
                null,
                null,
                null,
                Set.of("valid-topic", "  ", "another-topic"),
                null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("allowedKafkaTopics cannot contain null or blank strings.");
    }

    @Test
    public void testAllowedServicesValidation() {
        // Set.of() throws NullPointerException for null elements
        // This is expected Java behavior - Set.of() does not accept null values
        assertThatThrownBy(() -> new PipelineClusterConfig(
                "test-cluster",
                null,
                null,
                null,
                null,
                Set.of("valid-service", null, "another-service")
        ))
            .isInstanceOf(NullPointerException.class);
            
        // Test with HashSet to properly test our validation
        Set<String> servicesWithNull = new java.util.HashSet<>();
        servicesWithNull.add("valid-service");
        servicesWithNull.add(null);
        servicesWithNull.add("another-service");
        
        assertThatThrownBy(() -> new PipelineClusterConfig(
                "test-cluster",
                null,
                null,
                null,
                null,
                servicesWithNull
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("allowedGrpcServices cannot contain null or blank strings.");
            
        // Test blank string validation
        assertThatThrownBy(() -> new PipelineClusterConfig(
                "test-cluster",
                null,
                null,
                null,
                null,
                Set.of("valid-service", "  ", "another-service")
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("allowedGrpcServices cannot contain null or blank strings.");
    }

    @Test
    public void testImmutability() {
        Set<String> mutableTopics = new java.util.HashSet<>();
        mutableTopics.add("topic1");
        
        Set<String> mutableServices = new java.util.HashSet<>();
        mutableServices.add("service1");
        
        PipelineClusterConfig config = new PipelineClusterConfig(
                "immutable-cluster",
                null,
                null,
                null,
                mutableTopics,
                mutableServices
        );
        
        // Try to modify original sets
        mutableTopics.add("topic2");
        mutableServices.add("service2");
        
        // Config should not be affected
        assertThat(config.allowedKafkaTopics()).hasSize(1);
        assertThat(config.allowedGrpcServices()).hasSize(1);
        
        // Returned sets should be immutable
        assertThat(config.allowedKafkaTopics()).isUnmodifiable();
        assertThat(config.allowedGrpcServices()).isUnmodifiable();
    }

    @Test
    public void testSerializationDeserialization() throws Exception {
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
                Map.of("test-pipeline", createTestPipelineConfig())
        );
        
        PipelineModuleMap moduleMap = new PipelineModuleMap(
                Map.of("test-module", createTestModuleConfig())
        );
        
        PipelineClusterConfig original = new PipelineClusterConfig(
                "serialization-test-cluster",
                graphConfig,
                moduleMap,
                "test-pipeline",
                Set.of("topic-a", "topic-b"),
                Set.of("service-x", "service-y")
        );
        
        String json = getObjectMapper().writeValueAsString(original);
        
        // Verify JSON structure
        assertThat(json).contains("\"clusterName\":\"serialization-test-cluster\"");
        assertThat(json).contains("\"defaultPipelineName\":\"test-pipeline\"");
        assertThat(json).contains("\"allowedKafkaTopics\"");
        assertThat(json).contains("\"allowedGrpcServices\"");
        
        // Deserialize
        PipelineClusterConfig deserialized = getObjectMapper().readValue(json, PipelineClusterConfig.class);
        
        assertThat(deserialized.clusterName()).isEqualTo(original.clusterName());
        assertThat(deserialized.defaultPipelineName()).isEqualTo(original.defaultPipelineName());
        assertThat(deserialized.allowedKafkaTopics()).isEqualTo(original.allowedKafkaTopics());
        assertThat(deserialized.allowedGrpcServices()).isEqualTo(original.allowedGrpcServices());
    }

    @Test
    public void testDeserializationFromJson() throws Exception {
        String json = """
            {
                "clusterName": "production-rokkon-cluster",
                "pipelineGraphConfig": {
                    "pipelines": {
                        "document-processing": {
                            "name": "document-processing",
                            "pipelineSteps": {}
                        }
                    }
                },
                "pipelineModuleMap": {
                    "availableModules": {
                        "parser": {
                            "implementationName": "parser",
                            "implementationId": "parser-module-v1"
                        }
                    }
                },
                "defaultPipelineName": "document-processing",
                "allowedKafkaTopics": [
                    "external.events.input",
                    "external.events.output",
                    "audit.topic"
                ],
                "allowedGrpcServices": [
                    "external-nlp-service",
                    "external-translation-service"
                ]
            }
            """;
        
        PipelineClusterConfig config = getObjectMapper().readValue(json, PipelineClusterConfig.class);
        
        assertThat(config.clusterName()).isEqualTo("production-rokkon-cluster");
        assertThat(config.defaultPipelineName()).isEqualTo("document-processing");
        assertThat(config.allowedKafkaTopics()).containsExactlyInAnyOrder(
            "external.events.input", 
            "external.events.output", 
            "audit.topic"
        );
        assertThat(config.allowedGrpcServices()).containsExactlyInAnyOrder(
            "external-nlp-service",
            "external-translation-service"
        );
        
        // Verify nested objects
        assertThat(config.pipelineGraphConfig()).isNotNull();
        assertThat(config.pipelineGraphConfig().pipelines()).containsKey("document-processing");
        assertThat(config.pipelineModuleMap()).isNotNull();
        assertThat(config.pipelineModuleMap().availableModules()).containsKey("parser");
    }

    @Test
    public void testRealWorldClusterConfiguration() throws Exception {
        // Test a comprehensive production-like configuration
        String json = """
            {
                "clusterName": "rokkon-prod-us-east-1",
                "pipelineGraphConfig": {
                    "pipelines": {
                        "document-indexing": {
                            "name": "document-indexing",
                            "pipelineSteps": {
                                "parser": {
                                    "stepName": "tika-parser",
                                    "stepType": "INITIAL_PIPELINE",
                                    "processorInfo": {
                                        "grpcServiceName": "tika-parser-service"
                                    }
                                }
                            }
                        },
                        "realtime-analysis": {
                            "name": "realtime-analysis",
                            "pipelineSteps": {
                                "analyzer": {
                                    "stepName": "nlp-analyzer",
                                    "stepType": "PIPELINE",
                                    "processorInfo": {
                                        "grpcServiceName": "nlp-service"
                                    }
                                }
                            }
                        }
                    }
                },
                "pipelineModuleMap": {
                    "availableModules": {
                        "tika-parser": {
                            "implementationName": "tika-parser",
                            "implementationId": "tika-parser-v2",
                            "customConfigSchemaReference": {
                                "subject": "tika-config-v1",
                                "version": 1
                            }
                        },
                        "nlp-analyzer": {
                            "implementationName": "nlp-analyzer",
                            "implementationId": "nlp-analyzer-v1"
                        }
                    }
                },
                "defaultPipelineName": "document-indexing",
                "allowedKafkaTopics": [
                    "external.documents.input",
                    "external.events.stream",
                    "audit.all-events",
                    "monitoring.metrics",
                    "cross-region.sync"
                ],
                "allowedGrpcServices": [
                    "external-ocr-service",
                    "external-translation-api",
                    "legacy-search-service",
                    "third-party-enrichment"
                ]
            }
            """;
        
        PipelineClusterConfig config = getObjectMapper().readValue(json, PipelineClusterConfig.class);
        
        assertThat(config.clusterName()).isEqualTo("rokkon-prod-us-east-1");
        assertThat(config.pipelineGraphConfig().pipelines()).hasSize(2);
        assertThat(config.pipelineModuleMap().availableModules()).hasSize(2);
        assertThat(config.allowedKafkaTopics()).hasSize(5);
        assertThat(config.allowedGrpcServices()).hasSize(4);
        
        // Verify specific pipeline exists
        assertThat(config.pipelineGraphConfig().pipelines()).containsKeys("document-indexing", "realtime-analysis");
        
        // Verify module configuration
        PipelineModuleConfiguration tikaModule = config.pipelineModuleMap().availableModules().get("tika-parser");
        assertThat(tikaModule.implementationName()).isEqualTo("tika-parser");
        assertThat(tikaModule.customConfigSchemaReference()).isNotNull();
        assertThat(tikaModule.customConfigSchemaReference().subject()).isEqualTo("tika-config-v1");
    }

    // Helper methods to create test objects
    private PipelineConfig createTestPipelineConfig() {
        return new PipelineConfig("test-pipeline", Map.of());
    }
    
    private PipelineModuleConfiguration createTestModuleConfig() {
        return new PipelineModuleConfiguration(
            "test-module",
            "test-module-id",
            null,
            Map.of()
        );
    }
}