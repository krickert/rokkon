package com.rokkon.pipeline.consul.watch;

import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import com.rokkon.pipeline.events.cache.ConsulModuleHealthChanged;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.*;
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
import java.util.stream.Collectors;

/**
 * Watches Consul service catalog for module health changes.
 * This complements the KV watcher by monitoring the health status of registered modules.
 */
@ApplicationScoped
public class ConsulServiceWatcher {
    
    private static final Logger LOG = Logger.getLogger(ConsulServiceWatcher.class);
    
    @Inject
    ConsulConnectionManager connectionManager;
    
    @Inject
    Vertx vertx;
    
    @Inject
    Event<ConsulModuleHealthChanged> moduleHealthChangedEvent;
    
    @ConfigProperty(name = "pipeline.consul.watch.enabled", defaultValue = "true")
    boolean watchEnabled;
    
    @ConfigProperty(name = "pipeline.consul.watch.services.interval", defaultValue = "5s")
    Duration watchInterval;
    
    // Track active watches
    private final List<Watch<ServiceEntryList>> activeWatches = new ArrayList<>();
    private final Map<String, ServiceHealthState> lastHealthStates = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    
    /**
     * Start watching services after a delay
     */
    void onStart(@Observes StartupEvent ev) {
        if (!watchEnabled) {
            return;
        }
        
        // Start after ConsulWatcher has initialized
        Uni.createFrom().voidItem()
            .onItem().delayIt().by(Duration.ofSeconds(10))
            .subscribe().with(
                item -> {
                    if (!running) {
                        startWatching();
                    }
                },
                error -> LOG.error("Failed to schedule service watch startup", error)
            );
    }
    
    /**
     * Stop watches on shutdown
     */
    void onStop(@Observes ShutdownEvent ev) {
        stopWatching();
    }
    
    /**
     * Start watching module services
     */
    private synchronized void startWatching() {
        if (running) {
            return;
        }
        
        LOG.info("Starting Consul service watches");
        running = true;
        
        ConsulClient client = connectionManager.getClient().orElseThrow(() -> 
            new IllegalStateException("Consul client not available"));
        
        // Watch all services with "module" tag
        watchModuleServices(client);
        
        LOG.info("Service watches started");
    }
    
    /**
     * Stop all watches
     */
    private synchronized void stopWatching() {
        if (!running) {
            return;
        }
        
        LOG.info("Stopping Consul service watches");
        running = false;
        
        activeWatches.forEach(Watch::stop);
        activeWatches.clear();
        lastHealthStates.clear();
    }
    
    /**
     * Watch services tagged with "module"
     */
    private void watchModuleServices(ConsulClient client) {
        // Get the Consul options from the client to ensure we use the correct port
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(connectionManager.getConfiguration().host())
            .setPort(connectionManager.getConfiguration().port());
        
        // Watch health endpoint directly for all services instead of specific service
        // This allows us to filter by tag in the handler
        activeWatches.clear(); // Clear any existing watches
        
        // We need to watch specific services or use a different approach
        // For now, let's disable service watching until we can test properly
        LOG.warn("Service watching temporarily disabled - needs proper implementation");
        
        // TODO: Implement proper service watching:
        // Option 1: Query for services with "module" tag periodically
        // Option 2: Watch individual module services by name
        // Option 3: Use catalog watch endpoint
    }
    
    /**
     * Handle service health changes
     */
    private void handleServiceHealthChange(WatchResult<ServiceEntryList> result) {
        ServiceEntryList serviceList = result.nextResult();
        
        if (serviceList == null || serviceList.getList() == null) {
            return;
        }
        
        List<ServiceEntry> services = serviceList.getList();
        
        // Filter for module services (those with "module" tag)
        List<ServiceEntry> moduleServices = services.stream()
            .filter(entry -> entry.getService().getTags() != null && 
                            entry.getService().getTags().contains("module"))
            .toList();
        
        // Process each module service entry
        moduleServices.forEach(entry -> {
            String serviceId = entry.getService().getId();
            String serviceName = entry.getService().getName();
            
            // Determine health state
            ServiceHealthState currentState = determineHealthState(entry);
            ServiceHealthState lastState = lastHealthStates.get(serviceId);
            
            // Check if state changed
            if (lastState == null || !lastState.equals(currentState)) {
                LOG.debugf("Module %s health changed: %s -> %s", 
                    serviceName, lastState, currentState);
                
                lastHealthStates.put(serviceId, currentState);
                
                // Fire health change event
                moduleHealthChangedEvent.fire(
                    new ConsulModuleHealthChanged(
                        serviceId,
                        serviceName,
                        currentState.status(),
                        currentState.reason()
                    )
                );
            }
        });
        
        // Check for removed services
        lastHealthStates.keySet().stream()
            .filter(serviceId -> moduleServices.stream()
                .noneMatch(entry -> entry.getService().getId().equals(serviceId)))
            .forEach(removedServiceId -> {
                LOG.debugf("Module service removed: %s", removedServiceId);
                lastHealthStates.remove(removedServiceId);
                
                // Fire removal event
                moduleHealthChangedEvent.fire(
                    new ConsulModuleHealthChanged(
                        removedServiceId,
                        "",
                        "removed",
                        "Service no longer registered"
                    )
                );
            });
    }
    
    /**
     * Determine health state from service entry
     */
    private ServiceHealthState determineHealthState(ServiceEntry entry) {
        List<Check> checks = entry.getChecks();
        
        if (checks == null || checks.isEmpty()) {
            return new ServiceHealthState("unknown", "No health checks");
        }
        
        // Check all health checks
        boolean hasFailure = false;
        boolean hasWarning = false;
        StringBuilder reason = new StringBuilder();
        
        for (Check check : checks) {
            CheckStatus status = check.getStatus();
            
            if (status == CheckStatus.CRITICAL) {
                hasFailure = true;
                if (reason.length() > 0) reason.append("; ");
                reason.append(check.getName()).append(": ").append(check.getOutput());
            } else if (status == CheckStatus.WARNING) {
                hasWarning = true;
                if (!hasFailure && reason.length() > 0) reason.append("; ");
                if (!hasFailure) {
                    reason.append(check.getName()).append(": warning");
                }
            }
        }
        
        if (hasFailure) {
            return new ServiceHealthState("critical", reason.toString());
        } else if (hasWarning) {
            return new ServiceHealthState("warning", reason.toString());
        } else {
            return new ServiceHealthState("passing", "All checks passing");
        }
    }
    
    /**
     * Internal record for tracking health state
     */
    private record ServiceHealthState(String status, String reason) {}
}