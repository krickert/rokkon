package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.validation.PipelineConfigValidator;
import com.rokkon.pipeline.validation.PipelineConfigValidatable;
import com.rokkon.pipeline.validation.ValidationMode;
import com.rokkon.pipeline.validation.ValidationResult;
import com.rokkon.pipeline.validation.ValidationResultFactory;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates processor information configuration for pipeline steps.
 * Ensures each step has valid processor configuration (either gRPC or internal).
 */
@ApplicationScoped
public class ProcessorInfoValidator implements PipelineConfigValidator {
    
    @Override
    public ValidationResult validate(PipelineConfigValidatable validatable) {
        PipelineConfig config = (PipelineConfig) validatable;
        if (config == null || config.pipelineSteps() == null || config.pipelineSteps().isEmpty()) {
            return ValidationResultFactory.success();
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        for (var entry : config.pipelineSteps().entrySet()) {
            String stepId = entry.getKey();
            PipelineStepConfig step = entry.getValue();
            
            if (step == null) {
                continue; // Let RequiredFieldsValidator handle this
            }
            
            String stepPrefix = "Step '" + stepId + "': ";
            
            // ProcessorInfo is required and validated in the model constructor
            // Here we can add additional business logic validation
            if (step.processorInfo() != null) {
                PipelineStepConfig.ProcessorInfo processorInfo = step.processorInfo();
                
                // Check gRPC service name format
                if (processorInfo.grpcServiceName() != null && !processorInfo.grpcServiceName().isBlank()) {
                    String serviceName = processorInfo.grpcServiceName();
                    
                    // Validate gRPC service name format
                    if (serviceName.length() < 3) {
                        errors.add(stepPrefix + "gRPC service name '" + serviceName + "' is too short (minimum 3 characters)");
                    }
                    
                    if (serviceName.length() > 100) {
                        warnings.add(stepPrefix + "gRPC service name '" + serviceName + "' is very long (over 100 characters)");
                    }
                    
                    // Check for common naming patterns (allow colons for host:port)
                    if (!serviceName.matches("^[a-zA-Z][a-zA-Z0-9-._:]*$")) {
                        errors.add(stepPrefix + "gRPC service name '" + serviceName + 
                                 "' should start with a letter and contain only alphanumeric characters, hyphens, dots, underscores, and colons");
                    }
                    
                    // Warn about localhost references in production
                    if (serviceName.contains("localhost") || serviceName.contains("127.0.0.1")) {
                        warnings.add(stepPrefix + "gRPC service name contains localhost reference - ensure this is intentional");
                    }
                }
                
                // Check internal processor bean name format
                if (processorInfo.internalProcessorBeanName() != null && !processorInfo.internalProcessorBeanName().isBlank()) {
                    String beanName = processorInfo.internalProcessorBeanName();
                    
                    // Validate bean name format (Java identifier conventions)
                    if (!beanName.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*$")) {
                        errors.add(stepPrefix + "Internal processor bean name '" + beanName + 
                                 "' must be a valid Java identifier");
                    }
                    
                    if (beanName.length() > 50) {
                        warnings.add(stepPrefix + "Internal processor bean name '" + beanName + 
                                   "' is very long (over 50 characters)");
                    }
                    
                    // Warn about common bean name issues
                    if (beanName.equals("processor") || beanName.equals("service") || beanName.equals("bean")) {
                        warnings.add(stepPrefix + "Internal processor bean name '" + beanName + 
                                   "' is too generic - consider a more descriptive name");
                    }
                }
            }
        }
        
        return errors.isEmpty() ? 
            (warnings.isEmpty() ? ValidationResultFactory.success() : ValidationResultFactory.successWithWarnings(warnings)) : 
            ValidationResultFactory.failure(errors, warnings);
    }
    
    @Override
    public int getPriority() {
        return 250; // Run after naming validation but before reference validation
    }
    
    @Override
    public String getValidatorName() {
        return "ProcessorInfoValidator";
    }
    
    @Override
    public Set<ValidationMode> supportedModes() {
        // Only run in PRODUCTION mode - design and testing modes may have incomplete processor info
        return Set.of(ValidationMode.PRODUCTION);
    }
}