package com.rokkon.engine.grpc;

import com.rokkon.search.grpc.ModuleInfo;
import com.rokkon.search.grpc.ModuleId;
import com.rokkon.search.grpc.RegistrationStatus;
import com.rokkon.search.grpc.UnregistrationStatus;
import com.rokkon.search.grpc.ModuleHealthStatus;
import com.rokkon.search.grpc.ModuleList;
import com.rokkon.search.grpc.MutinyModuleRegistrationGrpc;
import com.rokkon.pipeline.consul.service.DELETE_ME_GlobalModuleRegistryService;
import com.rokkon.pipeline.consul.service.DELETE_ME_GlobalModuleRegistryService.ModuleRegistration;
import com.rokkon.pipeline.consul.service.DELETE_ME_GlobalModuleRegistryService.ServiceHealthStatus;
import com.google.protobuf.Empty;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.consul.CheckStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ModuleRegistrationServiceImpl using gRPC client
 */
@QuarkusTest
class ModuleRegistrationServiceImplTest {

    @GrpcClient("moduleRegistration")
    MutinyModuleRegistrationGrpc.MutinyModuleRegistrationStub registrationClient;

    @InjectMock
    DELETE_ME_GlobalModuleRegistryService mockRegistryService;
    
    @BeforeEach
    void setup() {
        // Set up default mock behavior
        Mockito.when(mockRegistryService.registerModule(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyInt(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(),
            Mockito.anyString(),
            Mockito.anyInt(),
            Mockito.any()
        )).thenReturn(Uni.createFrom().item(createMockRegistration()));
    }

    @Test
    void testRegisterModule_Success() {
        // Given
        ModuleInfo request = ModuleInfo.newBuilder()
            .setServiceName("test-module")
            .setServiceId("test-module-123")
            .setHost("localhost")
            .setPort(8080)
            .putMetadata("version", "1.0.0")
            .build();

        // When
        RegistrationStatus response = registrationClient.registerModule(request)
            .await().indefinitely();

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).contains("successfully");
        assertThat(response.getConsulServiceId()).isEqualTo("test-module-123");
    }

    @Test
    void testRegisterModule_WithNoVersion_DefaultsToNoVersion() {
        // Given
        ModuleInfo request = ModuleInfo.newBuilder()
            .setServiceName("test-module")
            .setHost("localhost")
            .setPort(8080)
            .build();

        // Set up specific mock for this test
        Mockito.when(mockRegistryService.registerModule(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyInt(),
            Mockito.anyString(),
            Mockito.eq("NO_VERSION"),
            Mockito.any(),
            Mockito.anyString(),
            Mockito.anyInt(),
            Mockito.any()
        )).thenReturn(Uni.createFrom().item(createMockRegistration()));

        // When
        RegistrationStatus response = registrationClient.registerModule(request)
            .await().indefinitely();

        // Then
        assertThat(response.getSuccess()).isTrue();
        
        // Verify NO_VERSION was used
        Mockito.verify(mockRegistryService).registerModule(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyInt(),
            Mockito.anyString(),
            Mockito.eq("NO_VERSION"),
            Mockito.any(),
            Mockito.anyString(),
            Mockito.anyInt(),
            Mockito.any()
        );
    }

    @Test
    void testUnregisterModule_Success() {
        // Given
        ModuleId request = ModuleId.newBuilder()
            .setServiceId("test-module-123")
            .build();

        Mockito.when(mockRegistryService.deregisterModule("test-module-123"))
            .thenReturn(Uni.createFrom().item(true));

        // When
        UnregistrationStatus response = registrationClient.unregisterModule(request)
            .await().indefinitely();

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).contains("successfully");
    }

    @Test
    void testUnregisterModule_Failure() {
        // Given
        ModuleId request = ModuleId.newBuilder()
            .setServiceId("test-module-123")
            .build();

        Mockito.when(mockRegistryService.deregisterModule("test-module-123"))
            .thenReturn(Uni.createFrom().item(false));

        // When
        UnregistrationStatus response = registrationClient.unregisterModule(request)
            .await().indefinitely();

        // Then
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).contains("Failed");
    }

    @Test
    void testGetModuleHealth_Healthy() {
        // Given
        ModuleId request = ModuleId.newBuilder()
            .setServiceId("test-module-123")
            .build();

        ServiceHealthStatus healthStatus = new ServiceHealthStatus(
            createMockRegistration(),
            CheckStatus.PASSING,
            true
        );

        Mockito.when(mockRegistryService.getModuleHealthStatus("test-module-123"))
            .thenReturn(Uni.createFrom().item(healthStatus));

        // When
        ModuleHealthStatus response = registrationClient.getModuleHealth(request)
            .await().indefinitely();

        // Then
        assertThat(response.getIsHealthy()).isTrue();
        assertThat(response.getHealthDetails()).contains("All health checks passing");
        assertThat(response.getServiceId()).isEqualTo("test-module-123");
        assertThat(response.getServiceName()).isEqualTo("test-module");
    }

    @Test
    void testGetModuleHealth_Critical() {
        // Given
        ModuleId request = ModuleId.newBuilder()
            .setServiceId("test-module-123")
            .build();

        ServiceHealthStatus healthStatus = new ServiceHealthStatus(
            createMockRegistration(),
            CheckStatus.CRITICAL,
            true
        );

        Mockito.when(mockRegistryService.getModuleHealthStatus("test-module-123"))
            .thenReturn(Uni.createFrom().item(healthStatus));

        // When
        ModuleHealthStatus response = registrationClient.getModuleHealth(request)
            .await().indefinitely();

        // Then
        assertThat(response.getIsHealthy()).isFalse();
        assertThat(response.getHealthDetails()).contains("critical");
    }

    @Test
    void testGetModuleHealth_NotFound() {
        // Given
        ModuleId request = ModuleId.newBuilder()
            .setServiceId("test-module-123")
            .build();

        Mockito.when(mockRegistryService.getModuleHealthStatus("test-module-123"))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Module not found")));

        // When
        ModuleHealthStatus response = registrationClient.getModuleHealth(request)
            .await().indefinitely();

        // Then
        assertThat(response.getIsHealthy()).isFalse();
        assertThat(response.getServiceName()).isEqualTo("unknown");
        assertThat(response.getHealthDetails()).contains("Module not found");
    }

    @Test
    void testListModules() {
        // Given
        Set<ModuleRegistration> mockModules = Set.of(
            createMockRegistration("module1", "Module 1"),
            createMockRegistration("module2", "Module 2")
        );

        Mockito.when(mockRegistryService.listRegisteredModules())
            .thenReturn(Uni.createFrom().item(mockModules));

        // When
        ModuleList response = registrationClient.listModules(Empty.newBuilder().build())
            .await().indefinitely();

        // Then
        assertThat(response.getModulesCount()).isEqualTo(2);
        assertThat(response.getModulesList())
            .extracting(ModuleInfo::getServiceId)
            .containsExactlyInAnyOrder("module1", "module2");
    }

    private ModuleRegistration createMockRegistration() {
        return createMockRegistration("test-module-123", "test-module");
    }

    private ModuleRegistration createMockRegistration(String moduleId, String moduleName) {
        return new ModuleRegistration(
            moduleId,
            moduleName,
            moduleId,
            "localhost",
            8080,
            "GRPC",
            "1.0.0",
            Map.of(),
            System.currentTimeMillis(),
            "localhost",
            8080,
            null,
            true,
            null,
            null,
            null
        );
    }
}