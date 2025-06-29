package com.rokkon.pipeline.validation;

/**
 * Base interface for all configuration validators in the Rokkon pipeline system.
 * 
 * @param <T> The type of configuration object being validated, must implement ConfigValidatable
 */
public interface ConfigValidator<T extends ConfigValidatable> {

    /**
     * Validates the given configuration object and returns a validation result.
     * 
     * @param config The configuration object to validate
     * @return ValidationResult containing the validation outcome
     */
    ValidationResult validate(T config);

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