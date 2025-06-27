package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.test.UnifiedTestProfile;
import com.rokkon.pipeline.consul.test.MockConsulClient;
import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.InjectMock;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.util.Optional;

/**
 * Unit test for Consul connection functionality.
 * Uses mocks to avoid requiring a real Consul instance.
 */
@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
class BasicConsulConnectionUnitTest extends BasicConsulConnectionTestBase {
    
    @InjectMock
    ConsulConnectionManager connectionManager;
    
    private MockConsulClient mockConsulClient;
    
    @BeforeEach
    void setupMocks() {
        // Create our mock Consul client
        mockConsulClient = new MockConsulClient();
        
        // Configure the mock connection manager to return our mock client
        Mockito.when(connectionManager.getMutinyClient()).thenReturn(Optional.of(mockConsulClient));
    }
    
    @Override
    protected ConsulConnectionManager getConnectionManager() {
        return connectionManager;
    }
    
    @Override
    protected boolean expectRealConsul() {
        return false; // This is a unit test with mocks
    }
}