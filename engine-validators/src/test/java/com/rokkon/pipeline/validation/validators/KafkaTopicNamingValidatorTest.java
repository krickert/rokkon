package com.rokkon.pipeline.validation.validators;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Unit test for KafkaTopicNamingValidator using @QuarkusTest.
 * Tests the validator in dev mode with Quarkus test environment and CDI injection.
 */
@QuarkusTest
class KafkaTopicNamingValidatorTest extends KafkaTopicNamingValidatorTestBase {

    @Inject
    KafkaTopicNamingValidator validator;

    @Override
    protected KafkaTopicNamingValidator getValidator() {
        return validator;
    }
}