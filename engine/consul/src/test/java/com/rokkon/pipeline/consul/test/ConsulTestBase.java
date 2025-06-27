package com.rokkon.pipeline.consul.test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.common.QuarkusTestResource;

/**
 * Base class for tests that require a running Consul instance.
 * This ensures Consul is started before Quarkus initialization.
 */
@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
@QuarkusTestResource(ConsulTestResource.class)
public abstract class ConsulTestBase {
    // Base class for all tests that need Consul
}