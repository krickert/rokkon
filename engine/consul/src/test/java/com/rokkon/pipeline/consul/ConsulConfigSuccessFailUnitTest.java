package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.test.UnifiedTestProfile;
import com.rokkon.pipeline.consul.test.MockConsulClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.mutiny.ext.consul.ConsulClient;
import org.jboss.logging.Logger;

/**
 * Unit test for Consul configuration operations.
 * Uses MockConsulClient to test without requiring a real Consul instance.
 */
@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
public class ConsulConfigSuccessFailUnitTest extends ConsulConfigSuccessFailTestBase {
    private static final Logger LOG = Logger.getLogger(ConsulConfigSuccessFailUnitTest.class);
    
    private MockConsulClient mockConsulClient = new MockConsulClient();
    
    @Override
    protected ConsulClient getConsulClient() {
        return mockConsulClient;
    }
    
    @Override
    protected boolean isRealConsul() {
        return false; // Unit test - no real Consul
    }
}