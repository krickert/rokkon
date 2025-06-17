package com.rokkon.pipeline.validation.validators;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class IntraPipelineLoopValidatorTest extends IntraPipelineLoopValidatorTestBase {
    
    @Inject
    IntraPipelineLoopValidator validator;
    
    @Override
    protected IntraPipelineLoopValidator getValidator() {
        return validator;
    }
}