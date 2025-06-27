package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.model.PipelineInstance;
import com.rokkon.pipeline.config.service.PipelineDefinitionService;
import com.rokkon.pipeline.config.service.PipelineInstanceService;
import com.rokkon.pipeline.consul.model.PipelineDefinitionSummary;
import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import com.rokkon.pipeline.validation.Composite;
import com.rokkon.pipeline.validation.PipelineConfigValidator;
import com.rokkon.pipeline.validation.DefaultValidationResult;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.UniHelper;
import io.vertx.ext.consul.ConsulClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of PipelineDefinitionService for managing global pipeline definitions in Consul.
 */
@ApplicationScoped
public class PipelineDefinitionServiceImpl implements PipelineDefinitionService {
    
    private static final Logger LOG = LoggerFactory.getLogger(PipelineDefinitionServiceImpl.class);
    private static final String PIPELINE_DEFINITIONS_PREFIX = "pipelines/definitions/";
    private static final String PIPELINE_METADATA_SUFFIX = "/metadata";
    
    @Inject
    ConsulConnectionManager connectionManager;
    
    @Inject
    ObjectMapper objectMapper;
    
    @Inject
    @Composite
    PipelineConfigValidator validator;
    
    @Inject
    PipelineInstanceService pipelineInstanceService;
    
    private ConsulClient getConsulClient() {
        return connectionManager.getClient().orElseThrow(() -> 
            new WebApplicationException("Consul not connected", Response.Status.SERVICE_UNAVAILABLE)
        );
    }
    
    @Override
    public Uni<List<PipelineDefinitionSummary>> listDefinitions() {
        return UniHelper.toUni(getConsulClient().getKeys(PIPELINE_DEFINITIONS_PREFIX))
            .flatMap(keys -> {
                
                if (keys == null || keys.isEmpty()) {
                    return Uni.createFrom().item(Collections.emptyList());
                }
                
                // Filter out metadata keys and get unique pipeline IDs
                Set<String> pipelineIds = keys.stream()
                    .filter(key -> !key.endsWith(PIPELINE_METADATA_SUFFIX))
                    .map(key -> key.substring(PIPELINE_DEFINITIONS_PREFIX.length()))
                    .filter(id -> !id.contains("/"))  // Skip nested keys
                    .collect(Collectors.toSet());
                
                // Create list of Unis for parallel fetching
                List<Uni<PipelineDefinitionSummary>> summaryUnis = pipelineIds.stream()
                    .map(pipelineId -> 
                        // Fetch definition, metadata, and instance count in parallel
                        Uni.combine().all().unis(
                            getDefinition(pipelineId),
                            getMetadata(pipelineId),
                            getActiveInstanceCount(pipelineId)
                        ).asTuple()
                        .map(tuple -> {
                            PipelineConfig definition = tuple.getItem1();
                            Map<String, String> metadata = tuple.getItem2();
                            Integer instanceCount = tuple.getItem3();
                            
                            if (definition != null) {
                                return new PipelineDefinitionSummary(
                                    pipelineId,
                                    definition.name(),
                                    getFirstStepDescription(definition),
                                    definition.pipelineSteps().size(),
                                    metadata.getOrDefault("createdAt", ""),
                                    metadata.getOrDefault("modifiedAt", ""),
                                    instanceCount
                                );
                            }
                            return null;
                        })
                        .onFailure().recoverWithItem(error -> {
                            LOG.warn("Failed to load pipeline definition summary for '{}'", pipelineId, error);
                            return null;
                        })
                    )
                    .toList();
                
                // Combine all Unis and filter out nulls
                return Uni.combine().all().unis(summaryUnis)
                    .with(list -> list.stream()
                        .filter(Objects::nonNull)
                        .map(obj -> (PipelineDefinitionSummary) obj)
                        .collect(Collectors.toList())
                    );
            });
    }
    
