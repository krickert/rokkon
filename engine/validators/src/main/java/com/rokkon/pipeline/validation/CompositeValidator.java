package com.rokkon.pipeline.validation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A validator that combines multiple validators and runs them in priority order.
 * 
 * @param <T> The type of object being validated
 */
public class CompositeValidator<T> implements Validator<T> {
    
    private final List<Validator<T>> validators;
    private final String name;
    
    public CompositeValidator(String name) {
        this.name = name;
        this.validators = new ArrayList<>();
    }
    
    public CompositeValidator(String name, List<Validator<T>> validators) {
        this.name = name;
        this.validators = new ArrayList<>(validators);
        // Sort by priority
        this.validators.sort(Comparator.comparingInt(Validator::getPriority));
    }
    
    /**
     * Adds a validator to this composite.
     * 
     * @param validator The validator to add
     * @return This composite for fluent API
     */
    public CompositeValidator<T> addValidator(Validator<T> validator) {
        validators.add(validator);
        // Re-sort by priority
        validators.sort(Comparator.comparingInt(Validator::getPriority));
        return this;
    }
    
    @Override
    public DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult validate(T object) {
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult.success();
        
        for (Validator<T> validator : validators) {
            DefaultValidationResult validatorResult = validator.validate(object);
            result = result.combine(validatorResult);
            
            // Option to stop on first error (could be configurable)
            // if (validatorResult.hasErrors() && stopOnFirstError) break;
        }
        
        return result;
    }
    
    @Override
    public String getValidatorName() {
        return name;
    }
    
    /**
     * Returns the list of validators in this composite.
     */
    public List<Validator<T>> getValidators() {
        return new ArrayList<>(validators);
    }
}