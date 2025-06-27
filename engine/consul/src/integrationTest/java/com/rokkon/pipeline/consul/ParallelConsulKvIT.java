package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;

/**
 * Integration test demonstrating that our test isolation works for parallel execution.
 * Multiple instances of this test can run in parallel without interfering.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
public class ParallelConsulKvIT extends ParallelConsulKvTestBase {
    
    @Inject
    Vertx vertx;
    
    @ConfigProperty(name = "consul.host")
    String consulHost;
    
    @ConfigProperty(name = "consul.port")
    int consulPort;
    
    private ConsulClient consulClient;
    
    @BeforeEach
    void setupConsulClient() {
        super.setup();
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consulHost)
            .setPort(consulPort);
        consulClient = ConsulClient.create(vertx, options);
    }
    
    @Override
    protected ConsulClient getConsulClient() {
        return consulClient;
    }
    
    @Override
    protected boolean isRealConsul() {
        return true; // This is an integration test with real Consul
    }
}