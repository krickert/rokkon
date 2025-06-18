package com.rokkon.pipeline.registration;

import com.google.protobuf.Empty;
import com.rokkon.search.grpc.*;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.PipeStepProcessorClient;
import com.rokkon.search.sdk.ServiceRegistrationData;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full stack integration test with all services running in Docker containers
 * on the same network, simulating a real deployment scenario.
 */
@QuarkusIntegrationTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FullStackIntegrationIT {

    private static final int MODULE_GRPC_PORT = 49093;
    private static final int REGISTRATION_GRPC_PORT = 9090;
    private static final int CONSUL_PORT = 8500;
    
    // Create a shared network for all containers
    static Network network = Network.newNetwork();

    @Container
    static GenericContainer<?> consulContainer = new GenericContainer<>(DockerImageName.parse("hashicorp/consul:1.21.1"))
            .withNetwork(network)
            .withNetworkAliases("consul")
            .withExposedPorts(CONSUL_PORT)
            .withCommand("agent", "-dev", "-client=0.0.0.0")
            .waitingFor(Wait.forHttp("/v1/status/leader").forPort(CONSUL_PORT));

    @Container
    static GenericContainer<?> registrationContainer = new GenericContainer<>(DockerImageName.parse("krickert/engine-registration:1.0.0-SNAPSHOT"))
            .withNetwork(network)
            .withNetworkAliases("engine-registration")
            .withExposedPorts(REGISTRATION_GRPC_PORT)
            .withEnv("CONSUL_HOST", "consul")
            .withEnv("CONSUL_PORT", "8500")
            .withEnv("QUARKUS_LOG_LEVEL", "INFO")
            .waitingFor(Wait.forLogMessage(".*Started gRPC server.*", 1))
            .withStartupTimeout(Duration.ofSeconds(60))
            .dependsOn(consulContainer);

    @Container
    static GenericContainer<?> testModuleContainer = new GenericContainer<>(DockerImageName.parse("rokkon/test-module:1.0.0-SNAPSHOT"))
            .withNetwork(network)
            .withNetworkAliases("test-module")
            .withExposedPorts(MODULE_GRPC_PORT)
            .withEnv("QUARKUS_LOG_LEVEL", "INFO")
            .waitingFor(Wait.forLogMessage(".*Started gRPC server.*", 1))
            .dependsOn(consulContainer);

    private ManagedChannel registrationChannel;
    private ManagedChannel moduleChannel;
    private ModuleRegistration moduleRegistrationService;
    private PipeStepProcessor testModuleService;

    @BeforeEach
    void setup() {
        // Connect to registration service via exposed port
        String regHost = registrationContainer.getHost();
        Integer regPort = registrationContainer.getMappedPort(REGISTRATION_GRPC_PORT);
        
        registrationChannel = ManagedChannelBuilder
                .forAddress(regHost, regPort)
                .usePlaintext()
                .build();
        moduleRegistrationService = new ModuleRegistrationClient("moduleRegistration", registrationChannel, 
                (serviceName, interceptors) -> interceptors);

        // Connect to test module via exposed port
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
        if (registrationChannel != null && !registrationChannel.isShutdown()) {
            registrationChannel.shutdown();
        }
        if (moduleChannel != null && !moduleChannel.isShutdown()) {
            moduleChannel.shutdown();
        }
    }

    @Test
    @Order(1)
    void testRegistrationServiceIsRunning() {
        // Simple test to verify the registration service is accessible
        ModuleList moduleList = moduleRegistrationService
                .listModules(Empty.newBuilder().build())
                .await().indefinitely();
                
        // Should return an empty list or existing modules
        assertThat(moduleList).isNotNull();
        assertThat(moduleList.getAsOf()).isNotNull();
    }

    @Test
    @Order(2)
    void testRegisterModuleWithConsulIntegration() throws InterruptedException {
        // Step 1: Get module metadata
        ServiceRegistrationData serviceData = testModuleService
                .getServiceRegistration(Empty.newBuilder().build())
                .await().indefinitely();
        
        assertThat(serviceData.getModuleName()).isEqualTo("test-processor");
        
        // Step 2: Register module using container network aliases
        // This is key - Consul needs to reach the module via its network alias
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
                .setServiceName(serviceData.getModuleName())
                .setServiceId("test-module-fullstack-" + System.currentTimeMillis())
                .setHost("test-module") // Network alias, not localhost!
                .setPort(MODULE_GRPC_PORT) // Container's internal port
                .setHealthEndpoint("/grpc.health.v1.Health/Check")
                .putMetadata("version", "1.0.0-SNAPSHOT")
                .putMetadata("configSchema", serviceData.getJsonConfigSchema())
                .putMetadata("deploymentType", "docker")
                .addTags("test")
                .addTags("fullstack")
                .build();

        RegistrationStatus response = moduleRegistrationService
                .registerModule(moduleInfo)
                .await().indefinitely();
        
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).contains("successfully registered");
        assertThat(response.getConsulServiceId()).isNotEmpty();
        
        // Step 3: Give Consul time to perform health checks
        Thread.sleep(3000);
        
        // Step 4: Verify module appears in listings
        ModuleList moduleList = moduleRegistrationService
                .listModules(Empty.newBuilder().build())
                .await().indefinitely();
        
        ModuleInfo registeredModule = moduleList.getModulesList().stream()
                .filter(m -> m.getServiceId().equals(moduleInfo.getServiceId()))
                .findFirst()
                .orElse(null);
                
        assertThat(registeredModule).isNotNull();
        assertThat(registeredModule.getHost()).isEqualTo("test-module");
        
        // The module should be healthy since Consul can reach it on the same network
        // In a full implementation, we'd check the health status from Consul
    }

    @Test
    @Order(3)
    void testMultipleModuleRegistration() {
        // This test would register multiple modules and verify they can all be discovered
        // For now, we'll just register a second instance of test-module
        
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
                .setServiceName("test-processor-2")
                .setServiceId("test-module-2-" + System.currentTimeMillis())
                .setHost("test-module")
                .setPort(MODULE_GRPC_PORT)
                .setHealthEndpoint("/grpc.health.v1.Health/Check")
                .putMetadata("version", "1.0.0-SNAPSHOT")
                .putMetadata("instance", "2")
                .build();

        RegistrationStatus response = moduleRegistrationService
                .registerModule(moduleInfo)
                .await().indefinitely();
        
        assertThat(response.getSuccess()).isTrue();
        
        // List all modules
        ModuleList moduleList = moduleRegistrationService
                .listModules(Empty.newBuilder().build())
                .await().indefinitely();
                
        // Should have at least 2 modules registered
        assertThat(moduleList.getModulesCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(4)
    void testUnregisterModule() {
        // First, find a registered module
        ModuleList moduleList = moduleRegistrationService
                .listModules(Empty.newBuilder().build())
                .await().indefinitely();
                
        if (moduleList.getModulesCount() > 0) {
            ModuleInfo moduleToUnregister = moduleList.getModules(0);
            
            ModuleId moduleId = ModuleId.newBuilder()
                    .setServiceId(moduleToUnregister.getServiceId())
                    .build();
                    
            UnregistrationStatus response = moduleRegistrationService
                    .unregisterModule(moduleId)
                    .await().indefinitely();
                    
            assertThat(response.getSuccess()).isTrue();
            assertThat(response.getMessage()).contains("unregistered");
        }
    }
}