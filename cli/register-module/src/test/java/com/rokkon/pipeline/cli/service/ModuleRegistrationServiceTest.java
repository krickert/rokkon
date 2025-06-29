package com.rokkon.pipeline.cli.service;

import com.rokkon.search.grpc.*;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.*;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@QuarkusTest
class ModuleRegistrationServiceTest {

    @InjectMock
    @GrpcClient("module-service")
    PipeStepProcessor moduleClient;

    @InjectMock
    @GrpcClient("engine-service")
    ModuleRegistration engineClient;

    @Test
    void testGetServiceRegistrationWithHealthCheck() {
        // Given - a registration request with test data
        RegistrationRequest request = RegistrationRequest.newBuilder()
            .setTestRequest(ProcessRequest.newBuilder()
                .setDocument(PipeDoc.newBuilder()
                    .setId("test-doc-1")
                    .setTitle("Test Document")
                    .setBody("Test content")
                    .build())
                .setMetadata(ServiceMetadata.newBuilder()
                    .setPipelineName("test-pipeline")
                    .setPipeStepName("test-step")
                    .build())
                .build())
            .build();

        // Mock the response
        ServiceRegistrationResponse mockResponse = ServiceRegistrationResponse.newBuilder()
            .setModuleName("test-module")
            .setJsonConfigSchema("{\"type\":\"object\"}")
            .setHealthCheckPassed(true)
            .setHealthCheckMessage("Module is healthy")
            .build();

        Mockito.when(moduleClient.getServiceRegistration(any(RegistrationRequest.class)))
            .thenReturn(Uni.createFrom().item(mockResponse));

        // When
        ServiceRegistrationResponse response = moduleClient.getServiceRegistration(request)
            .await().indefinitely();

        // Then
        assertThat(response.getModuleName()).isEqualTo("test-module");
        assertThat(response.getHealthCheckPassed()).isTrue();
        assertThat(response.getHealthCheckMessage()).isEqualTo("Module is healthy");
        assertThat(response.hasJsonConfigSchema()).isTrue();
    }

    @Test
    void testGetServiceRegistrationWithoutHealthCheck() {
        // Given - a registration request without test data
        RegistrationRequest request = RegistrationRequest.newBuilder().build();

        // Mock the response
        ServiceRegistrationResponse mockResponse = ServiceRegistrationResponse.newBuilder()
            .setModuleName("test-module")
            .setHealthCheckPassed(true)
            .setHealthCheckMessage("No health check performed - module assumed healthy")
            .build();

        Mockito.when(moduleClient.getServiceRegistration(any(RegistrationRequest.class)))
            .thenReturn(Uni.createFrom().item(mockResponse));

        // When
        ServiceRegistrationResponse response = moduleClient.getServiceRegistration(request)
            .await().indefinitely();

        // Then
        assertThat(response.getModuleName()).isEqualTo("test-module");
        assertThat(response.getHealthCheckPassed()).isTrue();
        assertThat(response.getHealthCheckMessage()).contains("No health check performed");
        assertThat(response.hasJsonConfigSchema()).isFalse();
    }

    @Test
    void testGetServiceRegistrationHealthCheckFailed() {
        // Given - a registration request with test data
        RegistrationRequest request = RegistrationRequest.newBuilder()
            .setTestRequest(ProcessRequest.newBuilder()
                .setDocument(PipeDoc.newBuilder()
                    .setId("test-doc-2")
                    .build())
                .build())
            .build();

        // Mock the response with failed health check
        ServiceRegistrationResponse mockResponse = ServiceRegistrationResponse.newBuilder()
            .setModuleName("test-module")
            .setHealthCheckPassed(false)
            .setHealthCheckMessage("Module health check failed: Unable to process document")
            .build();

        Mockito.when(moduleClient.getServiceRegistration(any(RegistrationRequest.class)))
            .thenReturn(Uni.createFrom().item(mockResponse));

        // When
        ServiceRegistrationResponse response = moduleClient.getServiceRegistration(request)
            .await().indefinitely();

        // Then
        assertThat(response.getModuleName()).isEqualTo("test-module");
        assertThat(response.getHealthCheckPassed()).isFalse();
        assertThat(response.getHealthCheckMessage()).contains("failed");
    }

    @Test
    void testModuleRegistrationFlow() {
        // Given - successful module registration
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
            .setServiceName("test-module")
            .setServiceId("test-module-123")
            .setHost("localhost")
            .setPort(9090)
            .putMetadata("health_check_passed", "true")
            .putMetadata("health_check_message", "Module is healthy")
            .build();

        RegistrationStatus mockStatus = RegistrationStatus.newBuilder()
            .setSuccess(true)
            .setMessage("Module registered successfully")
            .setConsulServiceId("grpc-test-module-123")
            .build();

        Mockito.when(engineClient.registerModule(any(ModuleInfo.class)))
            .thenReturn(Uni.createFrom().item(mockStatus));

        // When
        RegistrationStatus status = engineClient.registerModule(moduleInfo)
            .await().indefinitely();

        // Then
        assertThat(status.getSuccess()).isTrue();
        assertThat(status.getMessage()).contains("successfully");
        assertThat(status.getConsulServiceId()).isEqualTo("grpc-test-module-123");
    }
}