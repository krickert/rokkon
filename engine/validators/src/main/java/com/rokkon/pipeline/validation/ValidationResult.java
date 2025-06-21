package com.rokkon.pipeline.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of pipeline configuration validation.
 * Contains validation status, errors, and warnings.
 */
public record ValidationResult(
    boolean valid,
    List<String> errors,
    List<String> warnings
) {
    
    /**
     * Ensures immutability of lists
     */
    public ValidationResult {
        errors = errors != null ? List.copyOf(errors) : List.of();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }
    
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
        return new ValidationResult(true, List.of(), warnings);
    }
    
    /**
     * Creates an invalid result with the given errors.
     */
    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, errors, List.of());
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
        return new ValidationResult(false, errors, warnings);
    }
    
    /**
     * Combines this result with another, taking the most restrictive validity.
     * Accumulates all errors and warnings from both results.
     */
    public ValidationResult combine(ValidationResult other) {
        if (other == null) return this;
        
        List<String> combinedErrors = new ArrayList<>(this.errors);
        combinedErrors.addAll(other.errors);
        
        List<String> combinedWarnings = new ArrayList<>(this.warnings);
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
    
    /**
     * Returns true if there are any errors.
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    /**
     * Returns true if there are any warnings.
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}