package com.rokkon.pipeline.seed;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.KeyValue;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that uses a real Consul container to verify seeding functionality.
 * Uses a single container instance for all tests with proper cleanup.
 */
@QuarkusMainTest
@QuarkusTestResource(ConsulTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConsulSeederCommandIT {

    private static Vertx vertx;
    private static ConsulClient consulClient;

    @BeforeAll
    static void setUpAll() {
        vertx = Vertx.vertx();
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(System.getProperty("consul.host", "localhost"))
            .setPort(Integer.parseInt(System.getProperty("consul.port", "8500")));
        consulClient = ConsulClient.create(vertx, options);
        
        // Set up Awaitility defaults
        Awaitility.setDefaultTimeout(Duration.ofSeconds(10));
        Awaitility.setDefaultPollInterval(Duration.ofMillis(500));
    }

    @AfterAll
    static void tearDownAll() {
        // Clean up all test data
        if (consulClient != null) {
            consulClient.deleteValue("config/application").onComplete(ar -> {});
            consulClient.deleteValue("custom/config/path").onComplete(ar -> {});
            consulClient.close();
        }
        if (vertx != null) {
            vertx.close();
        }
    }


    private void cleanupConsulKeys() {
        // Delete all test keys
        consulClient.deleteValue("config/application").onComplete(ar -> {});
        consulClient.deleteValue("custom/config/path").onComplete(ar -> {});
        
        // Wait for cleanup to complete
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .until(() -> {
                AtomicReference<Boolean> cleaned = new AtomicReference<>(true);
                consulClient.getValue("config/application").onComplete(ar -> {
                    if (ar.succeeded() && ar.result() != null && ar.result().getValue() != null) {
                        cleaned.set(false);
                    }
                });
                return cleaned.get();
            });
    }

    @Test
    @Order(1)
    @Launch({})  // Launch with no arguments - should use defaults from properties
    public void testSeedingDefaultConfiguration(LaunchResult result) {
        // Verify command completed successfully
        assertThat(result.exitCode()).isEqualTo(0);
        
        // Use Awaitility to wait for configuration to be seeded
        AtomicReference<KeyValue> storedValue = new AtomicReference<>();
        
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .until(() -> {
                consulClient.getValue("config/application").onComplete(ar -> {
                    if (ar.succeeded()) {
                        storedValue.set(ar.result());
                    }
                });
                return storedValue.get() != null && storedValue.get().getValue() != null;
            });
        
        String config = storedValue.get().getValue();
        assertThat(config).contains("rokkon.engine.name=rokkon-engine");
        assertThat(config).contains("rokkon.consul.cleanup.interval=PT5M");
        assertThat(config).contains("rokkon.modules.connection-timeout=PT30S");
    }

    @Test
    @Order(3)
    @Launch({"--force"})
    public void testForceSeedingOverwritesExisting(LaunchResult result) {
        // The command has already run when we get here
        // Verify command completed successfully
        assertThat(result.exitCode()).isEqualTo(0);
        
        // Check that configuration was overwritten
        AtomicReference<String> newValue = new AtomicReference<>();
        
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .until(() -> {
                consulClient.getValue("config/application").onComplete(ar -> {
                    if (ar.succeeded() && ar.result() != null) {
                        newValue.set(ar.result().getValue());
                    }
                });
                String val = newValue.get();
                return val != null && val.contains("rokkon.engine.name=rokkon-engine");
            });
        
        String config = newValue.get();
        // The force flag should have overwritten any existing config
        assertThat(config).contains("rokkon.engine.name=rokkon-engine");
        assertThat(config).contains("rokkon.consul.cleanup.interval=PT5M");
    }

    @Test
    @Order(4)
    @Launch({"--key", "custom/config/path"})  // Test custom key path
    public void testSeedingToCustomKeyPath(LaunchResult result) {
        // Verify command completed successfully
        assertThat(result.exitCode()).isEqualTo(0);
        
        // Check that configuration was seeded to custom path
        AtomicReference<KeyValue> storedValue = new AtomicReference<>();
        
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .until(() -> {
                consulClient.getValue("custom/config/path").onComplete(ar -> {
                    if (ar.succeeded()) {
                        storedValue.set(ar.result());
                    }
                });
                return storedValue.get() != null && storedValue.get().getValue() != null;
            });
        
        assertThat(storedValue.get().getValue()).contains("rokkon.engine.name=rokkon-engine");
    }

    @Test
    @Order(2)
    @Launch({})  // Run without --force flag  
    public void testDoesNotOverwriteExistingByDefault(LaunchResult result) {
        // Since tests run in order and the previous test seeded config,
        // this test should not overwrite it without --force
        assertThat(result.exitCode()).isEqualTo(0);
        
        // The output should indicate that config already exists
        assertThat(result.getOutput()).contains("Configuration already exists");
        
        // Verify the config still exists and contains the expected content
        AtomicReference<String> existingValue = new AtomicReference<>();
        
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .until(() -> {
                consulClient.getValue("config/application").onComplete(ar -> {
                    if (ar.succeeded() && ar.result() != null) {
                        existingValue.set(ar.result().getValue());
                    }
                });
                return existingValue.get() != null;
            });
        
        // Should still have the seeded config from previous tests
        assertThat(existingValue.get()).contains("rokkon.engine.name=rokkon-engine");
    }
}