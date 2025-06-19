package com.rokkon.pipeline.registration;

import com.google.protobuf.Empty;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ServiceRegistrationData;
import com.rokkon.search.grpc.*;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that tests the full module registration flow:
 * 1. Test module service is running (TestModuleService) 
 * 2. Registration service can get module metadata via getServiceRegistration
 * 3. Registration service registers module 
 * 4. Module appears in listings and health checks work
 */
@QuarkusTest
class ModuleRegistrationIntegrationTest {

    @GrpcClient
    ModuleRegistration moduleRegistrationClient;
    
    @GrpcClient  
    PipeStepProcessor testModuleClient;

    @Test
    void testFullRegistrationFlow() {
        // Step 1: Verify test module provides registration data
        ServiceRegistrationData serviceData = testModuleClient.getServiceRegistration(Empty.newBuilder().build()).await().indefinitely();
        assertThat(serviceData.getModuleName()).isEqualTo("test-module");
        assertThat(serviceData.getJsonConfigSchema()).contains("testParam");
        
        // Step 2: Register the test module via registration service  
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
            .setServiceName("test-module")
            .setServiceId("test-123")
            .setHost("localhost")
            .setPort(9090) // Same port as test is running on
            .setHealthEndpoint("/health")
            .putMetadata("version", "1.0.0")
            .putMetadata("type", "test") 
            .addTags("test")
            .build();

        RegistrationStatus response = moduleRegistrationClient.registerModule(moduleInfo).await().indefinitely();
        
        // Step 3: Verify registration was successful
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).contains("test-module");
        assertThat(response.getConsulServiceId()).isNotEmpty();
        assertThat(response.getRegisteredAt()).isNotNull();
        
        // Step 4: Verify module appears in list
        ModuleList moduleList = moduleRegistrationClient.listModules(Empty.newBuilder().build()).await().indefinitely();
        assertThat(moduleList.getModulesCount()).isGreaterThan(0);
        
        boolean found = moduleList.getModulesList().stream()
            .anyMatch(module -> "test-module".equals(module.getServiceName()) && 
                               "test-123".equals(module.getServiceId()));
        assertThat(found).isTrue();
        
        // Step 5: Test health check via registration service
        ModuleId moduleId = ModuleId.newBuilder()
            .setServiceId("test-123")
            .build();
            
        ModuleHealthStatus healthStatus = moduleRegistrationClient.getModuleHealth(moduleId).await().indefinitely();
        assertThat(healthStatus.getIsHealthy()).isTrue();
        assertThat(healthStatus.getServiceName()).isEqualTo("test-module");
    }
}