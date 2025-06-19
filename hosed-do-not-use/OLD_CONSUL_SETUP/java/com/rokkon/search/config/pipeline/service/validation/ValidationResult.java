package com.rokkon.search.config.pipeline.service.validation;

import java.util.List;

/**
 * Result of pipeline configuration validation.
 * Based on the old StructuralValidationResult but adapted for the new system.
 */
public record ValidationResult(
    boolean valid,
    List<String> errors,
    List<String> warnings
) {
    /**
     * Creates a valid result with no errors or warnings.
     */
    public static ValidationResult success() {
        return new ValidationResult(true, List.of(), List.of());
    }
    
    /**
     * Creates a valid result with warnings.
     */
    public static ValidationResult successWithWarnings(List<String> warnings) {
        return new ValidationResult(true, List.of(), warnings != null ? warnings : List.of());
    }
    
    /**
     * Creates an invalid result with the given errors.
     */
    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, errors != null ? errors : List.of(), List.of());
    }
    
    /**
     * Creates an invalid result with a single error.
     */
    public static ValidationResult failure(String error) {
        return new ValidationResult(false, List.of(error), List.of());
    }
    
    /**
     * Creates an invalid result with errors and warnings.
     */
    public static ValidationResult failure(List<String> errors, List<String> warnings) {
        return new ValidationResult(false, 
            errors != null ? errors : List.of(), 
            warnings != null ? warnings : List.of());
    }
    
    /**
     * Combines this result with another, taking the most restrictive validity.
     */
    public ValidationResult combine(ValidationResult other) {
        if (other == null) return this;
        
        List<String> combinedErrors = new java.util.ArrayList<>(this.errors);
        combinedErrors.addAll(other.errors);
        
        List<String> combinedWarnings = new java.util.ArrayList<>(this.warnings);
        combinedWarnings.addAll(other.warnings);
        
        return new ValidationResult(
            this.valid && other.valid,
            combinedErrors,
            combinedWarnings
        );
    }
    
    /**
     * Returns true if there are any issues (errors or warnings).
     */
    public boolean hasIssues() {
        return !errors.isEmpty() || !warnings.isEmpty();
    }
}