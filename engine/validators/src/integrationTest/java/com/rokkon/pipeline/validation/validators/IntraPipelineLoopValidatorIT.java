package com.rokkon.pipeline.validation.validators;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeEach;

@QuarkusIntegrationTest
public class IntraPipelineLoopValidatorIT extends IntraPipelineLoopValidatorTestBase {
    
    private IntraPipelineLoopValidator validator;
    
    @BeforeEach
    void setup() {
        validator = new IntraPipelineLoopValidator();
    }
    
    @Override
    protected IntraPipelineLoopValidator getValidator() {
        return validator;
    }
}