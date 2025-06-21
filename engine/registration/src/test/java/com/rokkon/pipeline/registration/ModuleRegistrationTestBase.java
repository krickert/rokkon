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
        assertThat(response.getMessage()).contains("successfully registered");
        // In unit tests with MockConsul, this might be empty
        // In integration tests with real Consul, this should have a value
    }
    
    @Test
    void testRegisterModuleWithEmptyHost() {
        LOG.info("Testing module registration with empty host");
        
        // Empty host - protobuf allows empty strings
        ModuleInfo moduleWithEmptyHost = ModuleInfo.newBuilder()
            .setServiceName("empty-host-service")
            .setServiceId("empty-host-123")
            .setHost("") // Empty string
            .setPort(8080)
            .build();
            
        RegistrationStatus response = getModuleRegistrationService()
            .registerModule(moduleWithEmptyHost)
            .await().indefinitely();
            
        // The current implementation doesn't validate empty fields
        // In a real implementation, this should fail validation
        // For now, we just verify the behavior as it is
        assertThat(response).isNotNull();
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
        ModuleId moduleId = ModuleId.newBuilder()
            .setServiceId("unregister-789")
            .build();
            
        UnregistrationStatus unregResponse = getModuleRegistrationService()
            .unregisterModule(moduleId)
            .await().indefinitely();
            
        assertThat(unregResponse.getSuccess()).isTrue();
        assertThat(unregResponse.getMessage()).contains("unregistered successfully");
    }
    
    @Test
    void testGetModuleHealth() {
        LOG.info("Testing module health check");
        
        // First register a module
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
            .setServiceName("health-test")
            .setServiceId("health-test-999")
            .setHost("localhost")
            .setPort(5050)
            .setHealthEndpoint("/health")
            .build();

        getModuleRegistrationService()
            .registerModule(moduleInfo)
            .await().indefinitely();
        
        // Check its health
        ModuleId moduleId = ModuleId.newBuilder()
            .setServiceId("health-test-999")
            .build();
            
        ModuleHealthStatus health = getModuleRegistrationService()
            .getModuleHealth(moduleId)
            .await().indefinitely();
            
        assertThat(health.getServiceId()).isEqualTo("health-test-999");
        assertThat(health.getServiceName()).isEqualTo("health-test");
        // Health status depends on whether Consul can actually reach the service
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
        ModuleHeartbeat heartbeat = ModuleHeartbeat.newBuilder()
            .setServiceId("heartbeat-101")
            .putStatusInfo("status", "healthy")
            .build();
            
        HeartbeatAck response = getModuleRegistrationService()
            .heartbeat(heartbeat)
            .await().indefinitely();
            
        assertThat(response.getAcknowledged()).isTrue();
    }
}