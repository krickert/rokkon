package com.rokkon.pipeline.consul.api;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
class ClusterResourceTest {
    private static final Logger LOG = Logger.getLogger(ClusterResourceTest.class);
    
    @Test
    void testCreateClusterViaRest() {
        String clusterName = "rest-test-cluster";
        
        LOG.infof("Testing cluster creation via REST: %s", clusterName);
        
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
    void testGetClusterViaRest() {
        String clusterName = "rest-get-cluster";
        
        // First create cluster
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(201);
            
        // Then get it
        given()
            .when()
            .get("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(200)
            .body("name", is(clusterName))
            .body("metadata.status", is("active"));
    }
    
    @Test
    void testGetNonExistentCluster() {
        given()
            .when()
            .get("/api/v1/clusters/non-existent-cluster")
            .then()
            .statusCode(404)
            .body("valid", is(false))
            .body("errors[0]", containsString("not found"));
    }
    
    @Test
    void testDeleteClusterViaRest() {
        String clusterName = "rest-delete-cluster";
        
        // Create cluster
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(201);
            
        // Delete it
        given()
            .when()
            .delete("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(200)
            .body("valid", is(true));
            
        // Verify it's gone
        given()
            .when()
            .get("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(404);
    }
    
    @Test
    void testCreateDuplicateCluster() {
        String clusterName = "rest-duplicate-cluster";
        
        // Create first
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(201);
            
        // Try to create duplicate
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(400)
            .body("valid", is(false))
            .body("errors[0]", containsString("already exists"));
    }
}