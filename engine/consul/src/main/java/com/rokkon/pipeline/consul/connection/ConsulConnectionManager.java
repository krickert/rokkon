package com.rokkon.pipeline.consul.connection;

import io.quarkus.runtime.Startup;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import com.rokkon.pipeline.events.ConsulConnectionEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages dynamic Consul connections.
 * Allows changing Consul connection settings at runtime.
 */
@ApplicationScoped
public class ConsulConnectionManager {
    
    private static final Logger LOG = Logger.getLogger(ConsulConnectionManager.class);
    
    @Inject
    Vertx vertx;
    
    @Inject
    Event<ConsulConnectionEvent> connectionEvent;
    
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String defaultHost;
    
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    int defaultPort;
    
    @ConfigProperty(name = "consul.enabled", defaultValue = "true")
    boolean consulEnabled;
    
    private final AtomicReference<ConsulConnectionConfig> currentConfig = new AtomicReference<>();
    private final AtomicReference<ConsulClient> currentClient = new AtomicReference<>();
    
    void onStart(@jakarta.enterprise.event.Observes io.quarkus.runtime.StartupEvent ev) {
        // Check if consul is enabled from configuration (could be from persisted file or application.yml)
        if (consulEnabled) {
            // The configuration should already be loaded by the ConfigSource
            // Use the values that were injected via @ConfigProperty
            ConsulConnectionConfig config = new ConsulConnectionConfig(defaultHost, defaultPort, true);
            currentConfig.set(config);
            
            // Try to connect with the loaded settings
            testAndConnect(config).subscribe().with(
                client -> {
                    currentClient.set(client);
                    LOG.infof("Initial Consul connection established to %s:%d", config.host(), config.port());
                    connectionEvent.fire(new ConsulConnectionEvent(
                        ConsulConnectionEvent.Type.CONNECTED,
                        new ConsulConnectionEvent.ConsulConnectionConfig(
                            config.host(),
                            config.port(),
                            true
                        ),
                        "Connected on startup"
                    ));
                },
                failure -> {
                    LOG.warnf("Initial Consul connection failed: %s", failure.getMessage());
                    connectionEvent.fire(new ConsulConnectionEvent(
                        ConsulConnectionEvent.Type.CONNECTION_FAILED,
                        new ConsulConnectionEvent.ConsulConnectionConfig(
                            config.host(),
                            config.port(),
                            false
                        ),
                        failure.getMessage()
                    ));
                }
            );
        } else {
            LOG.info("Consul connection disabled by configuration");
            currentConfig.set(new ConsulConnectionConfig("", 0, false));
        }
    }
    
    /**
     * Get the current Consul client if connected.
     */
    public Optional<ConsulClient> getClient() {
        return Optional.ofNullable(currentClient.get());
    }
    
    /**
     * Get the current Consul client as Mutiny if connected.
     */
    public Optional<io.vertx.mutiny.ext.consul.ConsulClient> getMutinyClient() {
        return getClient().map(io.vertx.mutiny.ext.consul.ConsulClient::newInstance);
    }
    
    /**
     * Get the current connection configuration.
     */
    public ConsulConnectionConfig getConfiguration() {
        return currentConfig.get();
    }
    
    /**
     * Update Consul connection configuration dynamically.
     */
    public Uni<ConsulConnectionResult> updateConnection(String host, int port) {
        LOG.infof("Updating Consul connection to %s:%d", host, port);
        
        ConsulConnectionConfig newConfig = new ConsulConnectionConfig(host, port, true);
        
        return testAndConnect(newConfig)
            .onItem().transform(client -> {
                // Close old client if exists
                ConsulClient oldClient = currentClient.getAndSet(client);
                if (oldClient != null) {
                    try {
                        oldClient.close();
                    } catch (Exception e) {
                        LOG.warnf("Error closing old Consul client: %s", e.getMessage());
                    }
                }
                
                currentConfig.set(newConfig);
                
                // Configuration is now managed via environment variables only
                
                // Fire connection event
                connectionEvent.fire(new ConsulConnectionEvent(
                    ConsulConnectionEvent.Type.CONNECTED,
                    new ConsulConnectionEvent.ConsulConnectionConfig(
                        newConfig.host(),
                        newConfig.port(),
                        true
                    ),
                    "Successfully connected to Consul"
                ));
                
                return new ConsulConnectionResult(true, "Successfully connected to Consul", newConfig);
            })
            .onFailure().recoverWithItem(error -> {
                LOG.errorf("Failed to connect to Consul at %s:%d: %s", host, port, error.getMessage());
                
                // Fire disconnection event
                connectionEvent.fire(new ConsulConnectionEvent(
                    ConsulConnectionEvent.Type.CONNECTION_FAILED,
                    new ConsulConnectionEvent.ConsulConnectionConfig(
                        newConfig.host(),
                        newConfig.port(),
                        false
                    ),
                    error.getMessage()
                ));
                
                return new ConsulConnectionResult(false, error.getMessage(), newConfig);
            });
    }
    
    /**
     * Disconnect from Consul.
     */
    public Uni<ConsulConnectionResult> disconnect() {
        LOG.info("Disconnecting from Consul");
        
        ConsulClient client = currentClient.getAndSet(null);
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                LOG.warnf("Error closing Consul client: %s", e.getMessage());
            }
        }
        
        ConsulConnectionConfig noConnection = new ConsulConnectionConfig("", 0, false);
        currentConfig.set(noConnection);
        
        // Configuration is now managed via environment variables only
        
        // Fire disconnection event
        connectionEvent.fire(new ConsulConnectionEvent(
            ConsulConnectionEvent.Type.DISCONNECTED,
            new ConsulConnectionEvent.ConsulConnectionConfig(
                noConnection.host(),
                noConnection.port(),
                false
            ),
            "Manually disconnected from Consul"
        ));
        
        return Uni.createFrom().item(new ConsulConnectionResult(true, "Disconnected from Consul", noConnection));
    }
    
    /**
     * Test connection to Consul and return a client if successful.
     */
    private Uni<ConsulClient> testAndConnect(ConsulConnectionConfig config) {
        return Uni.createFrom().item(() -> {
            ConsulClientOptions options = new ConsulClientOptions()
                .setHost(config.host())
                .setPort(config.port());
            
            ConsulClient client = ConsulClient.create(vertx, options);
            
            // Test the connection by making a simple call
            return client;
        })
        .onItem().transformToUni(client -> {
            // Test with agent/self endpoint which should always be available
            return Uni.createFrom().completionStage(client.agentInfo().toCompletionStage())
                .onItem().transform(info -> {
                    LOG.debugf("Consul agent info: %s", info);
                    return client;
                });
        });
    }
    
    /**
     * Configuration for a Consul connection.
     */
    public record ConsulConnectionConfig(
        String host,
        int port,
        boolean enabled
    ) {
        public String getConnectionString() {
            return enabled ? String.format("%s:%d", host, port) : "Not connected";
        }
    }
    
    /**
     * Result of a connection attempt.
     */
    public record ConsulConnectionResult(
        boolean success,
        String message,
        ConsulConnectionConfig config
    ) {}
}