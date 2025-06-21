package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Base test class for PipelineModuleConfiguration serialization/deserialization.
 * Tests pipeline module configuration including custom config handling.
 */
public abstract class PipelineModuleConfigurationTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testValidConfiguration() {
        SchemaReference schemaRef = new SchemaReference("parser-config-schema", 1);
        Map<String, Object> customConfig = Map.of(
            "parserType", "tika",
            "maxFileSize", 10485760,
            "supportedFormats", Map.of(
                "pdf", true,
                "docx", true,
                "html", false
            )
        );
        
        PipelineModuleConfiguration config = new PipelineModuleConfiguration(
            "TikaParser",
            "com.rokkon.parser.tika",
            schemaRef,
            customConfig
        );
        
        assertThat(config.implementationName()).isEqualTo("TikaParser");
        assertThat(config.implementationId()).isEqualTo("com.rokkon.parser.tika");
        assertThat(config.customConfigSchemaReference()).isEqualTo(schemaRef);
        assertThat(config.customConfig())
            .containsEntry("parserType", "tika")
            .containsEntry("maxFileSize", 10485760);
    }

    @Test
    public void testMinimalConfiguration() {
        // Custom config and schema reference are optional
        PipelineModuleConfiguration config = new PipelineModuleConfiguration(
            "SimpleModule",
            "com.rokkon.simple",
            null,
            null
        );
        
        assertThat(config.implementationName()).isEqualTo("SimpleModule");
        assertThat(config.implementationId()).isEqualTo("com.rokkon.simple");
        assertThat(config.customConfigSchemaReference()).isNull();
        assertThat(config.customConfig()).isEmpty();
    }

    @Test
    public void testConvenienceConstructor() {
        SchemaReference schemaRef = new SchemaReference("enricher-schema", 2);
        
        PipelineModuleConfiguration config = new PipelineModuleConfiguration(
            "EntityEnricher",
            "com.rokkon.enricher.entity",
            schemaRef
        );
        
        assertThat(config.implementationName()).isEqualTo("EntityEnricher");
        assertThat(config.implementationId()).isEqualTo("com.rokkon.enricher.entity");
        assertThat(config.customConfigSchemaReference()).isEqualTo(schemaRef);
        assertThat(config.customConfig()).isEmpty();
    }

    @Test
    public void testNullImplementationNameValidation() {
        assertThatThrownBy(() -> new PipelineModuleConfiguration(
            null,
            "com.rokkon.test",
            null,
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PipelineModuleConfiguration implementationName cannot be null or blank.");
    }

    @Test
    public void testBlankImplementationNameValidation() {
        assertThatThrownBy(() -> new PipelineModuleConfiguration(
            "   ",
            "com.rokkon.test",
            null,
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PipelineModuleConfiguration implementationName cannot be null or blank.");
    }

    @Test
    public void testNullImplementationIdValidation() {
        assertThatThrownBy(() -> new PipelineModuleConfiguration(
            "TestModule",
            null,
            null,
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PipelineModuleConfiguration implementationId cannot be null or blank.");
    }

    @Test
    public void testBlankImplementationIdValidation() {
        assertThatThrownBy(() -> new PipelineModuleConfiguration(
            "TestModule",
            "",
            null,
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PipelineModuleConfiguration implementationId cannot be null or blank.");
    }

    @Test
    public void testCustomConfigImmutability() {
        Map<String, Object> mutableConfig = new java.util.HashMap<>();
        mutableConfig.put("key1", "value1");
        
        PipelineModuleConfiguration config = new PipelineModuleConfiguration(
            "ImmutableTest",
            "com.rokkon.immutable",
            null,
            mutableConfig
        );
        
        // Try to modify original map
        mutableConfig.put("key2", "value2");
        
        // Config should not be affected
        assertThat(config.customConfig()).hasSize(1);
        assertThat(config.customConfig()).isUnmodifiable();
    }

    @Test
    public void testSerializationDeserialization() throws Exception {
        PipelineModuleConfiguration original = new PipelineModuleConfiguration(
            "ChunkerModule",
            "com.rokkon.chunker.sliding",
            new SchemaReference("chunker-config-v1", 3),
            Map.of(
                "chunkSize", 1000,
                "overlapSize", 100,
                "strategy", "sliding_window",
                "metadata", Map.of(
                    "preserveHeaders", true,
                    "includePositions", false
                )
            )
        );
        
        String json = getObjectMapper().writeValueAsString(original);
        
        // Verify JSON structure
        assertThat(json).contains("\"implementationName\":\"ChunkerModule\"");
        assertThat(json).contains("\"implementationId\":\"com.rokkon.chunker.sliding\"");
        assertThat(json).contains("\"subject\":\"chunker-config-v1\"");
        assertThat(json).contains("\"chunkSize\":1000");
        
        // Deserialize
        PipelineModuleConfiguration deserialized = getObjectMapper().readValue(json, PipelineModuleConfiguration.class);
        
        assertThat(deserialized.implementationName()).isEqualTo(original.implementationName());
        assertThat(deserialized.implementationId()).isEqualTo(original.implementationId());
        assertThat(deserialized.customConfigSchemaReference()).isEqualTo(original.customConfigSchemaReference());
        assertThat(deserialized.customConfig()).isEqualTo(original.customConfig());
    }

    @Test
    public void testDeserializationFromJson() throws Exception {
        String json = """
            {
                "implementationName": "AdvancedClassifier",
                "implementationId": "com.rokkon.classifier.ml",
                "customConfigSchemaReference": {
                    "subject": "classifier-ml-config",
                    "version": 5
                },
                "customConfig": {
                    "modelType": "bert",
                    "confidence_threshold": 0.85,
                    "categories": ["technical", "business", "legal"],
                    "features": {
                        "useContext": true,
                        "maxTokens": 512
                    }
                }
            }
            """;
        
        PipelineModuleConfiguration config = getObjectMapper().readValue(json, PipelineModuleConfiguration.class);
        
        assertThat(config.implementationName()).isEqualTo("AdvancedClassifier");
        assertThat(config.implementationId()).isEqualTo("com.rokkon.classifier.ml");
        assertThat(config.customConfigSchemaReference().subject()).isEqualTo("classifier-ml-config");
        assertThat(config.customConfigSchemaReference().version()).isEqualTo(5);
        assertThat(config.customConfig())
            .containsEntry("modelType", "bert")
            .containsEntry("confidence_threshold", 0.85)
            .containsKey("categories");
    }

    @Test
    public void testNullFieldsOmittedInJson() throws Exception {
        PipelineModuleConfiguration config = new PipelineModuleConfiguration(
            "MinimalModule",
            "com.rokkon.minimal",
            null,
            null
        );
        
        String json = getObjectMapper().writeValueAsString(config);
        
        // Verify null fields are omitted due to @JsonInclude(JsonInclude.Include.NON_NULL)
        assertThat(json).contains("\"implementationName\":\"MinimalModule\"");
        assertThat(json).contains("\"implementationId\":\"com.rokkon.minimal\"");
        assertThat(json).doesNotContain("customConfigSchemaReference");
        assertThat(json).doesNotContain("\"customConfig\":null");
    }

    @Test
    public void testRealWorldParserConfiguration() throws Exception {
        String json = """
            {
                "implementationName": "ApacheTikaParser",
                "implementationId": "com.rokkon.parser.tika.full",
                "customConfigSchemaReference": {
                    "subject": "tika-parser-config",
                    "version": 2
                },
                "customConfig": {
                    "parseMode": "structured",
                    "maxFileSize": 52428800,
                    "timeout": 30000,
                    "detectLanguage": true,
                    "extractMetadata": true,
                    "ocrConfig": {
                        "enabled": true,
                        "languages": ["eng", "spa", "fra"],
                        "dpi": 300
                    },
                    "contentHandlers": {
                        "pdf": "pdfbox",
                        "office": "poi",
                        "html": "jsoup"
                    }
                }
            }
            """;
        
        PipelineModuleConfiguration config = getObjectMapper().readValue(json, PipelineModuleConfiguration.class);
        
        assertThat(config.implementationName()).isEqualTo("ApacheTikaParser");
        assertThat(config.customConfig()).containsKey("ocrConfig");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> ocrConfig = (Map<String, Object>) config.customConfig().get("ocrConfig");
        assertThat(ocrConfig).containsEntry("enabled", true);
    }
}