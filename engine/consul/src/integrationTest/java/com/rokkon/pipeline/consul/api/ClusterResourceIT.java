package com.rokkon.pipeline.consul.api;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
public class ClusterResourceIT {
    
    @Test
    void testCreateClusterViaRestIT() {
        String clusterName = "it-rest-test-cluster";
        
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(201)
            .body("valid", is(true))
            .body("errors.size()", is(0));
    }
    
    @Test
    void testFullClusterLifecycleIT() {
        String clusterName = "it-lifecycle-cluster";
        
        // Create cluster
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(201)
            .body("valid", is(true));
            
        // Get cluster
        given()
            .when()
            .get("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(200)
            .body("name", is(clusterName))
            .body("metadata.status", is("active"));
            
        // Try duplicate
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(400)
            .body("valid", is(false))
            .body("errors[0]", containsString("already exists"));
            
        // Delete cluster
        given()
            .when()
            .delete("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(200)
            .body("valid", is(true));
            
        // Verify deletion
        given()
            .when()
            .get("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(404)
            .body("valid", is(false));
    }
}