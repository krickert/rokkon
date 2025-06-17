package com.rokkon.parser.comprehensive;

import com.google.protobuf.Empty;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ServiceRegistrationData;
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
    public void testParserServiceRegistration() {
        LOG.info("=== Testing Parser Service Registration ===");

        // Get the registration
        Uni<ServiceRegistrationData> registrationUni = parserService.getServiceRegistration(Empty.getDefaultInstance());
        ServiceRegistrationData registration = registrationUni.await().indefinitely();

        // Implement comprehensive validation inline (same as ModuleRegistrationTester)
        LOG.info("Testing registration for module: parser-module");

        // Basic registration validation
        assertThat(registration).isNotNull();
        assertThat(registration.getModuleName()).isEqualTo("parser-module");

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

        // Performance check - registration should be fast
        long startTime = System.currentTimeMillis();
        registration = parserService.getServiceRegistration(Empty.getDefaultInstance())
                .await().indefinitely();
        long duration = System.currentTimeMillis() - startTime;

        assertThat(duration).isLessThan(1000);

        LOG.info("✅ Comprehensive parser service registration test passed!");
    }

    @Test
    public void testParserServiceRegistrationSchema() {
        LOG.info("=== Testing Parser Service Registration Schema Details ===");

        // Get the registration
        var registration = parserService.getServiceRegistration(Empty.getDefaultInstance())
                .await().indefinitely();

        assertThat(registration.getModuleName()).isEqualTo("parser-module");

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

        // Log schema details
        LOG.infof("Module name: %s", registration.getModuleName());
        LOG.infof("Schema length: %d characters", schema.length());
        LOG.infof("Schema contains extractMetadata: %s", schema.contains("extractMetadata"));
        LOG.infof("Schema contains maxContentLength: %s", schema.contains("maxContentLength"));
        LOG.infof("Schema contains enableTitleExtraction: %s", schema.contains("enableTitleExtraction"));

        LOG.info("✅ Parser service registration schema validation passed!");
    }

    @Test
    public void testParserServiceRegistrationPerformance() {
        LOG.info("=== Testing Parser Service Registration Performance ===");

        // Test that registration is fast (should complete quickly)
        long startTime = System.currentTimeMillis();

        var registration = parserService.getServiceRegistration(Empty.getDefaultInstance())
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
        var registration1 = parserService.getServiceRegistration(Empty.getDefaultInstance())
                .await().indefinitely();
        var registration2 = parserService.getServiceRegistration(Empty.getDefaultInstance())
                .await().indefinitely();
        var registration3 = parserService.getServiceRegistration(Empty.getDefaultInstance())
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
