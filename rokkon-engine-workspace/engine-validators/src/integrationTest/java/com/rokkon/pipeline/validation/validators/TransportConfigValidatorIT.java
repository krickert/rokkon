package com.rokkon.pipeline.validation.validators;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeEach;

@QuarkusIntegrationTest
public class TransportConfigValidatorIT extends TransportConfigValidatorTestBase {
    
    private TransportConfigValidator validator;
    
    @BeforeEach
    void setup() {
        validator = new TransportConfigValidator();
    }
    
    @Override
    protected TransportConfigValidator getValidator() {
        return validator;
    }
}