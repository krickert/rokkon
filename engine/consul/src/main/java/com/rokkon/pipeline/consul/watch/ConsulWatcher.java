package com.rokkon.pipeline.consul.watch;

import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import com.rokkon.pipeline.events.cache.ConsulPipelineDefinitionChangedEvent;
import com.rokkon.pipeline.events.cache.ConsulModuleRegistrationChangedEvent;
import com.rokkon.pipeline.events.cache.ConsulClusterPipelineChangedEvent;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.KeyValue;
import io.vertx.ext.consul.KeyValueList;
import io.vertx.ext.consul.Watch;
import io.vertx.ext.consul.WatchResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches Consul KV store for changes to pipeline definitions, module registrations,
 * and cluster configurations. Fires CDI events when changes are detected.
 */
@ApplicationScoped
public class ConsulWatcher {
    
    private static final Logger LOG = Logger.getLogger(ConsulWatcher.class);
    
    @Inject
    ConsulConnectionManager connectionManager;
    
    @Inject
    Vertx vertx;
    
    @Inject
    Event<ConsulPipelineDefinitionChangedEvent> pipelineDefinitionChangedEvent;
    
    @Inject
    Event<ConsulModuleRegistrationChangedEvent> moduleRegistrationChangedEvent;
    
    @Inject
    Event<ConsulClusterPipelineChangedEvent> clusterPipelineChangedEvent;
    
    @ConfigProperty(name = "pipeline.consul.watch.enabled", defaultValue = "true")
    boolean watchEnabled;
    
    @ConfigProperty(name = "consul.kv.prefix", defaultValue = "pipeline")
    String kvPrefix;
    
    @ConfigProperty(name = "pipeline.cluster.name", defaultValue = "default")
    String clusterName;
    
    @ConfigProperty(name = "pipeline.consul.watch.interval", defaultValue = "10s")
    Duration watchInterval;
    
    // Track active watches for cleanup
    private final List<Watch<KeyValueList>> activeWatches = new ArrayList<>();
    private final Map<String, Long> lastModifyIndex = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    
    /**
     * Start watching Consul on startup (after initialization completes)
     */
    void onStart(@Observes StartupEvent ev) {
        if (!watchEnabled) {
            LOG.info("Consul watching disabled");
            return;
        }
        
        // Delay start to allow initialization to complete first
        // Using Mutiny to schedule the delayed start
        Uni.createFrom().voidItem()
            .onItem().delayIt().by(Duration.ofSeconds(10))
            .subscribe().with(
                item -> {
                    if (!running) {
                        startWatching();
                    }
                },
                error -> LOG.error("Failed to schedule Consul watch startup", error)
            );
    }
    
    /**
     * Stop all watches on shutdown
     */
    void onStop(@Observes ShutdownEvent ev) {
        stopWatching();
    }
    
    /**
     * Start watching Consul KV paths
     */
    private synchronized void startWatching() {
        if (running) {
            return;
        }
        
        LOG.info("Starting Consul watches");
        running = true;
        
        ConsulClient client = connectionManager.getClient().orElseThrow(() -> 
            new IllegalStateException("Consul client not available"));
        
        // Watch pipeline definitions
        watchKeyPrefix(client, kvPrefix + "/pipelines/definitions", this::handlePipelineDefinitionChange);
        
        // Watch module registrations
        watchKeyPrefix(client, kvPrefix + "/modules/registered", this::handleModuleRegistrationChange);
        
        // Watch cluster pipelines
        watchKeyPrefix(client, kvPrefix + "/clusters/" + clusterName + "/pipelines", this::handleClusterPipelineChange);
        
        LOG.infof("Started %d Consul watches", activeWatches.size());
    }
    
    /**
     * Stop all active watches
     */
    private synchronized void stopWatching() {
        if (!running) {
            return;
        }
        
        LOG.info("Stopping Consul watches");
        running = false;
        
        activeWatches.forEach(Watch::stop);
        activeWatches.clear();
        lastModifyIndex.clear();
        
        LOG.info("Consul watches stopped");
    }
    
