package com.rokkon.pipeline.registration;

import com.rokkon.search.grpc.ModuleRegistration;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Unit test for ModuleRegistration service using @QuarkusTest.
 * Uses injected gRPC client for testing.
 */
@QuarkusTest
class ModuleRegistrationServiceTest extends ModuleRegistrationTestBase {

    @GrpcClient
    ModuleRegistration moduleRegistrationClient;

    @Override
    protected ModuleRegistration getModuleRegistrationService() {
        return moduleRegistrationClient;
    }
}