package com.rokkon.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration test for the full Consul sidecar setup.
 * Tests that:
 * - Consul server is running
 * - Engine has its own Consul sidecar agent
 * - Test module has its own Consul sidecar agent
 * - Services can discover each other through Consul
 */
@QuarkusTest
@QuarkusTestResource(ConsulSidecarSetup.class)
public class ConsulSidecarIntegrationTest {
    
    @Inject
    @ConfigProperty(name = "consul.url")
    String consulUrl;
    
    @Inject
    @ConfigProperty(name = "rokkon.engine.dashboard.url")
    String engineDashboardUrl;
    
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testConsulServerIsRunning() {
        System.out.println("✅ Testing Consul server...");
        System.out.println("Consul URL: " + consulUrl);
        
        // Check Consul is healthy
        RestAssured.given()
                .when().get(consulUrl + "/v1/agent/self")
                .then()
                .statusCode(200)
                .body("Config.Datacenter", equalTo("dc1"));
        
        System.out.println("✅ Consul server is running!");
    }
    
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testConsulAgentsAreConnected() {
        System.out.println("✅ Testing Consul agents...");
        
        // Check that we have multiple nodes in the cluster
        RestAssured.given()
                .when().get(consulUrl + "/v1/catalog/nodes")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(1)); // Should have server + agent nodes
        
        System.out.println("✅ Consul agents are connected!");
    }
    
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testEngineIsAccessible() {
        System.out.println("✅ Testing engine accessibility...");
        System.out.println("Engine Dashboard URL: " + engineDashboardUrl);
        
        // Check engine health
        RestAssured.given()
                .when().get(engineDashboardUrl + "/q/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
        
        System.out.println("✅ Engine is accessible!");
    }
    
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testServicesAreRegisteredInConsul() throws InterruptedException {
        System.out.println("✅ Testing service registration...");
        
        // Wait a bit for services to register
        Thread.sleep(5000);
        
        // Check that services are registered
        RestAssured.given()
                .when().get(consulUrl + "/v1/catalog/services")
                .then()
                .statusCode(200)
                .body("$", hasKey("consul")) // Consul itself should be registered
                .body("size()", greaterThan(1)); // Should have more than just Consul
        
        // Print all registered services for debugging
        var response = RestAssured.given()
                .when().get(consulUrl + "/v1/catalog/services")
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        System.out.println("Registered services: " + response.asString());
        System.out.println("✅ Services are registered in Consul!");
    }
}