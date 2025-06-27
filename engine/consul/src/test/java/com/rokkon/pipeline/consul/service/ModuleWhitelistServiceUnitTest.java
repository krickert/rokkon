package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.config.service.ModuleWhitelistService;
import com.rokkon.pipeline.config.service.PipelineConfigService;
import com.rokkon.pipeline.consul.service.ModuleWhitelistServiceImpl;
import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import com.rokkon.pipeline.consul.test.UnifiedTestProfile;
import com.rokkon.pipeline.validation.CompositeValidator;
import com.rokkon.pipeline.validation.ValidationResult;
import com.rokkon.pipeline.validation.ValidationResultFactory;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.KeyValue;
import io.vertx.ext.consul.ServiceEntry;
import io.vertx.ext.consul.ServiceList;
import io.vertx.ext.consul.ServiceEntryList;
import io.vertx.mutiny.core.Vertx;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.util.ObjectMapperFactory;

import java.util.*;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for ModuleWhitelistService using proper CDI mocks.
 */
@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
class ModuleWhitelistServiceUnitTest extends ModuleWhitelistServiceTestBase {
    
    @Inject
    ModuleWhitelistService whitelistService;
    
    @InjectMock
    ModuleWhitelistServiceImpl whitelistServiceImpl;
    
    @InjectMock
    ClusterService clusterService;
    
    @InjectMock  
    PipelineConfigService pipelineConfigService;
    
    @InjectMock
    CompositeValidator<PipelineConfig> pipelineValidator;
    
    @InjectMock
    ConsulConnectionManager connectionManager;
    
    private ConsulClient mockConsulClient;
    private Map<String, String> kvStore = new HashMap<>();
    private Map<String, List<ServiceEntry>> serviceRegistry = new HashMap<>();
    private final ObjectMapper objectMapper = ObjectMapperFactory.createConfiguredMapper();
    
