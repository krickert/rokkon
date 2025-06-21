package com.rokkon.pipeline.consul;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.consul.service.*;
import com.rokkon.pipeline.consul.test.ConsulTestResource;
import com.rokkon.pipeline.consul.test.TestSeedingService;
import com.rokkon.pipeline.consul.test.TestSeedingServiceImpl;
import com.rokkon.pipeline.validation.CompositeValidator;
import com.rokkon.pipeline.validation.Validator;
import com.rokkon.pipeline.validation.validators.RequiredFieldsValidator;
import com.rokkon.pipeline.validation.validators.StepTypeValidator;
import com.rokkon.test.containers.TestModuleContainerResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Integration test for methodical build-up of the engine ecosystem.
 * Runs in prod mode with real Consul and test-module containers.
 * 
 * NOTE: This is currently a placeholder - we'll need to implement REST clients
 * or other mechanisms to access the services in integration test mode.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
@QuarkusTestResource(TestModuleContainerResource.class)
class MethodicalBuildUpIT extends MethodicalBuildUpTestBase {
    
    private ClusterService clusterService;
    private ModuleWhitelistService moduleWhitelistService;
    private PipelineConfigService pipelineConfigService;
    private TestSeedingService testSeedingService;
    
    @BeforeEach
    void setUp() {
        
        // Get Consul connection details - in integration tests, these come from test resources
        String consulHost = System.getProperty("consul.host", "localhost");
        String consulPort = System.getProperty("consul.port", "8500");
        
        // Create service implementations directly
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Create ClusterService
        ClusterServiceImpl clusterServiceImpl = new ClusterServiceImpl();
        setField(clusterServiceImpl, "consulHost", consulHost);
        setField(clusterServiceImpl, "consulPort", consulPort);
        setField(clusterServiceImpl, "objectMapper", objectMapper);
        this.clusterService = clusterServiceImpl;
        
        // Create PipelineConfigService
        PipelineConfigServiceImpl pipelineConfigServiceImpl = new PipelineConfigServiceImpl();
        setField(pipelineConfigServiceImpl, "consulHost", consulHost);
        setField(pipelineConfigServiceImpl, "consulPort", consulPort);
        setField(pipelineConfigServiceImpl, "objectMapper", objectMapper);
        setField(pipelineConfigServiceImpl, "clusterService", clusterService);
        
        // Create validators
        List<Validator<PipelineConfig>> validators = List.of(
            new RequiredFieldsValidator(),
            new StepTypeValidator()
        );
        CompositeValidator<PipelineConfig> validator = new CompositeValidator<>("pipeline-validator", validators);
        setField(pipelineConfigServiceImpl, "validator", validator);
        this.pipelineConfigService = pipelineConfigServiceImpl;
        
        // Create ModuleWhitelistService
        ModuleWhitelistServiceImpl moduleWhitelistServiceImpl = new ModuleWhitelistServiceImpl();
        setField(moduleWhitelistServiceImpl, "consulHost", consulHost);
        setField(moduleWhitelistServiceImpl, "consulPort", consulPort);
        setField(moduleWhitelistServiceImpl, "objectMapper", objectMapper);
        setField(moduleWhitelistServiceImpl, "clusterService", clusterService);
        setField(moduleWhitelistServiceImpl, "pipelineConfigService", pipelineConfigService);
        setField(moduleWhitelistServiceImpl, "pipelineValidator", validator);
        this.moduleWhitelistService = moduleWhitelistServiceImpl;
        
        // Create TestSeedingService
        TestSeedingServiceImpl testSeedingServiceImpl = new TestSeedingServiceImpl();
        setField(testSeedingServiceImpl, "clusterService", clusterService);
        setField(testSeedingServiceImpl, "moduleWhitelistService", moduleWhitelistService);
        setField(testSeedingServiceImpl, "pipelineConfigService", pipelineConfigService);
        setField(testSeedingServiceImpl, "moduleName", "test-module");
        setField(testSeedingServiceImpl, "moduleHost", ConfigProvider.getConfig().getValue("test.module.container.network.alias", String.class));
        setField(testSeedingServiceImpl, "modulePort", 9090);
        this.testSeedingService = testSeedingServiceImpl;
    }
    
    @Override
    protected ClusterService getClusterService() {
        return clusterService;
    }
    
    @Override
    protected ModuleWhitelistService getModuleWhitelistService() {
        return moduleWhitelistService;
    }
    
    @Override
    protected PipelineConfigService getPipelineConfigService() {
        return pipelineConfigService;
    }
    
    @Override
    protected TestSeedingService getTestSeedingService() {
        return testSeedingService;
    }
    
    /**
     * Helper method to set private fields via reflection.
     */
    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName + " on " + target.getClass().getName(), e);
        }
    }
}