package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.test.MockConsulClient;
import com.rokkon.pipeline.consul.test.UnifiedTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.mutiny.ext.consul.ConsulClient;

/**
 * Unit test for isolated Consul KV writes using MockConsulClient.
 * Tests the isolation pattern without requiring real Consul.
 */
@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
public class IsolatedConsulKvUnitTest extends IsolatedConsulKvTestBase {
    
    private final MockConsulClient mockConsulClient = new MockConsulClient();
    
    @Override
    protected ConsulClient getConsulClient() {
        return mockConsulClient;
    }
    
    @Override
    protected boolean isRealConsul() {
        return false; // Using mock
    }
}