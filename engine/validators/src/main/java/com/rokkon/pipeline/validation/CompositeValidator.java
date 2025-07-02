package com.rokkon.pipeline.validation;

import org.jboss.logging.Logger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


/**
 * A validator that combines multiple validators and runs them in priority order.
 * 
 * @param <T> The type of object being validated
 */
public class CompositeValidator<T extends ConfigValidatable> implements ConfigValidator<T> {
    
    private static final Logger LOG = Logger.getLogger(CompositeValidator.class);

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
        // Default to PRODUCTION mode for backward compatibility
        return validate(object, ValidationMode.PRODUCTION);
    }
    
    /**
     * Validates an object using the specified validation mode.
     * Only runs validators that support the given mode.
     * 
     * @param object The object to validate
     * @param mode The validation mode to use
     * @return The combined validation result
     */
    public ValidationResult validate(T object, ValidationMode mode) {
        LOG.debugf("CompositeValidator.validate called with %d validators in %s mode", validators.size(), mode);
        ValidationResult result = ValidationResult.empty();
        
        for (ConfigValidator<T> validator : validators) {
            // Check if this validator supports the current mode
            if (!validator.supportedModes().contains(mode)) {
                LOG.debugf("Skipping validator %s - does not support %s mode", validator.getValidatorName(), mode);
                continue;
            }
            
            try {
                LOG.debugf("Running validator: %s", validator.getValidatorName());
                ValidationResult validatorResult;
                
                // Check if validator is mode-aware
                if (validator instanceof ModeAwareValidator) {
                    validatorResult = ((ModeAwareValidator<T>) validator).validate(object, mode);
                } else {
                    validatorResult = validator.validate(object);
                }
                
                LOG.debugf("Validator %s returned: valid=%s, errors=%s, warnings=%s", 
                    validator.getValidatorName(), validatorResult.valid(), 
                    validatorResult.errors(), validatorResult.warnings());
                result = result.combine(validatorResult);
            } catch (Exception e) {
                // Log the error and add it to the validation result
                String errorMessage = String.format(
                    "Validator '%s' threw an exception: %s",
                    validator.getValidatorName(),
                    e.getMessage()
                );
                LOG.error("Validator exception", e);
                result = result.combine(ValidationResultFactory.failure(errorMessage));
            }
        }
        
        LOG.debugf("CompositeValidator final result: valid=%s, errors=%s, warnings=%s", 
            result.valid(), result.errors(), result.warnings());
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