    @BeforeEach
    void setUp() {
        // Configure the whitelistServiceImpl mock to use test prefix
        whitelistServiceImpl.kvPrefix = "test";
        
        // Inject the mocked dependencies into the implementation mock
        whitelistServiceImpl.connectionManager = connectionManager;
        whitelistServiceImpl.clusterService = clusterService;
        whitelistServiceImpl.pipelineConfigService = pipelineConfigService;
        whitelistServiceImpl.objectMapper = objectMapper;
        
        // Configure mock to call real methods
        doCallRealMethod().when(whitelistServiceImpl).whitelistModule(anyString(), any(ModuleWhitelistRequest.class));
        doCallRealMethod().when(whitelistServiceImpl).listWhitelistedModules(anyString());
        doCallRealMethod().when(whitelistServiceImpl).removeModuleFromWhitelist(anyString(), anyString());
        
        // Create mock ConsulClient
        mockConsulClient = mock(ConsulClient.class);
        when(connectionManager.getClient()).thenReturn(Optional.of(mockConsulClient));
        
        // Clear test data
        kvStore.clear();
        serviceRegistry.clear();
        
        // Set up mock cluster service
        when(clusterService.createCluster(anyString()))
            .thenReturn(Uni.createFrom().item(ValidationResultFactory.success()));
        
        when(clusterService.getCluster(anyString()))
            .thenReturn(Uni.createFrom().item(Optional.of(createTestClusterMetadata())));
        
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
        
        // Set up mock service health checks
        when(mockConsulClient.healthServiceNodes(anyString(), anyBoolean())).thenAnswer(invocation -> {
            String serviceName = invocation.getArgument(0);
            List<ServiceEntry> entries = serviceRegistry.getOrDefault(serviceName, new ArrayList<>());
            ServiceEntryList serviceEntryList = new ServiceEntryList();
            serviceEntryList.setList(entries);
            return io.vertx.core.Future.succeededFuture(serviceEntryList);
        });
        
        // Set up mock pipeline config service
        when(pipelineConfigService.createPipeline(anyString(), anyString(), any()))
            .thenReturn(Uni.createFrom().item(ValidationResultFactory.success()));
        
        when(pipelineConfigService.updatePipeline(anyString(), anyString(), any()))
            .thenAnswer(invocation -> {
                PipelineConfig config = invocation.getArgument(2);
                String clusterName = invocation.getArgument(0);
                
                // Check if all modules are whitelisted
                String clusterKey = "test/clusters/" + clusterName + "/config";
                String configJson = kvStore.get(clusterKey);
                
                if (configJson != null) {
                    try {
                        PipelineClusterConfig clusterConfig = objectMapper.readValue(configJson, PipelineClusterConfig.class);
                        PipelineModuleMap moduleMap = clusterConfig.pipelineModuleMap();
                        
                        for (var step : config.pipelineSteps().values()) {
                            if (step.processorInfo() != null && step.processorInfo().grpcServiceName() != null) {
                                String moduleId = step.processorInfo().grpcServiceName();
                                if (moduleMap == null || !moduleMap.availableModules().containsKey(moduleId)) {
                                    return Uni.createFrom().item(
                                        ValidationResultFactory.failure(
                                            String.format("Module %s is not whitelisted for cluster %s", 
                                                        moduleId, clusterName)
                                        )
                                    );
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore parsing errors in test
                    }
                }
                
                return Uni.createFrom().item(ValidationResultFactory.success());
            });
        
        // Set up validator to always pass
        when(pipelineValidator.validate(any())).thenReturn(ValidationResultFactory.success());
        
        // Initialize cluster config in KV store
        initializeClusterInKvStore("test-cluster", createTestClusterConfigWithWhitelist());
    }
    
    @Override
    protected ModuleWhitelistService getWhitelistService() {
        return whitelistService;
    }
    
    @Override
    protected String getConsulHost() {
        return "localhost";
    }
    
    @Override
    protected String getConsulPort() {
        return "8500";
    }
    
    @Override
    protected ClusterService getClusterService() {
        return clusterService;
    }
    
    @Override
    protected PipelineConfigService getPipelineConfigService() {
        return pipelineConfigService;
    }
    
    @Override
    protected Uni<Boolean> registerModuleInConsul(String moduleName, String host, int port) {
        // Add module to service registry
        ServiceEntry entry = new ServiceEntry();
        entry.setService(new io.vertx.ext.consul.Service());
        entry.getService().setId(moduleName);
        entry.getService().setAddress(host);
        entry.getService().setPort(port);
        
        serviceRegistry.put(moduleName, List.of(entry));
        return Uni.createFrom().item(true);
    }
    
    
    private void initializeClusterInKvStore(String clusterName, PipelineClusterConfig config) {
        try {
            String key = "test/clusters/" + clusterName + "/config";
            String json = objectMapper.writeValueAsString(config);
            kvStore.put(key, json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize cluster config", e);
        }
    }
    
    // Add prefix test as requested
    @Test
    void testKvPrefixIsolation() {
        // This test verifies that the service uses the configured KV prefix
        // The prefix is set by UnifiedTestProfile for test classes to "test"
        
        // Verify the mock has the correct prefix
        assertThat(whitelistServiceImpl.kvPrefix).isEqualTo("test");
        
        // Create a module registration
        registerModuleInConsul("prefix-test-module", "localhost", 9090).await().indefinitely();
        
        // Whitelist the module  
        ModuleWhitelistRequest request = new ModuleWhitelistRequest(
            "Prefix Test Module",
            "prefix-test-module"
        );
        
        ModuleWhitelistResponse response = whitelistServiceImpl
            .whitelistModule("test-cluster", request)
            .await().indefinitely();
        
        assertThat(response.success()).isTrue();
        
        // Verify the config was stored with the test prefix
        assertThat(kvStore.keySet())
            .describedAs("KV store should contain keys with 'test/' prefix")
            .anySatisfy(key -> assertThat(key).startsWith("test/clusters/test-cluster/"));
        
        // Verify no data was written to the default prefix
        assertThat(kvStore.keySet())
            .describedAs("KV store should not contain keys with default 'rokkon/' prefix")
            .noneSatisfy(key -> assertThat(key).startsWith("rokkon/"));
        
        // Verify the exact key exists
        String expectedKey = "test/clusters/test-cluster/config";
        assertThat(kvStore.containsKey(expectedKey))
            .withFailMessage("Expected key '%s' not found. Available keys: %s", 
                           expectedKey, kvStore.keySet())
            .isTrue();
        
        // Verify the stored config contains the whitelisted module
        String configJson = kvStore.get(expectedKey);
        assertThat(configJson).isNotNull();
        
        try {
            PipelineClusterConfig config = objectMapper.readValue(configJson, PipelineClusterConfig.class);
            assertThat(config.pipelineModuleMap()).isNotNull();
            assertThat(config.pipelineModuleMap().availableModules())
                .containsKey("prefix-test-module");
        } catch (Exception e) {
            throw new AssertionError("Failed to parse stored config", e);
        }
    }
    
    private ClusterMetadata createTestClusterMetadata() {
        return new ClusterMetadata(
            "test-cluster",
            Instant.now(),
            null,
            Map.of("status", "active")
        );
    }
    
    private PipelineClusterConfig createTestClusterConfigWithWhitelist() {
        // Create empty module map
        Map<String, PipelineModuleConfiguration> modules = new HashMap<>();
        PipelineModuleMap moduleMap = new PipelineModuleMap(modules);
        
        // Create empty graph config
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(Map.of());
        
        return new PipelineClusterConfig(
            "test-cluster",
            graphConfig,
            moduleMap,
            null,
            Set.of(),
            Set.of()
        );
    }
}