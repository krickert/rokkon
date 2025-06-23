package com.rokkon.pipeline.validation.validators;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
public class OutputRoutingValidatorIT extends OutputRoutingValidatorTestBase {

    private final OutputRoutingValidator validator = new OutputRoutingValidator();

    @Override
    protected OutputRoutingValidator getValidator() {
        return validator;
    }
}