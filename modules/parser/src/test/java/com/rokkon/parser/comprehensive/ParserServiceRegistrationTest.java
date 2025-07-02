package com.rokkon.parser.comprehensive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.*;
import com.rokkon.test.registration.ModuleRegistrationTester;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive test for parser service registration functionality.
 * Uses the standardized ModuleRegistrationTester for consistent validation.
 */
@QuarkusTest
public class ParserServiceRegistrationTest {

    private static final Logger LOG = Logger.getLogger(ParserServiceRegistrationTest.class);

    @GrpcClient
    PipeStepProcessor parserService;

    // Use direct instantiation pattern like chunker module (works better than CDI injection)
    private final ModuleRegistrationTester moduleRegistrationTester = new ModuleRegistrationTester();

    @Test
    public void testParserServiceRegistrationWithoutHealthCheck() {
        LOG.info("=== Testing Parser Service Registration Without Health Check ===");

        // Get the registration without test request
        RegistrationRequest request = RegistrationRequest.newBuilder().build();
        ServiceRegistrationResponse registration = parserService.getServiceRegistration(request)
                .await().indefinitely();

        // Basic registration validation
        assertThat(registration).isNotNull();
        assertThat(registration.getModuleName()).isEqualTo("parser");
        assertThat(registration.getHealthCheckPassed()).isTrue();
        assertThat(registration.getHealthCheckMessage()).contains("No health check performed");

        // JSON schema validation
        String schema = registration.getJsonConfigSchema();
        assertThat(schema).isNotNull();
        assertThat(schema.trim()).isNotEmpty();
        assertThat(schema.length()).isGreaterThan(50);

        // Verify schema contains expected configuration keys
        String[] expectedConfigKeys = {
            "extractMetadata", 
            "maxContentLength", 
            "enableTitleExtraction", 
            "enableGeoTopicParser"
        };

        for (String expectedKey : expectedConfigKeys) {
            assertThat(schema).contains(expectedKey);
        }

        // Verify JSON schema structure
        assertThat(schema).contains("$schema");
        assertThat(schema).contains("properties");
        assertThat(schema).contains("type");

        LOG.info("✅ Parser service registration test without health check passed!");
    }

    @Test
    public void testParserServiceRegistrationWithHealthCheck() {
        LOG.info("=== Testing Parser Service Registration With Health Check ===");

        // Create a test document with blob data
        ByteString testContent = ByteString.copyFromUtf8("This is a test document for health check");
        Blob testBlob = Blob.newBuilder()
                .setData(testContent)
                .setFilename("health-check.txt")
                .build();

        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("health-check-doc")
                .setBlob(testBlob)
                .build();

        ProcessRequest processRequest = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("health-check")
                        .setPipeStepName("parser-health")
                        .build())
                .setConfig(ProcessConfiguration.newBuilder()
                        .putConfigParams("extractMetadata", "true")
                        .build())
                .build();

        // Get registration with health check
        RegistrationRequest request = RegistrationRequest.newBuilder()
                .setTestRequest(processRequest)
                .build();

        ServiceRegistrationResponse registration = parserService.getServiceRegistration(request)
                .await().indefinitely();

        // Validate registration
        assertThat(registration).isNotNull();
        assertThat(registration.getModuleName()).isEqualTo("parser");
        assertThat(registration.getHealthCheckPassed()).isTrue();
        assertThat(registration.getHealthCheckMessage()).contains("successfully processed test document");
        assertThat(registration.hasJsonConfigSchema()).isTrue();

        LOG.info("✅ Parser service registration test with health check passed!");
    }

    @Test
    public void testParserServiceRegistrationSchemaValidation() {
        LOG.info("=== Testing Parser Service Registration Schema Validation ===");

        // Get the registration
        RegistrationRequest request = RegistrationRequest.newBuilder().build();
        var registration = parserService.getServiceRegistration(request)
                .await().indefinitely();

        assertThat(registration.getModuleName()).isEqualTo("parser");

        // Verify JSON schema is present and comprehensive
        String schema = registration.getJsonConfigSchema();
        assertThat(schema).isNotNull();
        assertThat(schema.trim()).isNotEmpty();
        assertThat(schema.length()).isGreaterThan(500)
            .withFailMessage("Schema should be comprehensive (>500 chars), but was: " + schema.length());

        // Verify schema structure
        assertThat(schema).contains("$schema", "properties", "type");

        // Verify specific parser configuration options are in schema
        assertThat(schema).contains("extractMetadata");
        assertThat(schema).contains("maxContentLength");
        assertThat(schema).contains("enableTitleExtraction");
        assertThat(schema).contains("enableGeoTopicParser");

        // Validate the schema is valid JSON
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode schemaNode = objectMapper.readTree(schema);
            
            // Verify it's a valid JSON Schema
            assertThat(schemaNode.has("$schema")).isTrue();
            assertThat(schemaNode.get("$schema").asText()).contains("json-schema.org");
            assertThat(schemaNode.has("properties")).isTrue();
            assertThat(schemaNode.has("type")).isTrue();
            assertThat(schemaNode.get("type").asText()).isEqualTo("object");

            // Verify specific properties exist
            JsonNode properties = schemaNode.get("properties");
            assertThat(properties.has("parsingOptions")).isTrue();
            assertThat(properties.has("advancedOptions")).isTrue();
            assertThat(properties.has("contentTypeHandling")).isTrue();
            assertThat(properties.has("errorHandling")).isTrue();

            LOG.info("Schema is valid JSON and contains expected structure");
        } catch (Exception e) {
            throw new AssertionError("Schema should be valid JSON but failed to parse: " + e.getMessage(), e);
        }

        LOG.info("✅ Parser service registration schema validation passed!");
    }

    @Test
    public void testParserServiceRegistrationPerformance() {
        LOG.info("=== Testing Parser Service Registration Performance ===");

        // Test that registration is fast (should complete quickly)
        RegistrationRequest request = RegistrationRequest.newBuilder().build();
        long startTime = System.currentTimeMillis();

        var registration = parserService.getServiceRegistration(request)
                .await().indefinitely();

        long duration = System.currentTimeMillis() - startTime;

        assertThat(registration).isNotNull();
        assertThat(duration).isLessThan(1000)
            .withFailMessage("Service registration should complete quickly (< 1s), took: %d ms", duration);

        LOG.infof("Registration completed in: %d ms", duration);
        LOG.info("✅ Parser service registration performance test passed!");
    }

    @Test 
    public void testParserServiceRegistrationMultipleCalls() {
        LOG.info("=== Testing Parser Service Registration Multiple Calls ===");

        // Test that multiple calls return consistent results
        RegistrationRequest request = RegistrationRequest.newBuilder().build();
        
        var registration1 = parserService.getServiceRegistration(request)
                .await().indefinitely();
        var registration2 = parserService.getServiceRegistration(request)
                .await().indefinitely();
        var registration3 = parserService.getServiceRegistration(request)
                .await().indefinitely();

        // All registrations should be identical
        assertThat(registration1.getModuleName()).isEqualTo(registration2.getModuleName());
        assertThat(registration2.getModuleName()).isEqualTo(registration3.getModuleName());

        assertThat(registration1.getJsonConfigSchema()).isEqualTo(registration2.getJsonConfigSchema());
        assertThat(registration2.getJsonConfigSchema()).isEqualTo(registration3.getJsonConfigSchema());

        LOG.info("All three registration calls returned identical results");
        LOG.info("✅ Parser service registration consistency test passed!");
    }
}
