package com.rokkon.pipeline.events.cache;

import io.quarkus.cache.CacheManager;
import io.quarkus.cache.Cache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import java.util.Optional;

/**
 * Handles cache invalidation events triggered by Consul watches.
 * This ensures all engine instances stay in sync when data changes in Consul.
 */
@ApplicationScoped
public class CacheInvalidationHandler {
    
    private static final Logger LOG = Logger.getLogger(CacheInvalidationHandler.class);
    
    @Inject
    CacheManager cacheManager;
    
    /**
     * Handle pipeline definition changes by invalidating related caches
     */
    public void onPipelineDefinitionChanged(@Observes ConsulPipelineDefinitionChangedEvent event) {
        LOG.infof("Pipeline definition changed: %s - invalidating caches", event.pipelineId());
        
        // Invalidate the specific pipeline definition cache
        invalidateCache("pipeline-definitions", event.pipelineId());
        
        // Invalidate the pipeline list cache since the list may have changed
        invalidateAllCache("pipeline-definitions-list");
        
        // Invalidate existence cache
        invalidateCache("pipeline-definitions-exists", event.pipelineId());
        
        // Invalidate metadata cache
        invalidateCache("pipeline-metadata", event.pipelineId());
    }
    
    /**
     * Handle module registration changes by invalidating related caches
     */
    public void onModuleRegistrationChanged(@Observes ConsulModuleRegistrationChangedEvent event) {
        LOG.infof("Module registration changed: %s - invalidating caches", event.moduleId());
        
        // Invalidate all module-related caches
        invalidateAllCache("global-modules-list");
        invalidateAllCache("global-modules-enabled");
        invalidateCache("global-modules", event.moduleId());
        invalidateCache("module-health-status", event.moduleId());
    }
    
    /**
     * Handle cluster pipeline changes by invalidating related caches
     */
    public void onClusterPipelineChanged(@Observes ConsulClusterPipelineChangedEvent event) {
        LOG.infof("Cluster pipeline changed: %s/%s - invalidating caches", 
                  event.clusterName(), event.pipelineId());
        
        // Invalidate cluster pipeline caches
        invalidateCache("cluster-pipelines-list", event.clusterName());
        invalidateCache("cluster-pipelines", event.clusterName(), event.pipelineId());
    }
    
    /**
     * Invalidate a specific cache entry
     */
    private void invalidateCache(String cacheName, Object... keys) {
        try {
            Optional<Cache> cacheOpt = cacheManager.getCache(cacheName);
            if (cacheOpt.isPresent()) {
                Cache cache = cacheOpt.get();
                if (keys.length == 0) {
                    cache.invalidateAll().await().indefinitely();
                } else if (keys.length == 1) {
                    cache.invalidate(keys[0]).await().indefinitely();
                } else {
                    // For multi-key caches, construct a composite key
                    String compositeKey = String.join(":", 
                        java.util.Arrays.stream(keys)
                            .map(Object::toString)
                            .toArray(String[]::new));
                    cache.invalidate(compositeKey).await().indefinitely();
                }
                LOG.debugf("Invalidated cache %s for key(s): %s", cacheName, 
                          java.util.Arrays.toString(keys));
            } else {
                LOG.debugf("Cache %s not found - may not be initialized yet", cacheName);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to invalidate cache %s", cacheName);
        }
    }
    
    /**
     * Invalidate all entries in a cache
     */
    private void invalidateAllCache(String cacheName) {
        try {
            Optional<Cache> cacheOpt = cacheManager.getCache(cacheName);
            if (cacheOpt.isPresent()) {
                Cache cache = cacheOpt.get();
                cache.invalidateAll().await().indefinitely();
                LOG.debugf("Invalidated all entries in cache %s", cacheName);
            } else {
                LOG.debugf("Cache %s not found - may not be initialized yet", cacheName);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to invalidate all entries in cache %s", cacheName);
        }
    }
}