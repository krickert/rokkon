package com.rokkon.test.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.test.consul.ConsulKVClient;
import com.rokkon.test.model.PipelineConfig;
import com.rokkon.test.validation.PipelineValidator;
import com.rokkon.test.validation.ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.*;

/**
 * Service for managing pipeline configurations in Consul
 */
@ApplicationScoped
public class PipelineConfigService {
    
    private static final Logger LOG = Logger.getLogger(PipelineConfigService.class);
    private static final String PIPELINE_PREFIX = "pipelines/";
    
    @Inject
    @RestClient
    ConsulKVClient consulClient;
    
    @Inject
    PipelineValidator validator;
    
    @Inject
    ObjectMapper objectMapper;
    
    public ValidationResult createPipeline(String pipelineId, PipelineConfig config) {
        LOG.infof("Creating pipeline: %s", pipelineId);
        
        // Validate
        ValidationResult validation = validator.validate(config);
        if (!validation.isValid()) {
            return validation;
        }
        
        // Check if already exists
        String key = PIPELINE_PREFIX + pipelineId;
        try {
            Response response = consulClient.getValue(key, true);
            if (response.getStatus() == 200) {
                return ValidationResult.failure("Pipeline already exists: " + pipelineId);
            }
        } catch (Exception e) {
            LOG.debugf("Pipeline doesn't exist, proceeding with creation");
        }
        
        // Save to Consul
        try {
            String json = objectMapper.writeValueAsString(config);
            Response response = consulClient.putValue(key, json);
            if (response.getStatus() == 200) {
                LOG.infof("Successfully created pipeline: %s", pipelineId);
                return ValidationResult.success();
            } else {
                return ValidationResult.failure("Failed to save to Consul: " + response.getStatus());
            }
        } catch (Exception e) {
            LOG.error("Error creating pipeline", e);
            return ValidationResult.failure("Error: " + e.getMessage());
        }
    }
    
    public Optional<PipelineConfig> getPipeline(String pipelineId) {
        LOG.infof("Getting pipeline: %s", pipelineId);
        String key = PIPELINE_PREFIX + pipelineId;
        
        try {
            Response response = consulClient.getValue(key, true);
            if (response.getStatus() == 200) {
                String json = response.readEntity(String.class);
                PipelineConfig config = objectMapper.readValue(json, PipelineConfig.class);
                return Optional.of(config);
            }
        } catch (Exception e) {
            LOG.error("Error getting pipeline", e);
        }
        
        return Optional.empty();
    }
    
    public ValidationResult updatePipeline(String pipelineId, PipelineConfig config) {
        LOG.infof("Updating pipeline: %s", pipelineId);
        
        // Validate
        ValidationResult validation = validator.validate(config);
        if (!validation.isValid()) {
            return validation;
        }
        
        // Check if exists
        if (getPipeline(pipelineId).isEmpty()) {
            return ValidationResult.failure("Pipeline not found: " + pipelineId);
        }
        
        // Update in Consul
        String key = PIPELINE_PREFIX + pipelineId;
        try {
            String json = objectMapper.writeValueAsString(config);
            Response response = consulClient.putValue(key, json);
            if (response.getStatus() == 200) {
                LOG.infof("Successfully updated pipeline: %s", pipelineId);
                return ValidationResult.success();
            } else {
                return ValidationResult.failure("Failed to update in Consul: " + response.getStatus());
            }
        } catch (Exception e) {
            LOG.error("Error updating pipeline", e);
            return ValidationResult.failure("Error: " + e.getMessage());
        }
    }
    
    public ValidationResult deletePipeline(String pipelineId) {
        LOG.infof("Deleting pipeline: %s", pipelineId);
        String key = PIPELINE_PREFIX + pipelineId;
        
        try {
            Response response = consulClient.deleteValue(key);
            if (response.getStatus() == 200) {
                LOG.infof("Successfully deleted pipeline: %s", pipelineId);
                return ValidationResult.success();
            } else {
                return ValidationResult.failure("Failed to delete from Consul: " + response.getStatus());
            }
        } catch (Exception e) {
            LOG.error("Error deleting pipeline", e);
            return ValidationResult.failure("Error: " + e.getMessage());
        }
    }
    
    public List<String> listPipelines() {
        LOG.info("Listing all pipelines");
        
        try {
            Response response = consulClient.listKeys(PIPELINE_PREFIX, true);
            if (response.getStatus() == 200) {
                List<String> keys = response.readEntity(List.class);
                List<String> pipelineIds = new ArrayList<>();
                for (String key : keys) {
                    if (key.startsWith(PIPELINE_PREFIX)) {
                        pipelineIds.add(key.substring(PIPELINE_PREFIX.length()));
                    }
                }
                return pipelineIds;
            }
        } catch (Exception e) {
            LOG.error("Error listing pipelines", e);
        }
        
        return Collections.emptyList();
    }
}