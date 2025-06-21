package com.rokkon.pipeline.registration;

import com.google.protobuf.Empty;
import com.rokkon.search.grpc.*;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.PipeStepProcessorClient;
import com.rokkon.search.sdk.ServiceRegistrationData;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that uses a real module (test-module) running in Docker
 * to test the full registration flow.
 */
@QuarkusIntegrationTest
@Testcontainers
public class ModuleRegistrationWithRealModuleIT {

    private static final int MODULE_GRPC_PORT = 49093;
    private static final int REGISTRATION_GRPC_PORT = 9090;

    @Container
    static GenericContainer<?> testModuleContainer = new GenericContainer<>("rokkon/test-module:1.0.0-SNAPSHOT")
            .withExposedPorts(MODULE_GRPC_PORT)
            .waitingFor(Wait.forLogMessage(".*Started gRPC server.*", 1))
            .withEnv("QUARKUS_LOG_LEVEL", "INFO");

    private ManagedChannel registrationChannel;
    private ManagedChannel moduleChannel;
    private ModuleRegistration moduleRegistrationService;
    private PipeStepProcessor testModuleService;

    @BeforeEach
    void setup() {
        // Connect to registration service
        registrationChannel = ManagedChannelBuilder
                .forAddress("localhost", REGISTRATION_GRPC_PORT)
                .usePlaintext()
                .build();
        moduleRegistrationService = new ModuleRegistrationClient("moduleRegistration", registrationChannel, 
                (serviceName, interceptors) -> interceptors);

        // Connect to test module container
        String moduleHost = testModuleContainer.getHost();
        Integer modulePort = testModuleContainer.getMappedPort(MODULE_GRPC_PORT);
        
        moduleChannel = ManagedChannelBuilder
                .forAddress(moduleHost, modulePort)
                .usePlaintext()
                .build();
        testModuleService = new PipeStepProcessorClient("pipeStepProcessor", moduleChannel,
                (serviceName, interceptors) -> interceptors);
    }

    @AfterEach
    void cleanup() {
        if (registrationChannel != null) {
            registrationChannel.shutdown();
        }
        if (moduleChannel != null) {
            moduleChannel.shutdown();
        }
    }

    @Test
    void testFullRegistrationFlowWithRealModule() {
        // Step 1: Get module metadata from the real test-module container
        ServiceRegistrationData serviceData = testModuleService
                .getServiceRegistration(Empty.newBuilder().build())
                .await().indefinitely();
        
        assertThat(serviceData.getModuleName()).isEqualTo("test-processor");
        assertThat(serviceData.getJsonConfigSchema()).isNotEmpty();
        assertThat(serviceData.getJsonConfigSchema()).contains("mode");
        assertThat(serviceData.getJsonConfigSchema()).contains("addMetadata");
        
        // Step 2: Register the module with the registration service
        // For Consul registration, we use the container's network info
        String containerNetworkHost = testModuleContainer.getContainerInfo()
                .getNetworkSettings()
                .getNetworks()
                .values()
                .iterator()
                .next()
                .getIpAddress();
        
        // For external testing, we use the mapped port
        String externalHost = testModuleContainer.getHost();
        Integer externalPort = testModuleContainer.getMappedPort(MODULE_GRPC_PORT);
        
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
                .setServiceName(serviceData.getModuleName())
                .setServiceId("test-module-" + System.currentTimeMillis())
                .setHost(containerNetworkHost) // Consul will use this for health checks
                .setPort(MODULE_GRPC_PORT) // Container's internal port
                .setHealthEndpoint("/grpc.health.v1.Health/Check")
                .putMetadata("version", "1.0.0-SNAPSHOT")
                .putMetadata("type", "processor")
                .putMetadata("configSchema", serviceData.getJsonConfigSchema())
                .putMetadata("deploymentId", "docker-test")
                // Store external access info for clients outside the Docker network
                .putMetadata("externalHost", externalHost)
                .putMetadata("externalPort", String.valueOf(externalPort))
                .addTags("test")
                .addTags("docker")
                .build();

        RegistrationStatus response = moduleRegistrationService
                .registerModule(moduleInfo)
                .await().indefinitely();
        
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).contains("test-processor");
        assertThat(response.getMessage()).contains("successfully registered");
        
        // Step 3: Verify module appears in listings
        ModuleList moduleList = moduleRegistrationService
                .listModules(Empty.newBuilder().build())
                .await().indefinitely();
        
        boolean found = moduleList.getModulesList().stream()
                .anyMatch(m -> m.getServiceName().equals(serviceData.getModuleName()));
        assertThat(found).isTrue();
        
        // Step 4: Test that we can actually process data through the registered module
        com.rokkon.search.model.PipeDoc testDoc = com.rokkon.search.model.PipeDoc.newBuilder()
                .setId("test-doc-123")
                .setTitle("Integration Test Document")
                .setBody("This is a test document for integration testing")
                .build();
                
        com.rokkon.search.sdk.ProcessRequest processRequest = com.rokkon.search.sdk.ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .build();
                
        com.rokkon.search.sdk.ProcessResponse processResponse = testModuleService
                .processData(processRequest)
                .await().indefinitely();
                
        assertThat(processResponse.getSuccess()).isTrue();
        assertThat(processResponse.getProcessorLogsList()).isNotEmpty();
        // The test module logs multiple messages, check that it processed successfully
        assertThat(processResponse.getProcessorLogsList().toString())
                .contains("Document processed successfully");
    }
    
    @Test
    void testHealthCheckOnRegisteredModule() {
        // Register the module first
        String moduleHost = testModuleContainer.getHost();
        Integer modulePort = testModuleContainer.getMappedPort(MODULE_GRPC_PORT);
        
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
                .setServiceName("health-check-test")
                .setServiceId("health-test-" + System.currentTimeMillis())
                .setHost(moduleHost)
                .setPort(modulePort)
                .setHealthEndpoint("/grpc.health.v1.Health/Check")
                .build();

        RegistrationStatus regStatus = moduleRegistrationService
                .registerModule(moduleInfo)
                .await().indefinitely();
        
        assertThat(regStatus.getSuccess()).isTrue();
        
        // The registration service should have set up health checks in Consul
        // In a real scenario, Consul would be checking the health of the module
        // For now, we just verify the registration was successful
        
        // Send a heartbeat to simulate the module is alive
        ModuleHeartbeat heartbeat = ModuleHeartbeat.newBuilder()
                .setServiceId(moduleInfo.getServiceId())
                .putStatusInfo("status", "healthy")
                .build();
                
        HeartbeatAck heartbeatResponse = moduleRegistrationService
                .heartbeat(heartbeat)
                .await().indefinitely();
                
        assertThat(heartbeatResponse.getAcknowledged()).isTrue();
    }
}