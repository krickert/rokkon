package com.rokkon.pipeline.consul.init;

import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService;
import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService.ModuleRegistration;
import com.rokkon.pipeline.events.cache.ConsulStateRestored;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.Startup;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Restores engine state from Consul on startup.
 * The engine is stateful and uses Consul as its source of truth.
 * All engines share the same state from Consul.
 */
@ApplicationScoped
public class ConsulInitializer {
    
    private static final Logger LOG = Logger.getLogger(ConsulInitializer.class);
    
    @Inject
    GlobalModuleRegistryService moduleRegistryService;
    
    @Inject
    Event<ConsulStateRestored> stateRestoredEvent;
    
    @ConfigProperty(name = "pipeline.consul.init.enabled", defaultValue = "true")
    boolean initEnabled;
    
    // Engine state restored from Consul
    private final Set<ModuleRegistration> restoredModules = ConcurrentHashMap.newKeySet();
    
    /**
     * Restore engine state from Consul on startup
     */
    void onStart(@Observes StartupEvent ev) {
        if (!initEnabled) {
            LOG.info("Consul state restoration disabled");
            return;
        }
        
        // Delay initialization to ensure Consul connection is ready
        // This avoids the "Consul not connected" error
        Uni.createFrom().voidItem()
            .onItem().delayIt().by(java.time.Duration.ofSeconds(2))
            .onItem().transformToUni(v -> {
                LOG.info("Restoring engine state from Consul");
                return restoreModuleState();
            })
            .subscribe().with(
                v -> LOG.info("Engine state restored from Consul"),
                error -> LOG.error("Failed to restore engine state from Consul", error)
            );
    }
    
    /**
     * Restore module registrations from Consul - this is the engine's state
     */
    private Uni<Void> restoreModuleState() {
        return moduleRegistryService.listRegisteredModules()
            .onFailure().retry().atMost(3)
            .onItem().invoke(modules -> {
                LOG.infof("Restoring %d module registrations from Consul", modules.size());
                
                // Clear and repopulate engine state
                restoredModules.clear();
                restoredModules.addAll(modules);
                
                // Fire event so other components know state is restored
                stateRestoredEvent.fire(new ConsulStateRestored(modules));
                
                // Log what we restored
                modules.forEach(module -> {
                    LOG.infof("Restored module: %s (%s) at %s:%d - %s", 
                        module.moduleId(), 
                        module.moduleName(),
                        module.host(),
                        module.port(),
                        module.enabled() ? "enabled" : "disabled"
                    );
                });
                
                // Check for zombie modules AFTER state is restored
                checkForZombieModules(modules);
            })
            .replaceWith(Uni.createFrom().voidItem())
            .onFailure().invoke(error -> 
                LOG.errorf(error, "Failed to restore module state from Consul")
            );
    }
    
    /**
     * Check for zombie modules - modules that are registered but not healthy
     */
    private void checkForZombieModules(Set<ModuleRegistration> modules) {
        // This would be expanded to actually check health status
        // For now, just log what we found
        long enabledCount = modules.stream().filter(ModuleRegistration::enabled).count();
        long disabledCount = modules.size() - enabledCount;
        
        if (disabledCount > 0) {
            LOG.warnf("Found %d disabled modules that may be zombies", disabledCount);
        }
        
        // TODO: Check actual health status from Consul service health checks
        // to identify true zombies (registered but failing health checks)
    }
    
    /**
     * Get the restored module registrations
     */
    public Set<ModuleRegistration> getRestoredModules() {
        return Set.copyOf(restoredModules);
    }
}