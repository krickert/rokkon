package com.rokkon.pipeline.registration;

import com.google.protobuf.Empty;
import com.rokkon.search.grpc.*;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test class for ModuleRegistration service testing.
 * This abstract class can be extended by both unit tests (@QuarkusTest) 
 * and integration tests (@QuarkusIntegrationTest).
 */
public abstract class ModuleRegistrationTestBase {
    
    private static final Logger LOG = Logger.getLogger(ModuleRegistrationTestBase.class);
    
    protected abstract ModuleRegistration getModuleRegistrationService();
    
    @Test
    void testRegisterModule() {
        LOG.info("Testing module registration");
        
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
            .setServiceName("test-processor")
            .setServiceId("test-processor-123")
            .setHost("localhost")
            .setPort(49093)
            .setHealthEndpoint("/health")
            .putMetadata("version", "1.0.0")
            .putMetadata("type", "processor")
            .putMetadata("deploymentId", "test-deployment")
            .putMetadata("configSchema", "{\"type\":\"object\",\"properties\":{\"mode\":{\"type\":\"string\"}}}")
            .addTags("test")
            .addTags("processor")
            .build();

        getModuleRegistrationService().registerModule(moduleInfo)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
            .getItem();
        
        RegistrationStatus response = getModuleRegistrationService()
            .registerModule(moduleInfo)
            .await().indefinitely();
        
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).contains("test-processor");
        assertThat(response.getMessage()).contains("registered successfully");
        // In unit tests with MockConsul, this might be empty
        // In integration tests with real Consul, this should have a value
    }
    
    @Test
    void testRegisterModuleWithMissingRequiredFields() {
        LOG.info("Testing module registration with missing fields");
        
        // Missing host
        ModuleInfo invalidModule = ModuleInfo.newBuilder()
            .setServiceName("invalid-service")
            .setServiceId("invalid-123")
            .setPort(8080)
            .build();
            
        RegistrationStatus invalidResponse = getModuleRegistrationService()
            .registerModule(invalidModule)
            .await().indefinitely();
            
        assertThat(invalidResponse.getSuccess()).isFalse();
        assertThat(invalidResponse.getMessage()).containsIgnoringCase("missing required field");
    }
    
    @Test
    void testListModules() {
        LOG.info("Testing list modules");
        
        // First register a module
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
            .setServiceName("list-test-service")
            .setServiceId("list-test-456")
            .setHost("localhost")
            .setPort(8080)
            .setHealthEndpoint("/health")
            .build();

        getModuleRegistrationService()
            .registerModule(moduleInfo)
            .await().indefinitely();
        
        // List modules
        ModuleList moduleList = getModuleRegistrationService()
            .listModules(Empty.newBuilder().build())
            .await().indefinitely();
            
        assertThat(moduleList.getModulesCount()).isGreaterThanOrEqualTo(1);
        
        boolean found = moduleList.getModulesList().stream()
            .anyMatch(m -> m.getServiceId().equals("list-test-456"));
        assertThat(found).isTrue();
    }
    
    @Test
    void testUnregisterModule() {
        LOG.info("Testing module unregistration");
        
        // First register
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
            .setServiceName("unregister-test")
            .setServiceId("unregister-789")
            .setHost("localhost")
            .setPort(7070)
            .setHealthEndpoint("/health")
            .build();

        RegistrationStatus regStatus = getModuleRegistrationService()
            .registerModule(moduleInfo)
            .await().indefinitely();
            
        assertThat(regStatus.getSuccess()).isTrue();
        
        // Now unregister
        UnregisterRequest unregisterRequest = UnregisterRequest.newBuilder()
            .setServiceId("unregister-789")
            .build();
            
        getModuleRegistrationService().unregisterModule(unregisterRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
            .assertItem(response -> {
                assertThat(response.getSuccess()).isTrue();
                assertThat(response.getMessage()).contains("unregister-789");
                assertThat(response.getMessage()).contains("unregistered");
                return true;
            });
    }
    
    @Test
    void testHealthCheck() {
        LOG.info("Testing health check");
        
        HealthStatus health = getModuleRegistrationService()
            .checkHealth(Empty.newBuilder().build())
            .await().indefinitely();
            
        assertThat(health.getHealthy()).isTrue();
        assertThat(health.getMessage()).contains("Registration service is healthy");
    }
    
    @Test
    void testHeartbeat() {
        LOG.info("Testing heartbeat");
        
        // Register a module first
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
            .setServiceName("heartbeat-test")
            .setServiceId("heartbeat-101")
            .setHost("localhost")
            .setPort(6060)
            .setHealthEndpoint("/health")
            .build();

        getModuleRegistrationService()
            .registerModule(moduleInfo)
            .await().indefinitely();
        
        // Send heartbeat
        HeartbeatRequest heartbeat = HeartbeatRequest.newBuilder()
            .setServiceId("heartbeat-101")
            .setStatus("healthy")
            .build();
            
        HeartbeatResponse response = getModuleRegistrationService()
            .heartbeat(heartbeat)
            .await().indefinitely();
            
        assertThat(response.getAcknowledged()).isTrue();
    }
}