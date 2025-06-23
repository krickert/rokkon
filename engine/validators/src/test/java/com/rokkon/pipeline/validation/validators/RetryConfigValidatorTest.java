package com.rokkon.pipeline.validation.validators;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class RetryConfigValidatorTest extends RetryConfigValidatorTestBase {

    @Inject
    RetryConfigValidator validator;

    @Override
    protected RetryConfigValidator getValidator() {
        return validator;
    }
}