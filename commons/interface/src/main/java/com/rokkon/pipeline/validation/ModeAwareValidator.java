package com.rokkon.pipeline.validation;

/**
 * Extension of ConfigValidator that supports mode-aware validation.
 * Validators implementing this interface can adjust their validation
 * rules based on the ValidationMode.
 * 
 * @param <T> The type of configuration object being validated
 */
public interface ModeAwareValidator<T extends ConfigValidatable> extends ConfigValidator<T> {
    
    /**
     * Validates the given configuration object using the specified validation mode.
     * 
     * @param config The configuration object to validate
     * @param mode The validation mode to apply
     * @return ValidationResult containing the validation outcome
     */
    ValidationResult validate(T config, ValidationMode mode);
    
    /**
     * Default implementation delegates to mode-aware validate with PRODUCTION mode.
     */
    @Override
    default ValidationResult validate(T config) {
        return validate(config, ValidationMode.PRODUCTION);
    }
}