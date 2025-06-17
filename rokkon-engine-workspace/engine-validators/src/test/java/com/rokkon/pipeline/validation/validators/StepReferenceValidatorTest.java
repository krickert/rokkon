package com.rokkon.pipeline.validation.validators;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class StepReferenceValidatorTest extends StepReferenceValidatorTestBase {
    
    @Inject
    StepReferenceValidator validator;
    
    @Override
    protected StepReferenceValidator getValidator() {
        return validator;
    }
}