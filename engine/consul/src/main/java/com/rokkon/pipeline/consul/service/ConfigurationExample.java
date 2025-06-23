package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.consul.config.ConsulConfigSource;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Example showing how to use the hybrid configuration approach.
 * 
 * Simple configs come from consul-config (or application.yml as fallback).
 * Complex data structures remain in Consul KV store.
 */
@ApplicationScoped
public class ConfigurationExample {
    
    private static final Logger LOG = Logger.getLogger(ConfigurationExample.class);
    
    @Inject
    ConsulConfigSource config;
    
    @Inject
    GlobalModuleRegistryService registryService;
    
    @Inject
    PipelineConfigService pipelineService;
    
    /**
     * Example of using consul-config for scheduled tasks.
     * The interval comes from Consul config.
     * Note: Quarkus doesn't support dynamic enable/disable, so we check it in the method.
     */
    @Scheduled(every = "{rokkon.consul.cleanup.interval}")
    void scheduledCleanup() {
        if (!config.consul().cleanup().enabled()) {
            return;
        }
        
        LOG.infof("Running scheduled cleanup (interval: %s, zombie threshold: %s)",
            config.consul().cleanup().interval(),
            config.consul().cleanup().zombieThreshold());
        
        // The actual cleanup still uses the registry service
        registryService.cleanupZombieInstances()
            .subscribe().with(
                result -> LOG.infof("Cleanup complete: %d zombies detected, %d cleaned", 
                    result.zombiesDetected(), result.zombiesCleaned()),
                error -> LOG.error("Cleanup failed", error)
            );
    }
    
    /**
     * Example of module registration using config values.
     */
    public void registerModuleWithConfigDefaults(String moduleName, String host, int port) {
        // Use config for connection timeout
        var timeout = config.modules().connectionTimeout();
        
        // Use config for health check settings
        var healthInterval = config.consul().health().checkInterval();
        var deregisterAfter = config.consul().health().deregisterAfter();
        
        LOG.infof("Registering module %s with timeout=%s, health check every %s",
            moduleName, timeout, healthInterval);
        
        // Module registration still uses KV store for the actual data
        registryService.registerModule(
            moduleName,
            "impl-" + moduleName,
            host,
            port,
            "GRPC",
            "1.0.0",
            null,
            host,
            port,
            null
        ).await().indefinitely();
    }
    
    /**
     * Example showing config vs KV store usage.
     */
    public void demonstrateHybridApproach() {
        // Simple configs from consul-config
        LOG.infof("Engine running on gRPC port: %d", config.engine().grpcPort());
        LOG.infof("Auto-discovery enabled: %s", config.modules().autoDiscover());
        LOG.infof("Default cluster name: %s", config.defaultCluster().name());
        
        // Complex data from KV store
        var pipelines = pipelineService.listPipelines("default")
            .await().indefinitely();
        LOG.infof("Found %d pipelines in KV store", pipelines.size());
        
        var modules = registryService.listRegisteredModules()
            .await().indefinitely();
        LOG.infof("Found %d registered modules in KV store", modules.size());
    }
}