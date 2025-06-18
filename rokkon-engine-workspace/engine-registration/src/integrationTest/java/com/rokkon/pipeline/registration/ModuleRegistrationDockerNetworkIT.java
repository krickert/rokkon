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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that sets up a proper Docker network with Consul
 * to test real health checks and service discovery.
 * 
 * This test is marked as @Disabled by default because it requires:
 * 1. More setup time (starts Consul container)
 * 2. More complex networking
 * 
 * Enable it for full integration testing.
 */
@QuarkusIntegrationTest
@Testcontainers
@Disabled("Enable for full integration testing with Consul")
public class ModuleRegistrationDockerNetworkIT {

    private static final int MODULE_GRPC_PORT = 49093;
    private static final int REGISTRATION_GRPC_PORT = 9090;
    private static final int CONSUL_PORT = 8500;
    
    // Create a shared network for all containers
    static Network network = Network.newNetwork();

    @Container
    static GenericContainer<?> consulContainer = new GenericContainer<>("consul:1.16")
            .withNetwork(network)
            .withNetworkAliases("consul")
            .withExposedPorts(CONSUL_PORT)
            .withCommand("agent", "-dev", "-client=0.0.0.0")
            .waitingFor(Wait.forHttp("/v1/status/leader").forPort(CONSUL_PORT));

    @Container
    static GenericContainer<?> testModuleContainer = new GenericContainer<>("rokkon/test-module:1.0.0-SNAPSHOT")
            .withNetwork(network)
            .withNetworkAliases("test-module")
            .withExposedPorts(MODULE_GRPC_PORT)
            .waitingFor(Wait.forLogMessage(".*Started gRPC server.*", 1))
            .withEnv("QUARKUS_LOG_LEVEL", "INFO")
            .dependsOn(consulContainer);

    // Note: In a real scenario, the registration service would also be in a container
    // For this test, we assume it's running locally with access to the exposed Consul port

    private ManagedChannel registrationChannel;
    private ManagedChannel moduleChannel;
    private ModuleRegistration moduleRegistrationService;
    private PipeStepProcessor testModuleService;

    @BeforeAll
    static void setupConsulConfig() {
        // Set Consul connection for the registration service
        // This assumes the registration service can be configured via system properties
        System.setProperty("consul.host", "localhost");
        System.setProperty("consul.port", String.valueOf(consulContainer.getMappedPort(CONSUL_PORT)));
    }

    @BeforeEach
    void setup() {
        // Connect to registration service (assumed to be running locally)
        registrationChannel = ManagedChannelBuilder
                .forAddress("localhost", REGISTRATION_GRPC_PORT)
                .usePlaintext()
                .build();
        moduleRegistrationService = new ModuleRegistrationClient("moduleRegistration", registrationChannel, 
                (serviceName, interceptors) -> interceptors);

        // Connect to test module container (for testing from outside)
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
    void testRegistrationWithConsulHealthChecks() throws InterruptedException {
        // Step 1: Get module metadata
        ServiceRegistrationData serviceData = testModuleService
                .getServiceRegistration(Empty.newBuilder().build())
                .await().indefinitely();
        
        // Step 2: Register module using the container network alias
        // This is important - Consul needs to reach the module using the network alias
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
                .setServiceName(serviceData.getModuleName())
                .setServiceId("test-module-consul-" + System.currentTimeMillis())
                .setHost("test-module") // Network alias, not localhost!
                .setPort(MODULE_GRPC_PORT) // Container internal port
                .setHealthEndpoint("/grpc.health.v1.Health/Check")
                .putMetadata("version", "1.0.0-SNAPSHOT")
                .putMetadata("configSchema", serviceData.getJsonConfigSchema())
                .build();

        RegistrationStatus response = moduleRegistrationService
                .registerModule(moduleInfo)
                .await().indefinitely();
        
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getConsulServiceId()).isNotEmpty();
        
        // Step 3: Wait for Consul health check to mark service as healthy
        // In real implementation, the registration service would set up
        // a gRPC health check in Consul
        Thread.sleep(5000); // Give Consul time to perform health checks
        
        // Step 4: Query Consul directly to verify service is registered and healthy
        // This would require adding Consul client to the test dependencies
        // For now, we just verify through our registration service
        
        ModuleList moduleList = moduleRegistrationService
                .listModules(Empty.newBuilder().build())
                .await().indefinitely();
        
        ModuleInfo registeredModule = moduleList.getModulesList().stream()
                .filter(m -> m.getServiceId().equals(moduleInfo.getServiceId()))
                .findFirst()
                .orElse(null);
                
        assertThat(registeredModule).isNotNull();
        // In a full implementation, we'd check the health status here
    }
    
    @Test
    void testServiceDiscoveryThroughConsul() {
        // This test would verify that other services can discover
        // the registered module through Consul's service discovery
        
        // 1. Register a module
        // 2. Use Consul's service discovery API to find it
        // 3. Connect to the discovered service
        // 4. Verify it works
        
        // Implementation would require Consul client library
    }
}