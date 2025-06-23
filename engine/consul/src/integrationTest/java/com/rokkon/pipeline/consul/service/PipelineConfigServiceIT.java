package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.consul.test.ConsulTestResource;
import com.rokkon.pipeline.validation.CompositeValidator;
import com.rokkon.pipeline.validation.Validator;
import com.rokkon.pipeline.validation.validators.RequiredFieldsValidator;
import com.rokkon.pipeline.validation.validators.StepTypeValidator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Integration test for PipelineConfigService running in prod mode.
 * Extends PipelineConfigServiceTestBase to reuse common test logic.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
class PipelineConfigServiceIT extends PipelineConfigServiceTestBase {

    private PipelineConfigService pipelineConfigServiceClient;
    private ClusterService clusterServiceClient;

    @BeforeEach
    void initClients() {
        // In integration tests, we create services directly since we're testing
        // from outside the application container
        
        // Get Consul host and port from system properties set by ConsulTestResource
        // These will be the exposed ports accessible from the host machine
        String consulHost = System.getProperty("consul.host", "localhost");
        String consulPort = System.getProperty("consul.port", "8500");
        
        // Create service implementations directly
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Create ClusterService
        ClusterServiceImpl clusterServiceImpl = new ClusterServiceImpl();
        setField(clusterServiceImpl, "consulHost", consulHost);
        setField(clusterServiceImpl, "consulPort", consulPort);
        setField(clusterServiceImpl, "objectMapper", objectMapper);
        this.clusterServiceClient = clusterServiceImpl;
        
        // Create PipelineConfigService
        PipelineConfigServiceImpl pipelineConfigServiceImpl = new PipelineConfigServiceImpl();
        setField(pipelineConfigServiceImpl, "consulHost", consulHost);
        setField(pipelineConfigServiceImpl, "consulPort", consulPort);
        setField(pipelineConfigServiceImpl, "objectMapper", objectMapper);
        setField(pipelineConfigServiceImpl, "clusterService", clusterServiceClient);
        
        // Create validators for pipeline config service
        List<Validator<PipelineConfig>> validators = List.of(
            new RequiredFieldsValidator(),
            new StepTypeValidator()
        );
        CompositeValidator<PipelineConfig> validator = new CompositeValidator<>("pipeline-validator", validators);
        setField(pipelineConfigServiceImpl, "validator", validator);
        
        this.pipelineConfigServiceClient = pipelineConfigServiceImpl;
    }

    @Override
    protected PipelineConfigService getPipelineConfigService() {
        return pipelineConfigServiceClient;
    }

    @Override
    protected ClusterService getClusterService() {
        return clusterServiceClient;
    }

    // TODO: Create REST client implementations that call HTTP endpoints
    // These will need to be implemented once REST endpoints are created
    
    /**
     * Helper method to set private fields via reflection.
     * Needed for integration tests where we manually construct services.
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