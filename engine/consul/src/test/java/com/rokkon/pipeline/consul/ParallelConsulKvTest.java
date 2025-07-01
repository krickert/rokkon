package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.test.MockConsulClient;
import io.vertx.mutiny.ext.consul.ConsulClient;
import org.junit.jupiter.api.BeforeEach;

/**
 * Unit test for parallel Consul KV operations.
 * Uses mocks to avoid requiring a real Consul instance.
 * This test doesn't use @QuarkusTest since it uses mocks exclusively.
 */
class ParallelConsulKvTest extends ParallelConsulKvTestBase {

    private MockConsulClient mockConsulClient;

    @BeforeEach
    void setupMocks() {
        super.setup();
        // Create our mock Consul client
        mockConsulClient = new MockConsulClient();
    }

    @Override
    protected ConsulClient getConsulClient() {
        return mockConsulClient;
    }

    @Override
    protected boolean isRealConsul() {
        return false; // This is a unit test with mocks
    }
}
