package com.rokkon.test.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Simplified Pipeline configuration for CRUD testing
 */
public class PipelineConfig {
    
    @JsonProperty("pipeline_name")
    private String pipelineName;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("pipeline_steps")
    private Map<String, PipelineStep> pipelineSteps;
    
    @JsonProperty("enabled")
    private boolean enabled = true;
    
    // Constructors
    public PipelineConfig() {}
    
    public PipelineConfig(String pipelineName, String description) {
        this.pipelineName = pipelineName;
        this.description = description;
    }
    
    // Getters and Setters
    public String getPipelineName() {
        return pipelineName;
    }
    
    public void setPipelineName(String pipelineName) {
        this.pipelineName = pipelineName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Map<String, PipelineStep> getPipelineSteps() {
        return pipelineSteps;
    }
    
    public void setPipelineSteps(Map<String, PipelineStep> pipelineSteps) {
        this.pipelineSteps = pipelineSteps;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Simplified pipeline step
     */
    public static class PipelineStep {
        @JsonProperty("step_name")
        private String stepName;
        
        @JsonProperty("step_type")
        private String stepType;
        
        @JsonProperty("module_name")
        private String moduleName;
        
        // Constructors
        public PipelineStep() {}
        
        public PipelineStep(String stepName, String stepType, String moduleName) {
            this.stepName = stepName;
            this.stepType = stepType;
            this.moduleName = moduleName;
        }
        
        // Getters and Setters
        public String getStepName() {
            return stepName;
        }
        
        public void setStepName(String stepName) {
            this.stepName = stepName;
        }
        
        public String getStepType() {
            return stepType;
        }
        
        public void setStepType(String stepType) {
            this.stepType = stepType;
        }
        
        public String getModuleName() {
            return moduleName;
        }
        
        public void setModuleName(String moduleName) {
            this.moduleName = moduleName;
        }
    }
}