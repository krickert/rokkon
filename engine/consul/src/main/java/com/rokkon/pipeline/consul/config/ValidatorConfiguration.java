package com.rokkon.pipeline.consul.config;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.validation.CompositeValidator;
import com.rokkon.pipeline.validation.validators.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI configuration for validators used in the Consul service.
 */
@ApplicationScoped
public class ValidatorConfiguration {
    
    @Produces
    @ApplicationScoped
    public CompositeValidator<PipelineConfig> pipelineConfigValidator() {
        CompositeValidator<PipelineConfig> composite = new CompositeValidator<>("PipelineConfigValidator");
        
        // Add all the validators we need for pipeline configuration
        composite.addValidator(new RequiredFieldsValidator())
                 .addValidator(new NamingConventionValidator())
                 .addValidator(new StepTypeValidator())
                 .addValidator(new IntraPipelineLoopValidator())
                 .addValidator(new StepReferenceValidator())
                 .addValidator(new ProcessorInfoValidator())
                 .addValidator(new TransportConfigValidator())
                 .addValidator(new RetryConfigValidator())
                 .addValidator(new OutputRoutingValidator())
                 .addValidator(new KafkaTopicNamingValidator());
        
        return composite;
    }
}