package com.rokkon.pipeline.seed;

import io.quarkus.logging.Log;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.KeyValue;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import jakarta.inject.Inject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@TopCommand
@Command(name = "seed-consul", mixinStandardHelpOptions = true,
        description = "Seeds Consul with initial application configuration")
public class ConsulSeederCommand implements Runnable {

    @Inject
    Vertx vertx;

    @Option(names = {"-h", "--host"}, description = "Consul host", defaultValue = "${consul.host:-localhost}")
    String consulHost;

    @Option(names = {"-p", "--port"}, description = "Consul port", defaultValue = "${consul.port:-8500}")
    int consulPort;

    @Option(names = {"--key"}, description = "Consul key path", defaultValue = "config/application")
    String keyPath;

    @Option(names = {"--force"}, description = "Force overwrite existing configuration")
    boolean force;

    @Override
    public void run() {
        Log.infof("Connecting to Consul at %s:%d", consulHost, consulPort);
        
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consulHost)
            .setPort(consulPort);
            
        ConsulClient client = ConsulClient.create(vertx, options);
        
        // Check if config already exists
        CountDownLatch checkLatch = new CountDownLatch(1);
        final boolean[] configExists = {false};
        
        client.getValue(keyPath).onComplete(ar -> {
            if (ar.succeeded() && ar.result() != null && ar.result().getValue() != null) {
                configExists[0] = true;
                Log.infof("Configuration already exists at key: %s", keyPath);
            }
            checkLatch.countDown();
        });
        
        try {
            if (!checkLatch.await(5, TimeUnit.SECONDS)) {
                Log.error("Timeout checking existing configuration");
                return;
            }
        } catch (InterruptedException e) {
            Log.error("Interrupted while checking configuration", e);
            return;
        }
        
        // If config exists and not forcing, exit
        if (configExists[0] && !force) {
            Log.info("Configuration already exists. Use --force to overwrite.");
            return;
        }
        
        // Create default configuration
        String defaultConfig = createDefaultConfiguration();
        
        // Store in Consul
        CountDownLatch putLatch = new CountDownLatch(1);
        final boolean[] success = {false};
        
        client.putValue(keyPath, defaultConfig).onComplete(ar -> {
            if (ar.succeeded() && ar.result()) {
                success[0] = true;
                Log.infof("Successfully seeded configuration to Consul at key: %s", keyPath);
                Log.debug("Configuration content:\n" + defaultConfig);
            } else {
                Log.error("Failed to seed configuration to Consul", ar.cause());
            }
            putLatch.countDown();
        });
        
        try {
            if (!putLatch.await(5, TimeUnit.SECONDS)) {
                Log.error("Timeout storing configuration");
                return;
            }
        } catch (InterruptedException e) {
            Log.error("Interrupted while storing configuration", e);
            return;
        }
        
        client.close();
        
        if (success[0]) {
            Log.info("Consul seeding completed successfully!");
        } else {
            Log.error("Consul seeding failed!");
            System.exit(1);
        }
    }
    
    private String createDefaultConfiguration() {
        return """
            # Rokkon Engine default configuration
            rokkon.engine.name=rokkon-engine
            rokkon.engine.version=1.0.0-SNAPSHOT
            
            # Consul cleanup settings
            rokkon.consul.cleanup.interval=PT5M
            rokkon.consul.cleanup.zombie-threshold=PT2M
            
            # Consul health check settings
            rokkon.consul.health.check-interval=10s
            rokkon.consul.health.deregister-after=30s
            
            # Module management settings
            rokkon.modules.connection-timeout=PT30S
            rokkon.modules.max-retries=3
            
            # Default cluster configuration
            rokkon.clusters.default.name=default
            rokkon.clusters.default.description=Default cluster
            """;
    }
}