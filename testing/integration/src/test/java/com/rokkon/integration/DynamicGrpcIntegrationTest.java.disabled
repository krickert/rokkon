package com.rokkon.integration;

import com.rokkon.test.containers.SharedNetworkManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integration test for Dynamic gRPC functionality.
 * Tests that the engine can discover and call modules via Consul service discovery.
 */
@Testcontainers
@QuarkusTest
@QuarkusTestResource(DynamicGrpcTestResource.class)
public class DynamicGrpcIntegrationTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(DynamicGrpcIntegrationTest.class);
    
    @Test
    void testDynamicGrpcServiceDiscovery() throws Exception {
        // Wait for everything to stabilize
        Thread.sleep(5000);
        
        // 1. Verify Consul is healthy
        given()
            .when()
            .get("http://localhost:8500/v1/status/leader")
            .then()
            .statusCode(200);
        
        // 2. Verify engine is healthy
        given()
            .when()
            .get("http://localhost:8080/q/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
        
        // 3. Create a simple test pipeline that uses the test module
        String pipelineConfig = """
            {
                "pipelineId": "test-dynamic-grpc",
                "description": "Test pipeline for dynamic gRPC",
                "steps": [
                    {
                        "stepName": "test-processor",
                        "stepType": "PIPELINE",
                        "processorInfo": {
                            "grpcServiceName": "test-module"
                        }
                    }
                ]
            }
            """;
        
        // Deploy the pipeline
        given()
            .contentType("application/json")
            .body(pipelineConfig)
            .when()
            .post("http://localhost:8080/api/v1/clusters/default/pipelines/test-dynamic-grpc")
            .then()
            .statusCode(201);
        
        // 4. Send test data through the pipeline
        String testData = """
            {
                "content": "Test data for dynamic gRPC",
                "metadata": {
                    "source": "integration-test"
                }
            }
            """;
        
        // Process data through the pipeline
        var response = given()
            .contentType("application/json")
            .body(testData)
            .when()
            .post("http://localhost:8080/api/v1/process/test-dynamic-grpc")
            .then()
            .statusCode(200)
            .body("processed", equalTo(true))
            .body("moduleName", equalTo("test-module"))
            .extract()
            .response();
        
        LOG.info("Pipeline processing response: {}", response.asString());
        
        // 5. Verify the module was called via dynamic gRPC
        // The test module should have processed the data and returned a response
        assertThat(response.jsonPath().getString("processingResult"))
            .contains("Processed by test-module");
    }
    
    @Test
    void testDynamicGrpcLoadBalancing() throws Exception {
        // This test would verify load balancing across multiple instances
        // For now, we'll just verify that the service discovery finds the module
        
        Thread.sleep(3000);
        
        // Check that the test module is registered in Consul
        var services = given()
            .when()
            .get("http://localhost:8500/v1/catalog/services")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();
        
        LOG.info("Registered services: {}", services);
        assertThat(services).contains("test-module");
        
        // Get service instances
        var instances = given()
            .when()
            .get("http://localhost:8500/v1/health/service/test-module")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList("$");
        
        assertThat(instances).isNotEmpty();
        LOG.info("Found {} instances of test-module", instances.size());
    }
}