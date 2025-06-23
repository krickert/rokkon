package com.rokkon.pipeline.validation.validators;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Unit test for RequiredFieldsValidator using @QuarkusTest.
 * Tests the validator in dev mode with Quarkus test environment and CDI injection.
 */
@QuarkusTest
class RequiredFieldsValidatorTest extends RequiredFieldsValidatorTestBase {

    @Inject
    RequiredFieldsValidator validator;

    @Override
    protected RequiredFieldsValidator getValidator() {
        return validator;
    }
}