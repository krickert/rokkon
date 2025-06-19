package com.rokkon.pipeline.engine.registration;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import com.rokkon.pipeline.engine.service.ModuleRegistrationService;
import com.rokkon.pipeline.engine.model.ModuleRegistrationRequest;
import com.rokkon.pipeline.engine.model.ModuleRegistrationResponse;
import com.rokkon.test.containers.ModuleContainerResource;
import com.rokkon.test.containers.TestModuleContainerResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that verifies module registration works with containers
 * on a shared Docker network. This test demonstrates that:
 * 1. Modules can be registered with Consul using network-internal addresses
 * 2. Consul can perform gRPC health checks on the registered modules
 * 3. The registration service properly handles container networking
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusTestResource(ConsulTestResource.class)
@QuarkusTestResource(TestModuleContainerResource.class)
public class ModuleRegistrationServiceNetworkIT {
    
    @Inject
    ModuleRegistrationService registrationService;
    
    @ConfigProperty(name = "test.module.container.network.alias", defaultValue = "test-module")
    String moduleNetworkAlias;
    
    @ConfigProperty(name = "test.module.container.internal.grpc.port", defaultValue = "9090")
    int moduleGrpcPort;
    
    @ConfigProperty(name = "quarkus.consul-config.agent.host")
    String consulHost;
    
    @ConfigProperty(name = "quarkus.consul-config.agent.port")
    int consulPort;
    
    @Test
    void testModuleRegistrationWithSharedNetwork() throws Exception {
        // Register the module using its network alias (internal address)
        var request = new ModuleRegistrationRequest(
            "test-module-network-aware",  // moduleName
            moduleNetworkAlias,           // host - Use network alias instead of localhost
            moduleGrpcPort,               // port
            "default",                    // clusterName
            "grpc",                       // serviceType
            new java.util.HashMap<>() {{  // metadata
                put("network", "shared");
                put("consul.host", moduleNetworkAlias);  // Tell Consul to use network alias
                put("consul.port", String.valueOf(moduleGrpcPort));
            }}
        );
        
        var response = registrationService.registerModule(request)
            .await().atMost(Duration.ofSeconds(30));
        
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isNull();
        
        // Wait for Consul to perform health checks
        Thread.sleep(7000); // Allow time for Consul health checks to run
        
        // Verify the service is registered and healthy in Consul
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest healthRequest = HttpRequest.newBuilder()
            .uri(URI.create(String.format("http://%s:%d/v1/health/service/test-module-network-aware?passing=true", 
                consulHost, consulPort)))
            .GET()
            .build();
        
        HttpResponse<String> healthResponse = httpClient.send(healthRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(healthResponse.statusCode()).isEqualTo(200);
        
        String body = healthResponse.body();
        System.out.println("Health check response: " + body);
        
        // Verify the service is marked as healthy
        assertThat(body).contains("test-module-network-aware");
        assertThat(body).contains("\"Status\":\"passing\"");
        assertThat(body).contains("\"Output\":\"gRPC check");
        assertThat(body).doesNotContain("\"Status\":\"critical\"");
        assertThat(body).doesNotContain("\"Status\":\"warning\"");
        
        // The fact that we got a passing health check proves:
        // 1. The module was registered with the correct network address
        // 2. Consul could reach the module via the shared network
        // 3. The gRPC health check succeeded
    }
    
    @Test
    void testModuleRegistrationWithExternalAddress() throws Exception {
        // Also test that we can register with external addresses for development
        var request = new ModuleRegistrationRequest(
            "test-module-external",                                               // moduleName
            "localhost",                                                          // host
            Integer.parseInt(System.getProperty("test.module.container.grpc.port")), // port
            "default",                                                            // clusterName
            "grpc",                                                               // serviceType
            new java.util.HashMap<>()                                           // metadata
        );
        
        var response = registrationService.registerModule(request)
            .await().atMost(Duration.ofSeconds(30));
        
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isNull();
        
        // This test demonstrates flexibility - we can register with either:
        // - Internal network addresses (for production/container environments)
        // - External localhost addresses (for development/testing)
    }
}