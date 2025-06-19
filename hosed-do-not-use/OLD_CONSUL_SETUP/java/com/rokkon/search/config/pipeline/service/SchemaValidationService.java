package com.rokkon.search.config.pipeline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.search.config.pipeline.service.validation.ValidationResult;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Service for validating JSON configurations against JSON Schema v7.
 * Migrated from the old Micronaut service to Quarkus patterns.
 */
@ApplicationScoped
@RegisterForReflection
public class SchemaValidationService {
    
    private static final Logger LOG = LoggerFactory.getLogger(SchemaValidationService.class);
    
    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory;
    
    // Schema resource paths
    private static final String PIPELINE_CLUSTER_CONFIG_SCHEMA = "/schemas/pipeline-cluster-config-schema.json";
    private static final String PIPELINE_STEP_CONFIG_SCHEMA = "/schemas/pipeline-step-config-schema.json";
    
    @Inject
    public SchemaValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    }
    
    /**
     * Validates JSON content against a schema.
     */
    public CompletionStage<ValidationResult> validateJson(String jsonContent, String schemaContent) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonNode jsonNode = objectMapper.readTree(jsonContent);
                JsonNode schemaNode = objectMapper.readTree(schemaContent);
                return validateJsonSync(jsonNode, schemaNode);
            } catch (IOException e) {
                LOG.error("Failed to parse JSON or schema: {}", e.getMessage());
                return ValidationResult.failure("Failed to parse JSON or schema: " + e.getMessage());
            }
        });
    }
    
    /**
     * Validates JSON nodes against a schema.
     */
    public CompletionStage<ValidationResult> validateJson(JsonNode jsonNode, JsonNode schemaNode) {
        return CompletableFuture.supplyAsync(() -> validateJsonSync(jsonNode, schemaNode));
    }
    
    /**
     * Synchronous validation of JSON nodes.
     */
    private ValidationResult validateJsonSync(JsonNode jsonNode, JsonNode schemaNode) {
        try {
            JsonSchema schema = schemaFactory.getSchema(schemaNode);
            Set<ValidationMessage> errors = schema.validate(jsonNode);
            
            if (errors.isEmpty()) {
                return ValidationResult.success();
            } else {
                List<String> errorMessages = errors.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.toList());
                
                LOG.debug("Validation errors: {}", errorMessages);
                return ValidationResult.failure(errorMessages);
            }
        } catch (Exception e) {
            LOG.error("Error during validation: {}", e.getMessage(), e);
            return ValidationResult.failure("Validation error: " + e.getMessage());
        }
    }
    
    /**
     * Validates a pipeline cluster configuration against its schema.
     */
    public CompletionStage<ValidationResult> validatePipelineClusterConfig(String clusterConfigJson) {
        return getPipelineClusterConfigSchema()
                .thenCompose(schema -> validateJson(clusterConfigJson, schema));
    }
    
    /**
     * Validates a pipeline step configuration against its schema.
     */
    public CompletionStage<ValidationResult> validatePipelineStepConfig(String stepConfigJson) {
        return getPipelineStepConfigSchema()
                .thenCompose(schema -> validateJson(stepConfigJson, schema));
    }
    
    /**
     * Validates custom module configuration against a provided schema.
     */
    public CompletionStage<ValidationResult> validateCustomModuleConfig(String customConfigJson, String customConfigSchemaJson) {
        if (customConfigSchemaJson == null || customConfigSchemaJson.trim().isEmpty()) {
            // If no schema provided, just validate it's valid JSON
            return isValidJson(customConfigJson)
                    .thenApply(valid -> valid ? ValidationResult.success() : ValidationResult.failure("Invalid JSON syntax"));
        }
        
        return validateJson(customConfigJson, customConfigSchemaJson);
    }
    
    /**
     * Validates that a string is a valid JSON Schema v7.
     * Only checks JSON Schema v7 spec compliance - no forced fields.
     */
    public ValidationResult validateJsonSchema(String schemaContent) {
        // Check for empty or null schema
        if (schemaContent == null || schemaContent.trim().isEmpty()) {
            LOG.error("Empty or null schema provided");
            return ValidationResult.failure("Schema content cannot be empty");
        }
        
        try {
            // First check if it's valid JSON
            JsonNode schemaNode = objectMapper.readTree(schemaContent);
            
            // Try to create a JsonSchema from it - this validates it's a proper schema
            JsonSchema schema = schemaFactory.getSchema(schemaNode);
            
            // If we get here without exception, it's a valid JSON Schema v7
            LOG.debug("JSON Schema validation passed");
            return ValidationResult.success();
            
        } catch (IOException e) {
            LOG.error("Invalid JSON in schema: {}", e.getMessage());
            return ValidationResult.failure("Invalid JSON syntax: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Invalid JSON Schema: {}", e.getMessage());
            return ValidationResult.failure("Invalid JSON Schema v7: " + e.getMessage());
        }
    }
    
    /**
     * Checks if a string is valid JSON.
     */
    public CompletionStage<Boolean> isValidJson(String jsonString) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                objectMapper.readTree(jsonString);
                return true;
            } catch (IOException e) {
                return false;
            }
        });
    }
    
    /**
     * Gets the pipeline cluster configuration schema.
     */
    public CompletionStage<String> getPipelineClusterConfigSchema() {
        return loadSchemaFromResource(PIPELINE_CLUSTER_CONFIG_SCHEMA)
                .thenApply(schema -> schema != null ? schema : getDefaultPipelineClusterConfigSchema());
    }
    
    /**
     * Gets the pipeline step configuration schema.
     */
    public CompletionStage<String> getPipelineStepConfigSchema() {
        return loadSchemaFromResource(PIPELINE_STEP_CONFIG_SCHEMA)
                .thenApply(schema -> schema != null ? schema : getDefaultPipelineStepConfigSchema());
    }
    
    private CompletionStage<String> loadSchemaFromResource(String resourcePath) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
                if (is != null) {
                    return new String(is.readAllBytes());
                }
            } catch (Exception e) {
                LOG.warn("Failed to load schema from {}: {}", resourcePath, e.getMessage());
            }
            return null;
        });
    }
    
    /**
     * Default schema for pipeline cluster configuration.
     */
    private String getDefaultPipelineClusterConfigSchema() {
        return """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "required": ["clusterName"],
              "properties": {
                "clusterName": {
                  "type": "string",
                  "minLength": 1
                },
                "pipelineGraphConfig": {
                  "type": "object",
                  "properties": {
                    "pipelines": {
                      "type": "object",
                      "additionalProperties": {
                        "$ref": "#/definitions/pipelineConfig"
                      }
                    }
                  }
                },
                "pipelineModuleMap": {
                  "type": "object",
                  "properties": {
                    "availableModules": {
                      "type": "object",
                      "additionalProperties": {
                        "$ref": "#/definitions/moduleConfig"
                      }
                    }
                  }
                },
                "defaultPipelineName": {
                  "type": "string"
                },
                "allowedKafkaTopics": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                },
                "allowedGrpcServices": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                }
              },
              "definitions": {
                "pipelineConfig": {
                  "type": "object",
                  "required": ["name", "pipelineSteps"],
                  "properties": {
                    "name": {
                      "type": "string",
                      "minLength": 1
                    },
                    "pipelineSteps": {
                      "type": "object"
                    }
                  }
                },
                "moduleConfig": {
                  "type": "object",
                  "required": ["implementationName", "implementationId"],
                  "properties": {
                    "implementationName": {
                      "type": "string",
                      "minLength": 1
                    },
                    "implementationId": {
                      "type": "string",
                      "minLength": 1
                    },
                    "customConfigSchemaReference": {
                      "type": "object"
                    },
                    "customConfig": {
                      "type": "object"
                    }
                  }
                }
              }
            }
            """;
    }
    
    /**
     * Default schema for pipeline step configuration.
     */
    private String getDefaultPipelineStepConfigSchema() {
        return """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "required": ["stepName", "stepType", "processorInfo"],
              "properties": {
                "stepName": {
                  "type": "string",
                  "minLength": 1
                },
                "stepType": {
                  "type": "string",
                  "enum": ["PIPELINE", "INITIAL_PIPELINE", "SINK"]
                },
                "description": {
                  "type": "string"
                },
                "customConfigSchemaId": {
                  "type": "string"
                },
                "customConfig": {
                  "type": "object",
                  "properties": {
                    "jsonConfig": {
                      "type": "object"
                    },
                    "configParams": {
                      "type": "object"
                    }
                  }
                },
                "kafkaInputs": {
                  "type": "array",
                  "items": {
                    "type": "object"
                  }
                },
                "outputs": {
                  "type": "object"
                },
                "processorInfo": {
                  "type": "object",
                  "properties": {
                    "grpcServiceName": {
                      "type": "string"
                    },
                    "internalProcessorBeanName": {
                      "type": "string"
                    }
                  }
                }
              }
            }
            """;
    }
}