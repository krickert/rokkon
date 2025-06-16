package com.rokkon.search.engine.registration;

import com.rokkon.modules.echo.EchoService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Tests for JSON schema validation during module registration.
 * 
 * Tests various scenarios:
 * 1. Module with no schema (should pass)
 * 2. Module with valid JSON Schema v7 (should pass)
 * 3. Module with invalid JSON Schema (should fail)
 * 
 * Schema validation only checks JSON Schema v7 spec compliance - 
 * no forced fields or structure requirements.
 */
@QuarkusTest
@Disabled("Requires module registration services, gRPC test servers, and schema validation infrastructure")
class ModuleSchemaValidationTest {
    
    private List<Server> testServers = new ArrayList<>();
    
    @BeforeEach
    void startTestServices() throws IOException {
        // Start basic echo service (no schema)
        Server echoServer = ServerBuilder.forPort(50060)
                .addService(new EchoService())
                .addService(new io.grpc.services.HealthStatusManager().getHealthService())
                .build()
                .start();
        
        testServers.add(echoServer);
        
        // Start schema-enabled service
        Server schemaServer = ServerBuilder.forPort(50061)
                .addService(new TestModuleWithSchema())
                .addService(new io.grpc.services.HealthStatusManager().getHealthService())
                .build()
                .start();
        
        testServers.add(schemaServer);
        
        // Start invalid schema service
        Server invalidSchemaServer = ServerBuilder.forPort(50062)
                .addService(new TestModuleWithInvalidSchema())
                .addService(new io.grpc.services.HealthStatusManager().getHealthService())
                .build()
                .start();
        
        testServers.add(invalidSchemaServer);
        
        System.out.println("🚀 Test services started for schema validation tests");
    }
    
    @AfterEach
    void stopTestServers() {
        testServers.forEach(Server::shutdown);
        testServers.clear();
    }
    
    @Test
    void testModuleWithoutSchema_ShouldPass() {
        // Test echo service (no schema) - should pass validation
        ModuleRegistrationRequest request = new ModuleRegistrationRequest(
            "echo-no-schema",
            "echo-processor",
            "localhost",
            50060,
            "1.0.0",
            null, // No schema provided in request
            Map.of("test", "no-schema")
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/modules/register")
            .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("message", containsString("successfully registered"));
        
        System.out.println("✅ Module without schema registered successfully");
    }
    
    @Test
    void testModuleWithValidSchema_ShouldPass() {
        // Test module with valid JSON schema
        ModuleRegistrationRequest request = new ModuleRegistrationRequest(
            "test-module-valid-schema",
            "text-processor",
            "localhost",
            50061,
            "1.0.0",
            null, // Schema comes from module itself
            Map.of("test", "valid-schema")
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/modules/register")
            .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("message", containsString("successfully registered"));
        
        System.out.println("✅ Module with valid schema registered successfully");
    }
    
    @Test
    void testModuleWithInvalidSchema_ShouldFail() {
        // Test module with invalid JSON schema
        ModuleRegistrationRequest request = new ModuleRegistrationRequest(
            "test-module-invalid-schema",
            "broken-processor",
            "localhost",
            50062,
            "1.0.0",
            null, // Invalid schema comes from module
            Map.of("test", "invalid-schema")
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/modules/register")
            .then()
            .statusCode(400)
            .body("success", equalTo(false))
            .body("message", containsString("JSON schema validation failed"))
            .body("errors", hasSize(greaterThan(0)));
        
        System.out.println("✅ Module with invalid schema correctly rejected");
    }
    
    /**
     * Test module that provides a valid JSON schema
     */
    private static class TestModuleWithSchema extends com.rokkon.search.sdk.PipeStepProcessorGrpc.PipeStepProcessorImplBase {
        
        @Override
        public void getServiceRegistration(
                com.google.protobuf.Empty request,
                io.grpc.stub.StreamObserver<com.rokkon.search.sdk.ServiceRegistrationData> responseObserver) {
            
            // Valid JSON Schema v7 - any valid schema structure is allowed
            String validSchema = """
                {
                    "$schema": "https://json-schema.org/draft-07/schema#",
                    "type": "object",
                    "title": "Text Processor Configuration",
                    "properties": {
                        "language": {
                            "type": "string",
                            "enum": ["en", "es", "fr"],
                            "default": "en"
                        },
                        "maxLength": {
                            "type": "integer",
                            "minimum": 1,
                            "maximum": 10000,
                            "default": 1000
                        },
                        "enableSpellCheck": {
                            "type": "boolean",
                            "default": true
                        }
                    },
                    "additionalProperties": true
                }
                """;
            
            com.rokkon.search.sdk.ServiceRegistrationData response = 
                com.rokkon.search.sdk.ServiceRegistrationData.newBuilder()
                    .setModuleName("test-module-valid-schema")
                    .setJsonConfigSchema(validSchema)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        @Override
        public void processData(
                com.rokkon.search.sdk.ProcessRequest request,
                io.grpc.stub.StreamObserver<com.rokkon.search.sdk.ProcessResponse> responseObserver) {
            
            // Simple echo implementation for testing
            com.rokkon.search.sdk.ProcessResponse response = 
                com.rokkon.search.sdk.ProcessResponse.newBuilder()
                    .setSuccess(true)
                    .setOutputDoc(request.getDocument())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    /**
     * Test module that provides an invalid JSON schema
     */
    private static class TestModuleWithInvalidSchema extends com.rokkon.search.sdk.PipeStepProcessorGrpc.PipeStepProcessorImplBase {
        
        @Override
        public void getServiceRegistration(
                com.google.protobuf.Empty request,
                io.grpc.stub.StreamObserver<com.rokkon.search.sdk.ServiceRegistrationData> responseObserver) {
            
            // Invalid JSON Schema - malformed JSON
            String invalidSchema = """
                {
                    "type": "object",
                    "properties": {
                        "invalidProperty": {
                            "type": "unknownType"  // Invalid type
                        }
                    },
                    "invalid": syntax here  // Malformed JSON
                }
                """;
            
            com.rokkon.search.sdk.ServiceRegistrationData response = 
                com.rokkon.search.sdk.ServiceRegistrationData.newBuilder()
                    .setModuleName("test-module-invalid-schema")
                    .setJsonConfigSchema(invalidSchema)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        @Override
        public void processData(
                com.rokkon.search.sdk.ProcessRequest request,
                io.grpc.stub.StreamObserver<com.rokkon.search.sdk.ProcessResponse> responseObserver) {
            
            com.rokkon.search.sdk.ProcessResponse response = 
                com.rokkon.search.sdk.ProcessResponse.newBuilder()
                    .setSuccess(true)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}