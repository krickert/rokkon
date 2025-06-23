package com.rokkon.pipeline.validation.validators;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class InterPipelineLoopValidatorTest extends InterPipelineLoopValidatorTestBase {
    
    @Inject
    InterPipelineLoopValidator validator;
    
    @Override
    protected InterPipelineLoopValidator getValidator() {
        return validator;
    }
}