    private Uni<Map<String, String>> getMetadata(String pipelineId) {
        String key = PIPELINE_DEFINITIONS_PREFIX + pipelineId + PIPELINE_METADATA_SUFFIX;
        return UniHelper.toUni(getConsulClient().getValue(key))
            .map(keyValue -> {
                if (keyValue != null && keyValue.getValue() != null) {
                    try {
                        String json = keyValue.getValue();
                        return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                    } catch (Exception e) {
                        LOG.warn("Failed to parse metadata for pipeline definition '{}'", pipelineId, e);
                        return new HashMap<String, String>();
                    }
                }
                return new HashMap<String, String>();
            })
            .onFailure().recoverWithItem(error -> {
                LOG.warn("Failed to get metadata for pipeline definition '{}'", pipelineId, error);
                return new HashMap<String, String>();
            });
    }
    
    @Override
    public Uni<PipelineConfig> getDefinition(String pipelineId) {
        String key = PIPELINE_DEFINITIONS_PREFIX + pipelineId;
        return UniHelper.toUni(getConsulClient().getValue(key))
            .map(keyValue -> {
                if (keyValue != null && keyValue.getValue() != null) {
                    try {
                        String json = keyValue.getValue();
                        return objectMapper.readValue(json, PipelineConfig.class);
                    } catch (Exception e) {
                        LOG.error("Failed to parse pipeline definition '{}'", pipelineId, e);
                        return null;
                    }
                }
                return null;
            });
    }
    
    @Override
    public Uni<ValidationResult> createDefinition(String pipelineId, PipelineConfig definition) {
        // Check if already exists
        return definitionExists(pipelineId)
            .flatMap(exists -> {
                if (exists) {
                    return Uni.createFrom().item(DefaultValidationResult.failure("Pipeline definition '" + pipelineId + "' already exists"));
                }
                
                // Validate the pipeline configuration
                ValidationResult validationResult = validator.validate(definition);
                if (!validationResult.valid()) {
                    return Uni.createFrom().item(validationResult);
                }
                
                try {
                    // Store in Consul
                    String json = objectMapper.writeValueAsString(definition);
                    String key = PIPELINE_DEFINITIONS_PREFIX + pipelineId;
                    
                    return UniHelper.toUni(getConsulClient().putValue(key, json))
                        .flatMap(success -> {
                            if (!success) {
                                return Uni.createFrom().item(DefaultValidationResult.failure("Failed to store pipeline definition in Consul"));
                            }
                            
                            // Store metadata
                            Map<String, String> metadata = new HashMap<>();
                            metadata.put("createdAt", Instant.now().toString());
                            metadata.put("modifiedAt", Instant.now().toString());
                            metadata.put("createdBy", "system"); // TODO: Add user context
                            
                            try {
                                String metadataKey = PIPELINE_DEFINITIONS_PREFIX + pipelineId + PIPELINE_METADATA_SUFFIX;
                                String metadataJson = objectMapper.writeValueAsString(metadata);
                                
                                return UniHelper.toUni(getConsulClient().putValue(metadataKey, metadataJson))
                                    .map(metaSuccess -> {
                                        if (metaSuccess) {
                                            LOG.info("Created pipeline definition '{}' in Consul", pipelineId);
                                            return ValidationResult.success();
                                        } else {
                                            return DefaultValidationResult.failure("Failed to store metadata");
                                        }
                                    });
                            } catch (JsonProcessingException e) {
                                LOG.error("Failed to serialize metadata", e);
                                return Uni.createFrom().item(DefaultValidationResult.failure("Failed to serialize metadata: " + e.getMessage()));
                            }
                        });
                    
                } catch (JsonProcessingException e) {
                    LOG.error("Failed to serialize pipeline definition", e);
                    return Uni.createFrom().item(DefaultValidationResult.failure("Failed to serialize pipeline definition: " + e.getMessage()));
                }
            });
    }
    
