package com.rokkon.pipeline.validation;

import java.util.List;

/**
 * An empty/no-op implementation of ValidationResult for testing and mocking purposes.
 * Always returns success with no errors or warnings.
 */
public class EmptyValidationResult implements ValidationResult {
    
    private static final EmptyValidationResult INSTANCE = new EmptyValidationResult();
    
    private EmptyValidationResult() {
        // Singleton pattern
    }
    
    /**
     * Get the singleton instance of EmptyValidationResult.
     */
    public static EmptyValidationResult instance() {
        return INSTANCE;
    }
    
    @Override
    public boolean valid() {
        return true;
    }
    
    @Override
    public List<String> errors() {
        return List.of();
    }
    
    @Override
    public List<String> warnings() {
        return List.of();
    }
    
    @Override
    public ValidationResult combine(ValidationResult other) {
        // When combining with empty, just return the other result
        return other != null ? other : this;
    }
    
    @Override
    public boolean hasIssues() {
        return false;
    }
    
    @Override
    public boolean hasErrors() {
        return false;
    }
    
    @Override
    public boolean hasWarnings() {
        return false;
    }
    
    @Override
    public String toString() {
        return "EmptyValidationResult(valid=true, errors=[], warnings=[])";
    }
}