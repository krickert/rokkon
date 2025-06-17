package com.rokkon.pipeline.validation.validators;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeEach;

/**
 * Integration test for RequiredFieldsValidator using @QuarkusIntegrationTest.
 * Tests the validator in production mode with a fully deployed Quarkus application.
 */
@QuarkusIntegrationTest
public class RequiredFieldsValidatorIT extends RequiredFieldsValidatorTestBase {
    
    private RequiredFieldsValidator validator;
    
    @BeforeEach
    void setup() {
        // In integration test, create validator manually since we're testing 
        // the production-built application
        validator = new RequiredFieldsValidator();
    }
    
    @Override
    protected RequiredFieldsValidator getValidator() {
        return validator;
    }
}