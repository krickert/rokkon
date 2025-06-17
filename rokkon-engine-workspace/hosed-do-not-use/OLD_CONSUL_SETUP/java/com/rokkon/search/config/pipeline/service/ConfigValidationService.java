package com.rokkon.search.config.pipeline.service;

import com.rokkon.search.config.pipeline.model.PipelineClusterConfig;
import com.rokkon.search.config.pipeline.model.PipelineConfig;
import com.rokkon.search.config.pipeline.service.validation.ClusterStructuralValidationRule;
import com.rokkon.search.config.pipeline.service.validation.StructuralValidationRule;
import com.rokkon.search.config.pipeline.service.validation.ValidationResult;
import com.rokkon.search.config.pipeline.service.validation.rules.*;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Service for orchestrating comprehensive validation of pipeline configurations.
 * Migrated from the old PipelineStructuralValidator to Quarkus patterns.
 */
@ApplicationScoped
@RegisterForReflection
public class ConfigValidationService {
    
    private static final Logger LOG = LoggerFactory.getLogger(ConfigValidationService.class);
    
    // Default validation rules for single pipeline validation
    private final List<StructuralValidationRule> defaultPipelineRules;
    
    // Default validation rules for cluster-wide validation
    private final List<ClusterStructuralValidationRule> defaultClusterRules;
    
    @Inject
    public ConfigValidationService() {
        this.defaultPipelineRules = createDefaultPipelineRules();
        this.defaultClusterRules = createDefaultClusterRules();
        
        LOG.info("ConfigValidationService initialized with {} pipeline rules and {} cluster rules",
                defaultPipelineRules.size(), defaultClusterRules.size());
    }
    
    /**
     * Validates a single pipeline configuration using all default rules.
     */
    public CompletionStage<ValidationResult> validatePipelineStructure(String pipelineId, PipelineConfig config) {
        return validatePipelineStructure(pipelineId, config, defaultPipelineRules);
    }
    
    /**
     * Validates a single pipeline configuration using specified rules.
     */
    public CompletionStage<ValidationResult> validatePipelineStructure(String pipelineId, PipelineConfig config,
                                                                      List<StructuralValidationRule> rules) {
        return CompletableFuture.supplyAsync(() -> {
            if (config == null) {
                return ValidationResult.failure("Pipeline configuration is null");
            }
            
            List<String> allErrors = new ArrayList<>();
            List<String> allWarnings = new ArrayList<>();
            
            // Sort rules by priority (lower number = higher priority)
            List<StructuralValidationRule> sortedRules = new ArrayList<>(rules);
            sortedRules.sort(Comparator.comparingInt(StructuralValidationRule::getPriority));
            
            for (StructuralValidationRule rule : sortedRules) {
                try {
                    List<String> ruleErrors = rule.validate(pipelineId, config);
                    if (ruleErrors != null && !ruleErrors.isEmpty()) {
                        // Prefix errors with rule name for clarity
                        String ruleName = rule.getRuleName();
                        for (String error : ruleErrors) {
                            allErrors.add("[" + ruleName + "] " + error);
                        }
                    }
                } catch (Exception e) {
                    LOG.error("Error running validation rule {}: {}", rule.getRuleName(), e.getMessage(), e);
                    allErrors.add("[" + rule.getRuleName() + "] Validation error: " + e.getMessage());
                }
            }
            
            return allErrors.isEmpty() 
                ? (allWarnings.isEmpty() ? ValidationResult.success() : ValidationResult.successWithWarnings(allWarnings))
                : ValidationResult.failure(allErrors, allWarnings);
        });
    }
    
    /**
     * Validates a cluster configuration using all default rules.
     */
    public CompletionStage<ValidationResult> validateClusterStructure(PipelineClusterConfig clusterConfig) {
        return validateClusterStructure(clusterConfig, defaultPipelineRules, defaultClusterRules);
    }
    
