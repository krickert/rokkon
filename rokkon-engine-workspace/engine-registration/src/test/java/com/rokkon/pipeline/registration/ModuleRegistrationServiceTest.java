package com.rokkon.pipeline.registration;

import com.google.protobuf.Empty;
import com.rokkon.search.grpc.*;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ModuleRegistrationServiceTest {

    @GrpcClient
    ModuleRegistration moduleRegistrationClient;

    @Test
    void testRegisterModule() {
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
            .setServiceName("echo-service")
            .setServiceId("echo-123")
            .setHost("localhost")
            .setPort(9090)
            .setHealthEndpoint("/health")
            .putMetadata("version", "1.0.0")
            .putMetadata("type", "processor")
            .addTags("echo")
            .addTags("test")
            .build();

        // Simple blocking test for now
        RegistrationStatus response = moduleRegistrationClient.registerModule(moduleInfo).await().indefinitely();
        
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).contains("echo-service");
        assertThat(response.getConsulServiceId()).isNotEmpty();
    }

    @Test 
    void testListModules() {
        // First register a module
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
            .setServiceName("test-service")
            .setServiceId("test-456")
            .setHost("localhost")
            .setPort(8080)
            .build();

        moduleRegistrationClient.registerModule(moduleInfo).await().indefinitely();

        // Then list all modules
        ModuleList moduleList = moduleRegistrationClient.listModules(Empty.newBuilder().build()).await().indefinitely();
            
        assertThat(moduleList.getModulesCount()).isGreaterThan(0);
        assertThat(moduleList.getAsOf()).isNotNull();
        
        // Find our test module
        boolean found = moduleList.getModulesList().stream()
            .anyMatch(module -> "test-service".equals(module.getServiceName()));
        assertThat(found).isTrue();
    }

    @Test
    void testUnregisterModule() {
        // First register a module
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
            .setServiceName("temp-service")
            .setServiceId("temp-789")
            .setHost("localhost")
            .setPort(7070)
            .build();

        moduleRegistrationClient.registerModule(moduleInfo).await().indefinitely();

        // Then unregister it
        ModuleId moduleId = ModuleId.newBuilder()
            .setServiceId("temp-789")
            .build();

        UnregistrationStatus response = moduleRegistrationClient.unregisterModule(moduleId).await().indefinitely();
            
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).contains("successfully");
    }
}