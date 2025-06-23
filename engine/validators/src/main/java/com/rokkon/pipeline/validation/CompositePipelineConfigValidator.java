package com.rokkon.pipeline.validation;

import com.rokkon.pipeline.config.model.PipelineConfig;

import java.util.List;

/**
 * Composite implementation of PipelineConfigValidator that delegates to a list of validators.
 * This class is not a CDI bean itself - it's created via PipelineValidatorProducer.
 */
public class CompositePipelineConfigValidator implements PipelineConfigValidator {

    private final CompositeValidator<PipelineConfig> composite;

    public CompositePipelineConfigValidator() {
        // Default constructor for CDI
        this.composite = new CompositeValidator<>("Pipeline Configuration Validator", List.of());
    }

    public CompositePipelineConfigValidator(List<Validator<PipelineConfig>> validators) {
        this.composite = new CompositeValidator<>("Pipeline Configuration Validator", validators);
    }

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

    public void setValidators(List<Validator<PipelineConfig>> validators) {
        composite.getValidators().clear();
        composite.getValidators().addAll(validators);
    }
}
