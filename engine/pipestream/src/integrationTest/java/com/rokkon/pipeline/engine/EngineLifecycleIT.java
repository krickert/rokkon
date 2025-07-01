package com.rokkon.pipeline.engine;

import com.rokkon.pipeline.engine.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;

@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
@TestProfile(EngineLifecycleIT.RandomClusterProfile.class)
class EngineLifecycleIT {
    
    public static class RandomClusterProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "rokkon.cluster.name", "test-cluster-" + UUID.randomUUID()
            );
        }
    }
    
    @Test
    void testEngineStartupCreatesCustomCluster() throws Exception {
        // Given: Consul is running (via ConsulTestResource)
        // And: A random cluster name is configured via TestProfile
        
        // When: Engine is started (automatically by QuarkusIntegrationTest)
        // Then: Ping endpoint works
        RestAssured.given()
            .when().get("/ping")
            .then()
            .statusCode(200)
            .body(is("pong"));
        
        // And: Health check shows UP
        RestAssured.given()
            .when().get("/q/health")
            .then()
            .statusCode(200);
        
        // And: Custom cluster should be created in Consul
        // Note: We need to get the Consul port from system properties
        String consulHost = System.getProperty("consul.host", "localhost");
        String consulPort = System.getProperty("consul.port", "8500");
        String clusterName = System.getProperty("rokkon.cluster.name");
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://" + consulHost + ":" + consulPort + "/v1/kv/rokkon-clusters/" + clusterName + "/metadata?raw"))
            .GET()
            .build();
            
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"name\":\"" + clusterName + "\"");
        assertThat(response.body()).contains("\"status\":\"active\"");
        assertThat(response.body()).contains("\"autoCreated\":\"true\"");
        assertThat(response.body()).contains("\"createdBy\":\"rokkon-engine\"");
    }
    
    @Test
    void testHealthEndpointShowsGrpcService() {
        RestAssured.given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .body("status", is("UP"))
            .body("checks.size()", is(1))
            .body("checks[0].name", is("gRPC Server"))
            .body("checks[0].status", is("UP"));
    }
}