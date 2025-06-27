package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.validation.PipelineConfigValidator;
import com.rokkon.pipeline.validation.DefaultValidationResult;
import com.rokkon.pipeline.validation.DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates retry configuration parameters across all pipeline steps.
 * Ensures retry settings are reasonable and will not cause system instability.
 * Note: The model already enforces non-negative values, so this validator
 * focuses on business rule validation.
 */
@ApplicationScoped
public class RetryConfigValidator implements PipelineConfigValidator {

    private static final int MAX_RETRY_ATTEMPTS = 100;
    private static final int WARN_RETRY_ATTEMPTS = 10;
    private static final long MAX_BACKOFF_MS = 3600000; // 1 hour
    private static final long WARN_BACKOFF_MS = 300000; // 5 minutes
    private static final long MAX_TIMEOUT_MS = 3600000; // 1 hour
    private static final long WARN_TIMEOUT_MS = 600000; // 10 minutes

    @Override
    public DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult validate(PipelineConfig config) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (config == null || config.pipelineSteps() == null) {
            errors.add("Pipeline configuration or steps cannot be null");
            return new DefaultValidationResult(false, errors, warnings);
        }

        for (var entry : config.pipelineSteps().entrySet()) {
            String stepId = entry.getKey();
            PipelineStepConfig step = entry.getValue();
            validateStepRetryConfig(stepId, step, errors, warnings);
        }

        return new DefaultValidationResult(errors.isEmpty(), errors, warnings);
    }

    private void validateStepRetryConfig(String stepId, PipelineStepConfig step, List<String> errors, List<String> warnings) {
        if (step == null) {
            return;
        }

        String prefix = String.format("Step '%s' retry config", stepId);

        // Validate max retries (model already prevents negative values)
        if (step.maxRetries() != null) {
            if (step.maxRetries() > MAX_RETRY_ATTEMPTS) {
                errors.add(String.format("%s: maxRetries exceeds maximum allowed value of %d (was %d)", 
                          prefix, MAX_RETRY_ATTEMPTS, step.maxRetries()));
            } else if (step.maxRetries() > WARN_RETRY_ATTEMPTS) {
                warnings.add(String.format("%s: high number of retry attempts (%d) may cause processing delays", 
                            prefix, step.maxRetries()));
            }
        }

        // Validate retry backoff (model already prevents negative values)
        if (step.retryBackoffMs() != null) {
            if (step.retryBackoffMs() > MAX_BACKOFF_MS) {
                errors.add(String.format("%s: retryBackoffMs exceeds maximum allowed value of %d ms (was %d)", 
                          prefix, MAX_BACKOFF_MS, step.retryBackoffMs()));
            } else if (step.retryBackoffMs() > WARN_BACKOFF_MS) {
                warnings.add(String.format("%s: high retry backoff (%d ms) may cause significant processing delays", 
                            prefix, step.retryBackoffMs()));
            }
        }

        // Validate step timeout (model already prevents negative values)
        if (step.stepTimeoutMs() != null) {
            if (step.stepTimeoutMs() > MAX_TIMEOUT_MS) {
                errors.add(String.format("%s: stepTimeoutMs exceeds maximum allowed value of %d ms (was %d)", 
                          prefix, MAX_TIMEOUT_MS, step.stepTimeoutMs()));
            } else if (step.stepTimeoutMs() > WARN_TIMEOUT_MS) {
                warnings.add(String.format("%s: high step timeout (%d ms) may cause processing delays", 
                            prefix, step.stepTimeoutMs()));
            }
        }

        // Check for logical issues
        if (step.maxRetries() != null && step.maxRetries() == 0 && step.retryBackoffMs() != null && step.retryBackoffMs() > 0) {
            warnings.add(String.format("%s: retryBackoffMs defined but maxRetries is 0 (retries disabled)", prefix));
        }

        // Check timeout vs retry relationship
        if (step.stepTimeoutMs() != null && step.retryBackoffMs() != null && step.maxRetries() != null && step.maxRetries() > 0) {
            long totalRetryTime = step.retryBackoffMs() * step.maxRetries();
            if (totalRetryTime > step.stepTimeoutMs()) {
                warnings.add(String.format("%s: total retry time (%d ms) may exceed step timeout (%d ms)", 
                            prefix, totalRetryTime, step.stepTimeoutMs()));
            }
        }

        // Check for max retry backoff if it exists in the model
        if (step.retryBackoffMs() != null && step.maxRetryBackoffMs() != null) {
            if (step.retryBackoffMs() > step.maxRetryBackoffMs()) {
                errors.add(String.format("%s: initial retry backoff (%d ms) cannot exceed max retry backoff (%d ms)", 
                          prefix, step.retryBackoffMs(), step.maxRetryBackoffMs()));
            }
        }
    }

    @Override
    public int getPriority() {
        return 70;
    }

    @Override
    public String getValidatorName() {
        return "RetryConfigValidator";
    }
}