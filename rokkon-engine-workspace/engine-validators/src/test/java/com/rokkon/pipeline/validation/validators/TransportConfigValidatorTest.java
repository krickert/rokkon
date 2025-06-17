package com.rokkon.pipeline.validation.validators;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class TransportConfigValidatorTest extends TransportConfigValidatorTestBase {
    
    @Inject
    TransportConfigValidator validator;
    
    @Override
    protected TransportConfigValidator getValidator() {
        return validator;
    }
}