package com.rokkon.pipeline.validation;

import java.util.List;

/**
 * Factory for creating ValidationResult instances.
 */
public final class ValidationResultFactory {
    
    private ValidationResultFactory() {
        // Prevent instantiation
    }
    
    /**
     * Creates a valid result with no errors or warnings.
     */
    public static ValidationResult success() {
        return new DefaultValidationResult(true, List.of(), List.of());
    }

    /**
     * Creates a valid result with warnings.
     */
    public static ValidationResult successWithWarnings(List<String> warnings) {
        return new DefaultValidationResult(true, List.of(), warnings);
    }

    /**
     * Creates an invalid result with the given errors.
     */
    public static ValidationResult failure(List<String> errors) {
        return new DefaultValidationResult(false, errors, List.of());
    }

    /**
     * Creates an invalid result with a single error.
     */
    public static ValidationResult failure(String error) {
        return new DefaultValidationResult(false, List.of(error), List.of());
    }

    /**
     * Creates an invalid result with errors and warnings.
     */
    public static ValidationResult failure(List<String> errors, List<String> warnings) {
        return new DefaultValidationResult(false, errors, warnings);
    }
}