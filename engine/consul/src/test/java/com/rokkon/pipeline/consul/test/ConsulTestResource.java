package com.rokkon.pipeline.consul.test;

import io.quarkus.test.common.DevServicesContext;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;
import com.rokkon.test.containers.SharedNetworkManager;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import org.awaitility.Awaitility;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Testcontainers resource that starts Consul for integration tests.
 * Implements DevServicesContext.ContextAware to participate in Quarkus network sharing.
 * Uses a singleton pattern to share a single Consul container across all tests.
 */
public class ConsulTestResource implements QuarkusTestResourceLifecycleManager, DevServicesContext.ContextAware {
    
    private static final String CONSUL_IMAGE = "hashicorp/consul:1.18";
    private static volatile ConsulContainer sharedConsul;
    private static volatile boolean containerStarted = false;
    private Optional<String> containerNetworkId;
    private static volatile Network sharedNetwork;
    
    @Override
    public void setIntegrationTestContext(DevServicesContext context) {
        containerNetworkId = context.containerNetworkId();
    }
    
    @Override
    public Map<String, String> start() {
        // Use double-checked locking to ensure only one container is started
        if (!containerStarted) {
            synchronized (ConsulTestResource.class) {
                if (!containerStarted) {
                    // Get or create the shared network
                    sharedNetwork = SharedNetworkManager.getOrCreateNetwork();
                    
                    // Create and configure the container with reuse enabled
                    sharedConsul = new ConsulContainer(DockerImageName.parse(CONSUL_IMAGE))
                            .withNetwork(sharedNetwork)
                            .withNetworkAliases("consul")
                            .withReuse(true);
                    
                    sharedConsul.start();
                    containerStarted = true;
                    
                    // Seed Consul with initial configuration
                    seedConsulConfiguration();
                }
            }
        }
        
        // Return both external (for test) and internal (for containers) access information
        return Map.of(
            "consul.host", sharedConsul.getHost(),
            "consul.port", String.valueOf(sharedConsul.getMappedPort(8500)),
            "%test.consul.host", sharedConsul.getHost(),
            "%test.consul.port", String.valueOf(sharedConsul.getMappedPort(8500)),
            // Add container network information
            "consul.container.host", "consul",  // Network alias for container-to-container
            "consul.container.port", "8500",     // Internal port
            "test.network.id", sharedNetwork.getId()
        );
    }
    
    @Override
    public void stop() {
        // Don't stop the shared container - let it be reused across test runs
        // The container will be cleaned up when the JVM exits or when
        // Testcontainers Ryuk reaper removes it after inactivity
    }
    
    private void seedConsulConfiguration() {
        // Create Vertx instance for Consul client
        Vertx vertx = Vertx.vertx();
        
        try {
            // Create Consul client
            ConsulClientOptions options = new ConsulClientOptions()
                .setHost(sharedConsul.getHost())
                .setPort(sharedConsul.getMappedPort(8500));
            ConsulClient consulClient = ConsulClient.create(vertx, options);
            
            // Seed minimal configuration required for consul-config extension
            String minimalConfig = """
                smallrye:
                  config:
                    mapping:
                      validate-unknown: false
                
                rokkon:
                  engine:
                    grpc-port: 49000
                    rest-port: 8080
                    debug: false
                  
                  consul:
                    cleanup:
                      enabled: true
                      interval: 5m
                      zombie-threshold: 2m
                      cleanup-stale-whitelist: true
                    health:
                      check-interval: 10s
                      deregister-after: 1m
                      timeout: 5s
                  
                  modules:
                    auto-discover: false
                    service-prefix: "module-"
                    require-whitelist: true
                    connection-timeout: 30s
                    max-instances-per-module: 10
                  
                  default-cluster:
                    name: default
                    auto-create: true
                    description: "Default cluster for Rokkon pipelines"
                """;
            
            // Put configuration in Consul
            AtomicBoolean seeded = new AtomicBoolean(false);
            AtomicBoolean failed = new AtomicBoolean(false);
            
            consulClient.putValue("config/application", minimalConfig, ar -> {
                if (ar.succeeded()) {
                    System.out.println("Successfully seeded Consul with initial configuration");
                    seeded.set(true);
                } else {
                    System.err.println("Failed to seed Consul: " + ar.cause().getMessage());
                    failed.set(true);
                }
            });
            
            // Wait for seeding to complete using Awaitility
            Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> seeded.get() || failed.get());
            
            if (failed.get()) {
                throw new RuntimeException("Failed to seed Consul configuration");
            }
            
        } catch (Exception e) {
            System.err.println("Error seeding Consul: " + e.getMessage());
            e.printStackTrace();
        } finally {
            vertx.close();
        }
    }
}