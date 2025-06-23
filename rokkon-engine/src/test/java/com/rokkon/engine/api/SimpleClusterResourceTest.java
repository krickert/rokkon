package com.rokkon.engine.api;

import com.rokkon.pipeline.consul.service.ClusterService;
import com.rokkon.pipeline.validation.ValidationResult;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

/**
 * Simple test without Consul to verify REST endpoint migration works
 */
@QuarkusTest
class SimpleClusterResourceTest {

    @InjectMock
    ClusterService clusterService;

    @Test
    void testCreateClusterViaRest() {
        String clusterName = "rest-test-cluster";

        // Setup mock response - @InjectMock creates a Mockito mock automatically
        ValidationResult validResult = new ValidationResult(true, null, null);
        org.mockito.Mockito.when(clusterService.createCluster(clusterName))
            .thenReturn(Uni.createFrom().item(validResult));

        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(201)
            .body("valid", is(true))
            .body("errors.size()", is(0));
    }
}