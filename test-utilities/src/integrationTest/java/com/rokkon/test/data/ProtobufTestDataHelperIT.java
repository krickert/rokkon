package com.rokkon.test.data;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class ProtobufTestDataHelperIT extends ProtobufTestDataHelperTestBase {

    // Use a static instance to ensure thread safety test works
    private static ProtobufTestDataHelper protobufTestDataHelper;

    @Override
    protected ProtobufTestDataHelper getProtobufTestDataHelper() {
        if (protobufTestDataHelper == null) {
            synchronized (ProtobufTestDataHelperIT.class) {
                if (protobufTestDataHelper == null) {
                    protobufTestDataHelper = new ProtobufTestDataHelper();
                }
            }
        }
        return protobufTestDataHelper;
    }
}