    /**
     * Validates a cluster configuration using specified rules.
     */
    public CompletionStage<ValidationResult> validateClusterStructure(PipelineClusterConfig clusterConfig,
                                                                     List<StructuralValidationRule> pipelineRules,
                                                                     List<ClusterStructuralValidationRule> clusterRules) {
        return CompletableFuture.supplyAsync(() -> {
            if (clusterConfig == null) {
                return ValidationResult.failure("Cluster configuration is null");
            }
            
            List<String> allErrors = new ArrayList<>();
            List<String> allWarnings = new ArrayList<>();
            
            // Validate cluster name
            if (clusterConfig.clusterName() == null || clusterConfig.clusterName().isBlank()) {
                allErrors.add("Cluster name is required");
            }
            
            // Validate each pipeline
            if (clusterConfig.pipelineGraphConfig() != null && 
                clusterConfig.pipelineGraphConfig().pipelines() != null) {
                
                for (var entry : clusterConfig.pipelineGraphConfig().pipelines().entrySet()) {
                    String pipelineId = entry.getKey();
                    PipelineConfig pipeline = entry.getValue();
                    
                    try {
                        ValidationResult pipelineResult = validatePipelineStructure(pipelineId, pipeline, pipelineRules)
                                .toCompletableFuture().join();
                        
                        if (!pipelineResult.valid()) {
                            for (String error : pipelineResult.errors()) {
                                allErrors.add("Pipeline '" + pipelineId + "': " + error);
                            }
                        }
                        
                        // Collect warnings too
                        for (String warning : pipelineResult.warnings()) {
                            allWarnings.add("Pipeline '" + pipelineId + "': " + warning);
                        }
                    } catch (Exception e) {
                        LOG.error("Error validating pipeline {}: {}", pipelineId, e.getMessage(), e);
                        allErrors.add("Pipeline '" + pipelineId + "': Validation error: " + e.getMessage());
                    }
                }
            }
            
            // Apply cluster-wide validation rules
            List<ClusterStructuralValidationRule> sortedClusterRules = new ArrayList<>(clusterRules);
            sortedClusterRules.sort(Comparator.comparingInt(ClusterStructuralValidationRule::getPriority));
            
            for (ClusterStructuralValidationRule rule : sortedClusterRules) {
                try {
                    List<String> ruleErrors = rule.validate(clusterConfig);
                    if (ruleErrors != null && !ruleErrors.isEmpty()) {
                        String ruleName = rule.getRuleName();
                        for (String error : ruleErrors) {
                            allErrors.add("[" + ruleName + "] " + error);
                        }
                    }
                } catch (Exception e) {
                    LOG.error("Error running cluster validation rule {}: {}", rule.getRuleName(), e.getMessage(), e);
                    allErrors.add("[" + rule.getRuleName() + "] Validation error: " + e.getMessage());
                }
            }
            
            return allErrors.isEmpty() 
                ? (allWarnings.isEmpty() ? ValidationResult.success() : ValidationResult.successWithWarnings(allWarnings))
                : ValidationResult.failure(allErrors, allWarnings);
        });
    }
    
    /**
     * Validates that a service name follows naming conventions.
     */
    public ValidationResult validateServiceName(String serviceName) {
        List<String> errors = new ArrayList<>();
        
        if (serviceName == null || serviceName.isBlank()) {
            errors.add("Service name is required");
        } else if (!serviceName.matches("^[a-z][a-z0-9-]*$")) {
            errors.add("Service name must start with lowercase letter and contain only lowercase letters, numbers, and hyphens");
        } else if (serviceName.length() > 63) {
            errors.add("Service name cannot exceed 63 characters");
        }
        
        return errors.isEmpty() 
            ? ValidationResult.success() 
            : ValidationResult.failure(errors);
    }
    
    /**
     * Validates that a Kafka topic name follows conventions.
     */
    public ValidationResult validateKafkaTopic(String topicName) {
        List<String> errors = new ArrayList<>();
        
        if (topicName == null || topicName.isBlank()) {
            errors.add("Topic name is required");
        } else if (!topicName.matches("^[a-zA-Z0-9._-]+$")) {
            errors.add("Topic name can only contain letters, numbers, dots, underscores, and hyphens");
        } else if (topicName.length() > 249) {
            errors.add("Topic name cannot exceed 249 characters");
        } else if (topicName.equals(".") || topicName.equals("..")) {
            errors.add("Topic name cannot be '.' or '..'");
        }
        
        return errors.isEmpty() 
            ? ValidationResult.success() 
            : ValidationResult.failure(errors);
    }
    
    private List<StructuralValidationRule> createDefaultPipelineRules() {
        return List.of(
            new NamingConventionValidator(),
            new RequiredFieldsValidator(),
            new StepTypeValidator(),
            new StepReferenceValidator(),
            new KafkaTopicNamingValidator(),
            new IntraPipelineLoopValidator()
        );
    }
    
    private List<ClusterStructuralValidationRule> createDefaultClusterRules() {
        return List.of(
            new InterPipelineLoopValidator()
        );
    }
}