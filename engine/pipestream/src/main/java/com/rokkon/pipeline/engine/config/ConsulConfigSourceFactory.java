package com.rokkon.pipeline.engine.config;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Custom ConfigSourceFactory that seeds Consul with default configuration if needed.
 * This runs before the Quarkus consul-config extension tries to load config.
 */
public class ConsulConfigSourceFactory implements ConfigSourceFactory {
    
    private static final Logger LOG = Logger.getLogger(ConsulConfigSourceFactory.class);
    
    @Override
    public OptionalInt getPriority() {
        return OptionalInt.of(250); // Higher than default (100) but lower than dev mode (500)
    }
    
    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        // Check if consul-config is enabled
        String consulEnabled = context.getValue("quarkus.consul-config.enabled").getValue();
        if (!"true".equals(consulEnabled)) {
            LOG.info("Consul config is disabled, skipping seeding");
            return Collections.emptyList();
        }
        
        // Get Consul connection details
        String consulHost = context.getValue("consul.host").getValue();
        if (consulHost == null) consulHost = "localhost";
        
        String consulPortStr = context.getValue("consul.port").getValue();
        int consulPort = consulPortStr != null ? Integer.parseInt(consulPortStr) : 8500;
        
        LOG.infof("Checking Consul at %s:%d for application configuration...", consulHost, consulPort);
        
        // Create Vert.x and Consul client
        Vertx vertx = Vertx.vertx();
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consulHost)
            .setPort(consulPort);
        ConsulClient client = ConsulClient.create(vertx, options);
        
        // Use a latch to wait for the async operation
        CountDownLatch latch = new CountDownLatch(1);
        
        // Check and seed configuration
        String configKey = "config/application";
        client.getValue(configKey).onComplete(ar -> {
            try {
                if (ar.succeeded() && ar.result() != null && ar.result().getValue() != null) {
                    LOG.info("Configuration already exists in Consul");
                } else {
                    LOG.info("Configuration not found in Consul, seeding defaults...");
                    seedDefaultConfiguration(client, configKey);
                }
            } finally {
                latch.countDown();
            }
        });
        
        try {
            // Wait up to 5 seconds for the operation to complete
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while seeding Consul configuration", e);
        } finally {
            // Clean up
            client.close();
            vertx.close();
        }
        
        // Return empty - we're just seeding, not providing a config source
        return Collections.emptyList();
    }
    
    private void seedDefaultConfiguration(ConsulClient client, String configKey) {
        // Create default configuration
        Map<String, String> props = new HashMap<>();
        props.put("rokkon.engine.name", "rokkon-engine");
        props.put("rokkon.engine.version", "1.0.0-SNAPSHOT");
        props.put("rokkon.consul.cleanup.interval", "PT5M");
        props.put("rokkon.consul.cleanup.zombie-threshold", "PT2M");
        props.put("rokkon.consul.health.check-interval", "10s");
        props.put("rokkon.consul.health.deregister-after", "30s");
        props.put("rokkon.modules.connection-timeout", "PT30S");
        props.put("rokkon.modules.max-retries", "3");
        props.put("rokkon.clusters.default.name", "default");
        props.put("rokkon.clusters.default.description", "Default cluster");
        
        // Convert to properties format
        StringBuilder sb = new StringBuilder();
        props.forEach((key, value) -> {
            sb.append(key).append("=").append(value).append("\n");
        });
        
        String configValue = sb.toString();
        
        // Store in Consul synchronously
        CountDownLatch putLatch = new CountDownLatch(1);
        client.putValue(configKey, configValue).onComplete(ar -> {
            if (ar.succeeded() && ar.result()) {
                LOG.info("Successfully seeded default configuration to Consul");
                LOG.debug("Configuration content:\n" + configValue);
            } else {
                LOG.error("Failed to seed configuration to Consul", ar.cause());
            }
            putLatch.countDown();
        });
        
        try {
            putLatch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while storing configuration in Consul", e);
        }
    }
}