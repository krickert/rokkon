package com.rokkon.pipeline.validation;

import java.util.List;

/**
 * Interface for validation results in the Rokkon pipeline system.
 * Contains validation status, errors, and warnings.
 */
public interface ValidationResult {
    
    /**
     * Returns whether the validation was successful.
     * 
     * @return true if valid, false otherwise
     */
    boolean valid();
    
    /**
     * Returns the list of validation errors.
     * 
     * @return list of error messages
     */
    List<String> errors();
    
    /**
     * Returns the list of validation warnings.
     * 
     * @return list of warning messages
     */
    List<String> warnings();
    
    /**
     * Combines this result with another, taking the most restrictive validity.
     * Accumulates all errors and warnings from both results.
     * 
     * @param other the other validation result to combine with
     * @return a new combined validation result
     */
    ValidationResult combine(ValidationResult other);
    
    /**
     * Returns true if there are any issues (errors or warnings).
     * 
     * @return true if there are any issues
     */
    boolean hasIssues();
    
    /**
     * Returns true if there are any errors.
     * 
     * @return true if there are any errors
     */
    boolean hasErrors();
    
    /**
     * Returns true if there are any warnings.
     * 
     * @return true if there are any warnings
     */
    boolean hasWarnings();
    
    /**
     * Factory method to create an empty validation result.
     * Useful for testing and cases where validation is not needed.
     * Always returns valid with no errors or warnings.
     * @return an empty validation result that is always valid
     */
    static ValidationResult empty() {
        return EmptyValidationResult.instance();
    }
}