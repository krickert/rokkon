package com.rokkon.pipeline.validation;

/**
 * Base interface for all validators in the Rokkon pipeline system.
 * 
 * @param <T> The type of object being validated
 */
public interface Validator<T> {
    
    /**
     * Validates the given object and returns a validation result.
     * 
     * @param object The object to validate
     * @return ValidationResult containing the validation outcome
     */
    ValidationResult validate(T object);
    
    /**
     * Returns the name of this validator for error reporting.
     * 
     * @return A human-readable name for this validator
     */
    default String getValidatorName() {
        return this.getClass().getSimpleName();
    }
    
    /**
     * Returns the priority of this validator (lower numbers = higher priority).
     * Validators with higher priority are executed first.
     * 
     * @return Priority level (default is 100)
     */
    default int getPriority() {
        return 100;
    }
}