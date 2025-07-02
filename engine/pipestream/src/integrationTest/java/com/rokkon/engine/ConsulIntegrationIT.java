package com.rokkon.engine;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import com.rokkon.engine.grpc.ConsulTestResource;

@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
public class ConsulIntegrationIT {

    @Test
    void testConsulIsRunning() {
        // Test that the application started successfully with Consul
        RestAssured.when()
            .get("/q/health")
            .then()
            .statusCode(200);
    }
    
    @Test
    void testConsulReadiness() {
        // Test that Consul connectivity is established
        RestAssured.when()
            .get("/q/health/ready")
            .then()
            .statusCode(200);
    }
}
