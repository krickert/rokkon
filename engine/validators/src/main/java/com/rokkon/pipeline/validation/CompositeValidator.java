package com.rokkon.pipeline.validation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


/**
 * A validator that combines multiple validators and runs them in priority order.
 * 
 * @param <T> The type of object being validated
 */
public class CompositeValidator<T extends ConfigValidatable> implements ConfigValidator<T> {

    private final List<ConfigValidator<T>> validators;
    private final String name;

    public CompositeValidator(String name) {
        this.name = name;
        this.validators = new ArrayList<>();
    }

    public CompositeValidator(String name, List<ConfigValidator<T>> validators) {
        this.name = name;
        this.validators = new ArrayList<>(validators);
        // Sort by priority
        this.validators.sort(Comparator.comparingInt(ConfigValidator::getPriority));
    }

    /**
     * Adds a validator to this composite.
     * 
     * @param validator The validator to add
     * @return This composite for fluent API
     */
    public CompositeValidator<T> addValidator(ConfigValidator<T> validator) {
        validators.add(validator);
        // Re-sort by priority
        validators.sort(Comparator.comparingInt(ConfigValidator::getPriority));
        return this;
    }

    @Override
    public ValidationResult validate(T object) {
        ValidationResult result = ValidationResult.empty();
        
        for (ConfigValidator<T> validator : validators) {
            try {
                ValidationResult validatorResult = validator.validate(object);
                result = result.combine(validatorResult);
            } catch (Exception e) {
                // Log the error and add it to the validation result
                String errorMessage = String.format(
                    "Validator '%s' threw an exception: %s",
                    validator.getValidatorName(),
                    e.getMessage()
                );
                result = result.combine(ValidationResultFactory.failure(errorMessage));
            }
        }
        
        return result;
    }

    @Override
    public String getValidatorName() {
        return name;
    }

    @Override
    public int getPriority() {
        return 0; // Composite validators typically run first
    }

    /**
     * Returns the list of validators in this composite.
     * 
     * @return The list of validators
     */
    public List<ConfigValidator<T>> getValidators() {
        return validators;
    }
}