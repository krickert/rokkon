package com.rokkon.pipeline.engine.startup;

import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.KeyValue;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Seeds Consul with application configuration if it doesn't exist.
 * This allows the application to start even if Consul doesn't have the config yet.
 */
@ApplicationScoped
@io.quarkus.runtime.Startup
public class ConsulConfigSeeder {
    
    private static final Logger LOG = Logger.getLogger(ConsulConfigSeeder.class);
    
    @Inject
    Vertx vertx;
    
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String consulHost;
    
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    int consulPort;
    
    @ConfigProperty(name = "quarkus.consul-config.enabled", defaultValue = "true")
    boolean consulConfigEnabled;
    
    @ConfigProperty(name = "quarkus.application.name", defaultValue = "rokkon-engine")
    String applicationName;
    
    void onStart(@Observes StartupEvent event) {
        if (!consulConfigEnabled) {
            LOG.info("Consul config is disabled, skipping config seeding");
            return;
        }
        
        LOG.info("Checking Consul for application configuration...");
        
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consulHost)
            .setPort(consulPort);
            
        ConsulClient client = ConsulClient.create(vertx, options);
        
        // Check if config exists in Consul
        String configKey = "config/application";
        
        client.getValue(configKey).onComplete(ar -> {
            if (ar.succeeded()) {
                KeyValue kv = ar.result();
                if (kv != null && kv.getValue() != null) {
                    LOG.info("Configuration already exists in Consul at: " + configKey);
                    return;
                }
            }
            
            LOG.info("Configuration not found in Consul, seeding from classpath...");
            seedConfiguration(client, configKey);
        });
    }
    
    private void seedConfiguration(ConsulClient client, String configKey) {
        try {
            // Try to load application.properties from classpath
            Properties props = new Properties();
            boolean foundConfig = false;
            
            // Try application.properties first
            try (InputStream is = getClass().getResourceAsStream("/application.properties")) {
                if (is != null) {
                    props.load(is);
                    foundConfig = true;
                    LOG.info("Loaded application.properties from classpath");
                }
            }
            
            // If no properties file, create minimal config
            if (!foundConfig) {
                LOG.info("No application.properties found, creating minimal configuration");
                props.setProperty("rokkon.engine.name", applicationName);
                props.setProperty("rokkon.engine.version", "1.0.0-SNAPSHOT");
                props.setProperty("rokkon.consul.cleanup.interval", "PT5M");
                props.setProperty("rokkon.consul.cleanup.zombie-threshold", "PT2M");
                props.setProperty("rokkon.consul.health.check-interval", "10s");
                props.setProperty("rokkon.consul.health.deregister-after", "30s");
                props.setProperty("rokkon.modules.connection-timeout", "PT30S");
                props.setProperty("rokkon.modules.max-retries", "3");
                props.setProperty("rokkon.clusters.default.name", "default");
                props.setProperty("rokkon.clusters.default.description", "Default cluster");
            }
            
            // Convert properties to string
            StringBuilder sb = new StringBuilder();
            props.forEach((key, value) -> {
                sb.append(key).append("=").append(value).append("\n");
            });
            
            String configValue = sb.toString();
            
            // Store in Consul
            client.putValue(configKey, configValue).onComplete(ar -> {
                if (ar.succeeded() && ar.result()) {
                    LOG.info("Successfully seeded configuration to Consul at: " + configKey);
                    LOG.debug("Configuration content:\n" + configValue);
                } else {
                    LOG.error("Failed to seed configuration to Consul", ar.cause());
                }
            });
            
        } catch (IOException e) {
            LOG.error("Error loading configuration from classpath", e);
        }
    }
}