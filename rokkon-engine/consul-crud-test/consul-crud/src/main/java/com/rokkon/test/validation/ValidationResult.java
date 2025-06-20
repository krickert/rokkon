package com.rokkon.test.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Simplified validation result
 */
public class ValidationResult {
    private final boolean valid;
    private final List<String> errors;
    
    private ValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
    }
    
    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }
    
    public static ValidationResult failure(String error) {
        return new ValidationResult(false, List.of(error));
    }
    
    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, errors);
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }
}