    @Override
    public Uni<ValidationResult> updateDefinition(String pipelineId, PipelineConfig definition) {
        // Check if exists
        return definitionExists(pipelineId)
            .flatMap(exists -> {
                if (!exists) {
                    return Uni.createFrom().item(DefaultValidationResult.failure("Pipeline definition '" + pipelineId + "' not found"));
                }
                
                // Validate the pipeline configuration
                ValidationResult validationResult = validator.validate(definition);
                if (!validationResult.valid()) {
                    return Uni.createFrom().item(validationResult);
                }
                
                try {
                    // Update in Consul
                    String json = objectMapper.writeValueAsString(definition);
                    String key = PIPELINE_DEFINITIONS_PREFIX + pipelineId;
                    
                    return UniHelper.toUni(getConsulClient().putValue(key, json))
                        .flatMap(success -> {
                            if (!success) {
                                return Uni.createFrom().item(DefaultValidationResult.failure("Failed to update pipeline definition in Consul"));
                            }
                            
                            // Update metadata
                            Map<String, String> metadata = getMetadata(pipelineId).await().indefinitely();
                            metadata.put("modifiedAt", Instant.now().toString());
                            metadata.put("modifiedBy", "system"); // TODO: Add user context
                            
                            try {
                                String metadataKey = PIPELINE_DEFINITIONS_PREFIX + pipelineId + PIPELINE_METADATA_SUFFIX;
                                String metadataJson = objectMapper.writeValueAsString(metadata);
                                
                                return UniHelper.toUni(getConsulClient().putValue(metadataKey, metadataJson))
                                    .map(metaSuccess -> {
                                        if (metaSuccess) {
                                            LOG.info("Updated pipeline definition '{}' in Consul", pipelineId);
                                            return ValidationResult.success();
                                        } else {
                                            return DefaultValidationResult.failure("Failed to update metadata");
                                        }
                                    });
                            } catch (JsonProcessingException e) {
                                LOG.error("Failed to serialize metadata", e);
                                return Uni.createFrom().item(DefaultValidationResult.failure("Failed to serialize metadata: " + e.getMessage()));
                            }
                        });
                    
                } catch (JsonProcessingException e) {
                    LOG.error("Failed to serialize pipeline definition", e);
                    return Uni.createFrom().item(DefaultValidationResult.failure("Failed to serialize pipeline definition: " + e.getMessage()));
                }
            });
    }
    
    @Override
    public Uni<ValidationResult> deleteDefinition(String pipelineId) {
        // Check if exists
        return definitionExists(pipelineId)
            .flatMap(exists -> {
                if (!exists) {
                    return Uni.createFrom().item(DefaultValidationResult.failure("Pipeline definition '" + pipelineId + "' not found"));
                }
                
                // Check for active instances
                return getActiveInstanceCount(pipelineId)
                    .flatMap(instanceCount -> {
                        if (instanceCount > 0) {
                            return Uni.createFrom().item(DefaultValidationResult.failure("Cannot delete pipeline definition with " + instanceCount + " active instances"));
                        }
                        
                        // Delete from Consul
                        String key = PIPELINE_DEFINITIONS_PREFIX + pipelineId;
                        String metadataKey = PIPELINE_DEFINITIONS_PREFIX + pipelineId + PIPELINE_METADATA_SUFFIX;
                        
                        return UniHelper.toUni(getConsulClient().deleteValue(key))
                            .flatMap(success -> UniHelper.toUni(getConsulClient().deleteValue(metadataKey)))
                            .map(metaSuccess -> {
                                LOG.info("Deleted pipeline definition '{}' from Consul", pipelineId);
                                return ValidationResult.success();
                            });
                    });
            });
    }
    
    @Override
    public Uni<Boolean> definitionExists(String pipelineId) {
        String key = PIPELINE_DEFINITIONS_PREFIX + pipelineId;
        return UniHelper.toUni(getConsulClient().getValue(key))
            .map(keyValue -> keyValue != null && keyValue.getValue() != null);
    }
    
    @Override
    public Uni<Integer> getActiveInstanceCount(String pipelineId) {
        return pipelineInstanceService.listInstancesByDefinition(pipelineId)
            .map(instances -> (int) instances.stream()
                .filter(instance -> instance.status() == PipelineInstance.PipelineInstanceStatus.RUNNING 
                    || instance.status() == PipelineInstance.PipelineInstanceStatus.STARTING)
                .count()
            )
            .onFailure().recoverWithItem(error -> {
                LOG.warn("Failed to get active instance count for pipeline '{}'", pipelineId, error);
                return 0;
            });
    }
    
    
    private String getFirstStepDescription(PipelineConfig config) {
        if (config.pipelineSteps() != null && !config.pipelineSteps().isEmpty()) {
            return config.pipelineSteps().values().stream()
                .findFirst()
                .map(step -> step.description() != null ? step.description() : "No description")
                .orElse("No steps defined");
        }
        return "No steps defined";
    }
}