package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import com.rokkon.pipeline.consul.test.UnifiedTestProfile;
import com.rokkon.pipeline.validation.ValidationResultFactory;
import com.rokkon.pipeline.utils.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.InjectMock;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.KeyValue;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test for ClusterService that uses mocked dependencies.
 * Tests service logic without requiring real Consul.
 */
@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
class ClusterServiceUnitTest extends ClusterServiceTestBase {
    
    @Inject
    ClusterService clusterService;
    
    @InjectMock
    ClusterServiceImpl clusterServiceImpl;
    
    @InjectMock
    ConsulConnectionManager connectionManager;
    
    private ConsulClient mockConsulClient;
    private Map<String, String> kvStore = new HashMap<>();
    private final ObjectMapper objectMapper = ObjectMapperFactory.createConfiguredMapper();
    
    @BeforeEach
    void setup() {
        // Configure the clusterServiceImpl mock to use test prefix
        clusterServiceImpl.kvPrefix = "test";
        
        // Inject the mocked dependencies
        clusterServiceImpl.connectionManager = connectionManager;
        clusterServiceImpl.objectMapper = objectMapper;
        
        // Configure mock to call real methods
        doCallRealMethod().when(clusterServiceImpl).createCluster(anyString());
        doCallRealMethod().when(clusterServiceImpl).createCluster(null);
        doCallRealMethod().when(clusterServiceImpl).getCluster(anyString());
        doCallRealMethod().when(clusterServiceImpl).clusterExists(anyString());
        doCallRealMethod().when(clusterServiceImpl).deleteCluster(anyString());
        doCallRealMethod().when(clusterServiceImpl).listClusters();
        
        // Create mock ConsulClient
        mockConsulClient = mock(ConsulClient.class);
        when(connectionManager.getClient()).thenReturn(Optional.of(mockConsulClient));
        
        // Clear test data
        kvStore.clear();
        
        // Set up mock Consul KV operations
        when(mockConsulClient.getValue(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = kvStore.get(key);
            KeyValue kv = null;
            if (value != null) {
                kv = new KeyValue();
                kv.setKey(key);
                kv.setValue(value);
            }
            return io.vertx.core.Future.succeededFuture(kv);
        });
        
        when(mockConsulClient.putValue(anyString(), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            kvStore.put(key, value);
            return io.vertx.core.Future.succeededFuture(true);
        });
        
        when(mockConsulClient.deleteValue(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            kvStore.remove(key);
            return io.vertx.core.Future.succeededFuture();
        });
        
        when(mockConsulClient.deleteValues(anyString())).thenAnswer(invocation -> {
            String prefix = invocation.getArgument(0);
            // Remove all keys that start with the prefix
            kvStore.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
            return io.vertx.core.Future.succeededFuture();
        });
        
        when(mockConsulClient.getKeys(anyString())).thenAnswer(invocation -> {
            String prefix = invocation.getArgument(0);
            var keys = kvStore.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .toList();
            return io.vertx.core.Future.succeededFuture(keys);
        });
    }
    
    @Override
    protected ClusterService getClusterService() {
        return clusterServiceImpl;
    }
}