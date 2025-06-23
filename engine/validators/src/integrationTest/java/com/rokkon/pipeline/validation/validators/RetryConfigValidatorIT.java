package com.rokkon.pipeline.validation.validators;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
public class RetryConfigValidatorIT extends RetryConfigValidatorTestBase {

    private final RetryConfigValidator validator = new RetryConfigValidator();

    @Override
    protected RetryConfigValidator getValidator() {
        return validator;
    }
}