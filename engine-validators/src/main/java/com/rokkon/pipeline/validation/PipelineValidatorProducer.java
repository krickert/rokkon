package com.rokkon.pipeline.validation;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.validation.validators.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Producer for the composite PipelineConfigValidator that aggregates all individual validators.
 */
@ApplicationScoped
public class PipelineValidatorProducer {
    
    @Inject RequiredFieldsValidator requiredFieldsValidator;
    @Inject NamingConventionValidator namingConventionValidator;
    @Inject StepReferenceValidator stepReferenceValidator;
    @Inject ProcessorInfoValidator processorInfoValidator;
    @Inject RetryConfigValidator retryConfigValidator;
    @Inject TransportConfigValidator transportConfigValidator;
    @Inject OutputRoutingValidator outputRoutingValidator;
    @Inject KafkaTopicNamingValidator kafkaTopicNamingValidator;
    @Inject IntraPipelineLoopValidator intraPipelineLoopValidator;
    @Inject StepTypeValidator stepTypeValidator;
    
    @Produces
    @Default
    @ApplicationScoped
    public PipelineConfigValidator producePipelineConfigValidator() {
        List<Validator<PipelineConfig>> validatorList = new ArrayList<>();
        
        // Add all validators explicitly to avoid circular dependency issues
        validatorList.add(requiredFieldsValidator);
        validatorList.add(namingConventionValidator);
        validatorList.add(stepReferenceValidator);
        validatorList.add(processorInfoValidator);
        validatorList.add(retryConfigValidator);
        validatorList.add(transportConfigValidator);
        validatorList.add(outputRoutingValidator);
        validatorList.add(kafkaTopicNamingValidator);
        validatorList.add(intraPipelineLoopValidator);
        validatorList.add(stepTypeValidator);
        
        // Create a composite validator that also implements PipelineConfigValidator
        CompositeValidator<PipelineConfig> composite = new CompositeValidator<>("Pipeline Configuration Validator", validatorList);
        
        // Return a wrapper that implements PipelineConfigValidator
        return new PipelineConfigValidator() {
            @Override
            public ValidationResult validate(PipelineConfig config) {
                return composite.validate(config);
            }
            
            @Override
            public String getValidatorName() {
                return composite.getValidatorName();
            }
            
            @Override
            public int getPriority() {
                return 0;
            }
        };
    }
}