    /**
     * Watch a specific key prefix in Consul
     */
    private void watchKeyPrefix(ConsulClient client, String keyPrefix, 
                                java.util.function.Consumer<WatchResult<KeyValueList>> handler) {
        
        // Get the Consul options from the client to ensure we use the correct port
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(connectionManager.getConfiguration().host())
            .setPort(connectionManager.getConfiguration().port());
        
        Watch<KeyValueList> watch = Watch.keyPrefix(keyPrefix, vertx, options);
        
        watch.setHandler(result -> {
            if (result.succeeded()) {
                handler.accept(result);
            } else {
                LOG.warnf(result.cause(), "Watch failed for prefix: %s", keyPrefix);
                
                // Restart watch after failure
                if (running) {
                    watch.stop(); // Stop the failed watch first
                    Uni.createFrom().voidItem()
                        .onItem().delayIt().by(watchInterval)
                        .subscribe().with(
                            item -> {
                                if (running) {
                                    watchKeyPrefix(client, keyPrefix, handler); // Create a new watch
                                }
                            }
                        );
                }
            }
        });
        
        watch.start();
        activeWatches.add(watch);
        LOG.debugf("Started watch for: %s", keyPrefix);
    }
    
    /**
     * Handle changes to pipeline definitions
     */
    private void handlePipelineDefinitionChange(WatchResult<KeyValueList> result) {
        KeyValueList kvList = result.nextResult();
        
        if (kvList == null || kvList.getList() == null) {
            return;
        }
        
        // Check if the data actually changed using modify index
        Long currentIndex = kvList.getIndex();
        Long lastIndex = lastModifyIndex.get("pipeline-definitions");
        
        if (lastIndex != null && lastIndex.equals(currentIndex)) {
            return; // No actual change
        }
        
        lastModifyIndex.put("pipeline-definitions", currentIndex);
        
        List<KeyValue> changes = kvList.getList();
        LOG.debugf("Pipeline definitions changed: %d keys affected", changes.size());
        
        // Fire event for each changed pipeline
        changes.forEach(kv -> {
            String key = kv.getKey();
            // Extract pipeline ID from key: rokkon/pipelines/definitions/{pipelineId}
            String[] parts = key.split("/");
            if (parts.length >= 4) {
                String pipelineId = parts[3];
                // Skip metadata keys
                if (!key.endsWith("/metadata")) {
                    pipelineDefinitionChangedEvent.fire(
                        new ConsulPipelineDefinitionChangedEvent(pipelineId, kv.getValue())
                    );
                }
            }
        });
    }
    
    /**
     * Handle changes to module registrations
     */
    private void handleModuleRegistrationChange(WatchResult<KeyValueList> result) {
        KeyValueList kvList = result.nextResult();
        
        if (kvList == null || kvList.getList() == null) {
            return;
        }
        
        // Check if the data actually changed
        Long currentIndex = kvList.getIndex();
        Long lastIndex = lastModifyIndex.get("module-registrations");
        
        if (lastIndex != null && lastIndex.equals(currentIndex)) {
            return;
        }
        
        lastModifyIndex.put("module-registrations", currentIndex);
        
        List<KeyValue> changes = kvList.getList();
        LOG.debugf("Module registrations changed: %d keys affected", changes.size());
        
        // Fire event for each changed module
        changes.forEach(kv -> {
            String key = kv.getKey();
            // Extract module ID from key: rokkon/modules/registered/{moduleId}
            String[] parts = key.split("/");
            if (parts.length >= 4) {
                String moduleId = parts[3];
                moduleRegistrationChangedEvent.fire(
                    new ConsulModuleRegistrationChangedEvent(moduleId, kv.getValue())
                );
            }
        });
    }
    
    /**
     * Handle changes to cluster pipelines
     */
    private void handleClusterPipelineChange(WatchResult<KeyValueList> result) {
        KeyValueList kvList = result.nextResult();
        
        if (kvList == null || kvList.getList() == null) {
            return;
        }
        
        // Check if the data actually changed
        Long currentIndex = kvList.getIndex();
        Long lastIndex = lastModifyIndex.get("cluster-pipelines");
        
        if (lastIndex != null && lastIndex.equals(currentIndex)) {
            return;
        }
        
        lastModifyIndex.put("cluster-pipelines", currentIndex);
        
        List<KeyValue> changes = kvList.getList();
        LOG.debugf("Cluster pipelines changed: %d keys affected", changes.size());
        
        // Fire event for each changed pipeline
        changes.forEach(kv -> {
            String key = kv.getKey();
            // Extract pipeline ID from key: rokkon/clusters/{cluster}/pipelines/{pipelineId}/config
            String[] parts = key.split("/");
            if (parts.length >= 6) {
                String pipelineId = parts[5];
                clusterPipelineChangedEvent.fire(
                    new ConsulClusterPipelineChangedEvent(clusterName, pipelineId, kv.getValue())
                );
            }
        });
    }
}