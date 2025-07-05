package com.rokkon.pipeline.engine.events;

import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Handles module lifecycle events and updates Consul KV accordingly
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class ModuleLifecycleEventHandler {
    
    private static final Logger LOG = Logger.getLogger(ModuleLifecycleEventHandler.class);
    
    @Inject
    GlobalModuleRegistryService moduleRegistry;
    
    public void onModuleLifecycleEvent(@Observes ModuleLifecycleEvent event) {
        LOG.infof("Module lifecycle event: %s - %s - %s", 
            event.getModuleName(), event.getEventType(), event.getMessage());
        
        // Update module status in Consul KV based on event type
        String status = mapEventTypeToStatus(event.getEventType());
        if (status != null) {
            updateModuleStatusInConsul(event.getModuleName(), status);
        }
        
        // Handle specific event types
        switch (event.getEventType()) {
            case DEPLOYED:
                // Module is deployed and ready to be registered
                LOG.infof("Module %s deployed successfully", event.getModuleName());
                break;
                
            case UNDEPLOYING:
                // Start deregistration process
                LOG.infof("Module %s is being undeployed", event.getModuleName());
                break;
                
            case DEPLOYMENT_FAILED:
                LOG.errorf("Module %s deployment failed: %s", 
                    event.getModuleName(), event.getMessage());
                if (event.getError() != null) {
                    LOG.errorf("Error details:", event.getError());
                }
                break;
                
            default:
                // Log other events
                break;
        }
    }
    
    private String mapEventTypeToStatus(ModuleLifecycleEvent.EventType eventType) {
        return switch (eventType) {
            case DEPLOYING -> "deploying";
            case DEPLOYED -> "deployed";
            case DEPLOYMENT_FAILED -> "failed";
            case UNDEPLOYING -> "undeploying";
            case UNDEPLOYED -> "undeployed";
            case REGISTERED -> "registered";
            case DEREGISTERED -> "deregistered";
            case HEALTH_CHECK_PASSED -> "healthy";
            case HEALTH_CHECK_FAILED -> "unhealthy";
        };
    }
    
    private void updateModuleStatusInConsul(String moduleName, String status) {
        // For now, just log - we'll implement Consul KV update later
        LOG.debugf("Would update module %s status to %s in Consul KV", moduleName, status);
        
        // TODO: When we have a proper Consul KV service:
        // String key = "pipeline/modules/" + moduleName + "/status";
        // consulKvService.putValue(key, status)
        //     .onSuccess(v -> LOG.debugf("Updated status for module %s to %s", moduleName, status))
        //     .onFailure(t -> LOG.warnf("Failed to update status for module %s: %s", moduleName, t.getMessage()));
    }
}