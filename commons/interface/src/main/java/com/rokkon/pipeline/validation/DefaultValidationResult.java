package com.rokkon.pipeline.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of pipeline configuration validation.
 * Contains validation status, errors, and warnings.
 */
public record DefaultValidationResult(
    boolean valid,
    List<String> errors,
    List<String> warnings
) implements ValidationResult {

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

    /**
     * Ensures immutability of lists
     */
    public DefaultValidationResult {
        errors = errors != null ? List.copyOf(errors) : List.of();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    /**
     * Combines this result with another, taking the most restrictive validity.
     * Accumulates all errors and warnings from both results.
     */
    @Override
    public ValidationResult combine(ValidationResult other) {
        if (other == null) return this;

        List<String> combinedErrors = new ArrayList<>(this.errors);
        combinedErrors.addAll(other.errors());

        List<String> combinedWarnings = new ArrayList<>(this.warnings);
        combinedWarnings.addAll(other.warnings());

        return new DefaultValidationResult(
            this.valid && other.valid(),
            combinedErrors,
            combinedWarnings
        );
    }

    /**
     * Returns true if there are any issues (errors or warnings).
     */
    @Override
    public boolean hasIssues() {
        return !errors.isEmpty() || !warnings.isEmpty();
    }

    /**
     * Returns true if there are any errors.
     */
    @Override
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Returns true if there are any warnings.
     */
    @Override
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}