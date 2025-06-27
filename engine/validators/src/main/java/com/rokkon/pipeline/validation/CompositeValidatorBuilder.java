package com.rokkon.pipeline.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating CompositeValidator instances.
 * This provides a fluent API for constructing validators with specific configurations.
 * 
 * @param <T> The type of object being validated
 */
public class CompositeValidatorBuilder<T extends ConfigValidatable> {
    
    private String name = "CompositeValidator";
    private final List<ConfigValidator<T>> validators = new ArrayList<>();
    
    /**
     * Creates a new builder instance.
     */
    public static <T extends ConfigValidatable> CompositeValidatorBuilder<T> create() {
        return new CompositeValidatorBuilder<>();
    }
    
    /**
     * Sets the name of the composite validator.
     */
    public CompositeValidatorBuilder<T> withName(String name) {
        this.name = name;
        return this;
    }
    
    /**
     * Adds a validator to the composite.
     */
    public CompositeValidatorBuilder<T> addValidator(ConfigValidator<T> validator) {
        this.validators.add(validator);
        return this;
    }
    
    /**
     * Adds multiple validators to the composite.
     */
    public CompositeValidatorBuilder<T> addValidators(ConfigValidator<T>... validators) {
        for (ConfigValidator<T> validator : validators) {
            this.validators.add(validator);
        }
        return this;
    }
    
    /**
     * Adds a list of validators to the composite.
     */
    public CompositeValidatorBuilder<T> addValidators(List<ConfigValidator<T>> validators) {
        this.validators.addAll(validators);
        return this;
    }
    
    /**
     * Creates an empty validator that always returns success.
     * Useful for testing when you don't want validation to interfere.
     */
    public CompositeValidatorBuilder<T> withEmptyValidation() {
        this.validators.clear();
        this.validators.add(new ConfigValidator<T>() {
            @Override
            public ValidationResult validate(T config) {
                return ValidationResult.empty();
            }
            
            @Override
            public String getValidatorName() {
                return "EmptyValidator";
            }
        });
        return this;
    }
    
    /**
     * Creates a validator that always fails with the given error.
     * Useful for testing error handling.
     */
    public CompositeValidatorBuilder<T> withFailingValidation(String errorMessage) {
        this.validators.clear();
        this.validators.add(new ConfigValidator<T>() {
            @Override
            public ValidationResult validate(T config) {
                return ValidationResultFactory.failure(errorMessage);
            }
            
            @Override
            public String getValidatorName() {
                return "FailingValidator";
            }
        });
        return this;
    }
    
    /**
     * Builds the CompositeValidator with the configured validators.
     */
    public CompositeValidator<T> build() {
        return new CompositeValidator<>(name, validators);
    }
}