package com.rokkon.pipeline.validation.validators;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeEach;

@QuarkusIntegrationTest
public class StepReferenceValidatorIT extends StepReferenceValidatorTestBase {
    
    private StepReferenceValidator validator;
    
    @BeforeEach
    void setup() {
        validator = new StepReferenceValidator();
    }
    
    @Override
    protected StepReferenceValidator getValidator() {
        return validator;
    }
}