package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Unit test for ModuleWhitelistService using Quarkus dev mode.
 * Uses real Consul instance via Testcontainers.
 * No mocking - all real services and validation.
 */
@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
class ModuleWhitelistServiceUnitTest extends ModuleWhitelistServiceTestBase {
    
    @Inject
    ModuleWhitelistService whitelistService;
    
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String consulHost;
    
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    String consulPort;
    
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    @Override
    protected String getConsulHost() {
        return consulHost;
    }
    
    @Override
    protected String getConsulPort() {
        return consulPort;
    }
    
    @Override
    protected ModuleWhitelistService getWhitelistService() {
        return whitelistService;
    }
    
    @Override
    protected Uni<Boolean> registerModuleInConsul(String moduleName, String host, int port) {
        // Register module in Consul as a gRPC service
        String serviceJson = String.format("""
            {
                "ID": "%s-unit-test",
                "Name": "%s",
                "Tags": ["grpc", "test"],
                "Address": "%s",
                "Port": %d,
                "Check": {
                    "Name": "Module Health Check",
                    "GRPC": "%s:%d",
                    "GRPCUseTLS": false,
                    "Interval": "30s",
                    "Timeout": "5s"
                }
            }
            """, moduleName, moduleName, host, port, host, port);
        
        String url = String.format("http://%s:%s/v1/agent/service/register", consulHost, consulPort);
        System.out.println("Registering module in Consul at: " + url);
        System.out.println("Consul host: " + consulHost + ", port: " + consulPort);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(serviceJson))
            .build();
        
        return Uni.createFrom().completionStage(
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        )
        .map(response -> {
            System.out.println("Consul registration response: " + response.statusCode());
            if (response.statusCode() != 200) {
                System.err.println("Consul registration failed: " + response.body());
            }
            return response.statusCode() == 200;
        })
        .onFailure().invoke(throwable -> {
            System.err.println("Failed to register module in Consul: " + throwable.getMessage());
            throwable.printStackTrace();
        });
    }
}