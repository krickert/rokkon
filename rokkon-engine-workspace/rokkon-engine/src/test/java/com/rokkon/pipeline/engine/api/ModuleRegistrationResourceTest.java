package com.rokkon.pipeline.engine.api;

import com.rokkon.pipeline.engine.model.ModuleRegistrationRequest;
import com.rokkon.pipeline.engine.model.ModuleRegistrationResponse;
import com.rokkon.pipeline.engine.service.ModuleRegistrationService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class ModuleRegistrationResourceTest {
    
    @InjectMock
    ModuleRegistrationService registrationService;
    
    @BeforeEach
    void setUp() {
        reset(registrationService);
    }
    
    @Test
    void testRegisterModuleSuccess() {
        // Given: Registration service returns success
        ModuleRegistrationResponse successResponse = ModuleRegistrationResponse.success(
            "test-module-123"
        );
        
        when(registrationService.registerModule(any()))
            .thenReturn(Uni.createFrom().item(successResponse));
        
        // When: Calling the registration endpoint
        given()
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", "localhost",
                "port", 9090,
                "clusterName", "test-cluster",
                "serviceData", Map.of("version", "1.0.0"),
                "metadata", Map.of("env", "test")
            ))
            .when().post("/api/v1/modules/register")
            .then()
            .statusCode(200)
            .body("success", is(true))
            .body("message", is("Module registered successfully"))
            .body("moduleId", is("test-module-123"))
            .body("consulServiceId", is("consul-service-123"))
            .body("registeredAt", notNullValue());
    }
    
    @Test
    void testRegisterModuleValidationFailure() {
        // Given: Registration service returns validation failure
        ModuleRegistrationResponse failureResponse = ModuleRegistrationResponse.failure(
            "Cluster 'test-cluster' does not exist"
        );
        
        when(registrationService.registerModule(any()))
            .thenReturn(Uni.createFrom().item(failureResponse));
        
        // When: Calling the registration endpoint
        given()
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", "localhost",
                "port", 9090,
                "clusterName", "test-cluster"
            ))
            .when().post("/api/v1/modules/register")
            .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", is("Cluster 'test-cluster' does not exist"))
            .body("moduleId", nullValue())
            .body("consulServiceId", nullValue());
    }
    
    @Test
    void testRegisterModuleMissingRequiredFields() {
        // When: Calling the registration endpoint with missing fields
        given()
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module"
                // Missing required fields: host, port, clusterName
            ))
            .when().post("/api/v1/modules/register")
            .then()
            .statusCode(400);
    }
    
    @Test
    void testRegisterModuleInternalError() {
        // Given: Registration service throws exception
        when(registrationService.registerModule(any()))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Database connection failed")));
        
        // When: Calling the registration endpoint
        given()
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", "localhost",
                "port", 9090,
                "clusterName", "test-cluster"
            ))
            .when().post("/api/v1/modules/register")
            .then()
            .statusCode(500)
            .body("success", is(false))
            .body("message", containsString("Internal error"))
            .body("message", containsString("Database connection failed"));
    }
}