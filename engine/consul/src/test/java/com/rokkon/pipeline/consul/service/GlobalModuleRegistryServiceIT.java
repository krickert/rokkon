package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.consul.test.ConsulIntegrationTestBase;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GlobalModuleRegistryService using real Consul.
 * Each test runs in isolation with its own prefixes.
 */
@DisplayName("GlobalModuleRegistryService Consul Integration Tests")
class GlobalModuleRegistryServiceIT extends ConsulIntegrationTestBase {
    
    @Inject
    GlobalModuleRegistryService registryService;
    
    // Test-specific JSON schema
    private final String validJsonSchema = """
        {
            "$schema": "http://json-schema.org/draft-07/schema#",
            "title": "Test Schema",
            "type": "object",
            "properties": {
                "name": { "type": "string" },
                "value": { "type": "number" }
            },
            "required": ["name"]
        }
        """;
    
    @Test
    @DisplayName("Should successfully register a module with unique test prefix")
    void testRegisterModule_Success() {
        // Given
        String moduleName = "test-module";
        String host = "localhost";
        int port = findAvailablePort();
        
        // When - Register module with test-specific naming
        var registration = registryService.registerModule(
            moduleName,
            "impl-" + testId,
            host,
            port,
            "GRPC",
            "1.0.0",
            Map.of("test-id", testId),
            host,
            port,
            validJsonSchema
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(Duration.ofSeconds(5))
            .assertCompleted()
            .getItem();
        
        // Then
        assertNotNull(registration);
        assertEquals(moduleName, registration.moduleName());
        assertEquals(host, registration.host());
        assertEquals(port, registration.port());
        assertTrue(registration.enabled());
        assertEquals("1.0.0", registration.version());
        assertNotNull(registration.jsonSchema());
        
        // Verify in Consul using our test client
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            // Check service is registered
            var services = consulClient.healthServiceNodes("module-" + moduleName, true)
                .await().indefinitely()
                .getList();
            
            assertFalse(services.isEmpty(), "Service should be registered in Consul");
            
            // Check metadata is in KV store
            var kvValue = consulClient.getValue(
                "rokkon/modules/registered/" + registration.moduleId()
            ).await().indefinitely();
            
            assertNotNull(kvValue, "Module metadata should be in KV store");
            assertTrue(kvValue.getValue().contains(registration.moduleId()));
        });
    }
    
    @Test
    @DisplayName("Should fail registration when module is unreachable")
    void testRegisterModule_ConnectionFailure() {
        // Given - unreachable port
        String moduleName = "unreachable-module";
        String host = "localhost";
        int port = 1; // Port 1 is typically reserved and unreachable
        
        // When/Then
        WebApplicationException exception = assertThrows(WebApplicationException.class, () -> {
            registryService.registerModule(
                moduleName,
                "impl-fail-" + testId,
                host,
                port,
                "GRPC",
                "1.0.0",
                null,
                host,
                port,
                null
            ).await().atMost(Duration.ofSeconds(5));
        });
        
        assertEquals(400, exception.getResponse().getStatus());
        assertTrue(exception.getMessage().contains("Cannot connect to module"));
    }
    
    @Test
    @DisplayName("Should validate JSON schema during registration")
    void testRegisterModule_InvalidJsonSchema() {
        // Given
        String moduleName = "schema-test-module";
        String invalidSchema = "{ invalid json schema }";
        
        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            registryService.registerModule(
                moduleName,
                "impl-schema-" + testId,
                "localhost",
                findAvailablePort(),
                "GRPC",
                "1.0.0",
                null,
                "localhost",
                findAvailablePort(),
                invalidSchema
            ).await().indefinitely();
        });
        
        assertTrue(exception.getMessage().contains("Invalid JSON Schema"));
    }
    
    @Test
    @DisplayName("Should enable and disable modules within test cluster")
    void testEnableDisableModule() {
        // Given - First register a module
        var registration = registryService.registerModule(
            "toggle-module",
            "impl-toggle-" + testId,
            "localhost",
            findAvailablePort(),
            "GRPC",
            "1.0.0",
            null,
            "localhost",
            findAvailablePort(),
            null
        ).await().indefinitely();
        
        String moduleId = registration.moduleId();
        
        // Add to test cluster whitelist
        registryService.enableModuleForCluster(testClusterName, moduleId)
            .await().indefinitely();
        
        // When - Disable the module
        var disableResult = registryService.disableModule(moduleId)
            .await().indefinitely();
        
        // Then - Verify disable succeeded
        assertTrue(disableResult);
        
        // When - Re-enable the module
        var enableResult = registryService.enableModule(moduleId)
            .await().indefinitely();
        
        // Then - Verify enable succeeded
        assertTrue(enableResult);
        
        // Verify cluster whitelist is maintained
        var clusterModules = registryService.listEnabledModulesForCluster(testClusterName)
            .await().indefinitely();
        assertTrue(clusterModules.contains(moduleId));
    }
    
    @Test
    @DisplayName("Should handle multiple module instances with same name")
    void testMultipleInstances() {
        // Given
        String moduleName = "multi-instance-module";
        
        // When - Register multiple instances
        var instance1 = registryService.registerModule(
            moduleName,
            "impl-1-" + testId,
            "localhost",
            findAvailablePort(),
            "GRPC",
            "1.0.0",
            Map.of("instance", "1"),
            "localhost",
            findAvailablePort(),
            null
        ).await().indefinitely();
        
        var instance2 = registryService.registerModule(
            moduleName,
            "impl-2-" + testId,
            "localhost",
            findAvailablePort(),
            "GRPC",
            "1.0.0",
            Map.of("instance", "2"),
            "localhost",
            findAvailablePort(),
            null
        ).await().indefinitely();
        
        // Then - Both should be registered with different IDs
        assertNotEquals(instance1.moduleId(), instance2.moduleId());
        assertEquals(moduleName, instance1.moduleName());
        assertEquals(moduleName, instance2.moduleName());
        
        // Verify both appear in module list
        var allModules = registryService.listRegisteredModules()
            .await().indefinitely();
        
        assertTrue(allModules.stream().anyMatch(m -> m.moduleId().equals(instance1.moduleId())));
        assertTrue(allModules.stream().anyMatch(m -> m.moduleId().equals(instance2.moduleId())));
    }
    
    @Test
    @DisplayName("Should cleanup zombie instances in test namespace")
    void testZombieCleanup() {
        // Given - Create a "zombie" service directly in Consul
        String zombieId = getTestServiceName("zombie-" + UUID.randomUUID().toString().substring(0, 8));
        
        // Register directly with Consul with a failing health check
        consulClient.registerService(
            new io.vertx.ext.consul.ServiceOptions()
                .setId(zombieId)
                .setName(getTestServiceName("zombie-module"))
                .setAddress("localhost")
                .setPort(findAvailablePort())
                .setCheckOptions(new io.vertx.ext.consul.CheckOptions()
                    .setTcp("localhost:1") // This will fail - port 1 is reserved
                    .setInterval("1s")
                    .setDeregisterAfter("5s"))
        ).await().indefinitely();
        
        // Add fake metadata to KV store
        consulClient.putValue(
            "rokkon/modules/registered/" + zombieId,
            """
            {
                "moduleId": "%s",
                "moduleName": "zombie-module",
                "enabled": true
            }
            """.formatted(zombieId)
        ).await().indefinitely();
        
        // Wait for health check to fail
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(1))
            .until(() -> {
                var health = consulClient.healthChecks(getTestServiceName("zombie-module"))
                    .await().indefinitely();
                return health.getList().stream()
                    .anyMatch(check -> check.getStatus() == io.vertx.ext.consul.CheckStatus.CRITICAL);
            });
        
        // When - Run zombie cleanup
        var cleanupResult = registryService.cleanupZombieInstances()
            .await().indefinitely();
        
        // Then - Zombie should be detected and cleaned
        assertTrue(cleanupResult.zombiesDetected() > 0);
        assertTrue(cleanupResult.zombiesCleaned() > 0);
        
        // Verify zombie is gone from KV store
        var kvValue = consulClient.getValue("rokkon/modules/registered/" + zombieId)
            .await().indefinitely();
        assertNull(kvValue);
    }
    
    @Override
    protected void setupTest() {
        // Any additional test-specific setup
        LOG.infof("Setting up GlobalModuleRegistryService integration test");
    }
    
    @Override 
    protected void cleanupTest() {
        // Clean up any module registrations from this test
        try {
            // Clean up any modules registered during the test
            var allModules = registryService.listRegisteredModules()
                .await().atMost(Duration.ofSeconds(5));
            
            for (var module : allModules) {
                // Only clean up modules from this test (check metadata)
                if (module.metadata() != null && testId.equals(module.metadata().get("test-id"))) {
                    registryService.deregisterModule(module.moduleId())
                        .await().atMost(Duration.ofSeconds(2));
                }
            }
        } catch (Exception e) {
            // Error cleaning up test modules
        }
    }
}