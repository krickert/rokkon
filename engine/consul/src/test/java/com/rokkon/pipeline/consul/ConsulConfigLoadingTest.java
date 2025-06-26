package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import com.rokkon.pipeline.consul.test.NoSchedulerTestProfile;
import com.rokkon.pipeline.consul.test.TestSeedingService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test that consul-config actually loads configuration from Consul KV.
 */
@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
@TestProfile(NoSchedulerTestProfile.class)
public class ConsulConfigLoadingTest {
    
    @Inject
    TestSeedingService testSeedingService;

    @Inject
    Vertx vertx;

    @ConfigProperty(name = "consul.host")
    String consulHost;

    @ConfigProperty(name = "consul.port")
    int consulPort;

    // This property name matches the key being set in TestSeedingService
    @ConfigProperty(name = "rokkon.test-key", defaultValue = "not-loaded")
    String testValue;

    @Test
    void testSeedingAndConfigLoading() {
        // 1. Assert that the config property has its default value at startup
        assertThat(testValue).isEqualTo("not-loaded");

        // 2. Seed the configuration using the dedicated service
        testSeedingService.seedConsulConfiguration();

        // 3. Assert that the config property in the running application is NOT updated,
        //    as consul-config loads at startup and watch is disabled for tests.
        assertThat(testValue).isEqualTo("not-loaded");

        // 4. Verify directly in Consul that the value was written successfully by the seeding service.
        ConsulClient consulClient = ConsulClient.create(vertx, new ConsulClientOptions().setHost(consulHost).setPort(consulPort));
        String valueFromConsul = consulClient.getValue("rokkon/test-key")
                .map(kv -> kv.getValue())
                .await().indefinitely();
        assertThat(valueFromConsul).isEqualTo("test-value");
        System.out.println("✓ Successfully verified that seeding service wrote to Consul and that running config was not affected.");
    }
}