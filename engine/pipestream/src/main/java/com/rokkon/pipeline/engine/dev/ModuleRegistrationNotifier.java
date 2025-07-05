package com.rokkon.pipeline.engine.dev;

import com.rokkon.pipeline.engine.api.ModuleDeploymentSSE;
import com.rokkon.pipeline.events.ModuleRegistrationResponseEvent;
import io.quarkus.arc.Arc;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

/**
 * Notifies WebSocket clients when modules are registered
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class ModuleRegistrationNotifier {
    
    private static final Logger LOG = Logger.getLogger(ModuleRegistrationNotifier.class);
    
    /**
     * Observes module registration events and sends WebSocket notifications
     */
    public void onModuleRegistered(@Observes ModuleRegistrationResponseEvent event) {
        if (event.success()) {
            LOG.infof("Module registered: %s", event.moduleId());
            
            // Extract module name from moduleId (e.g., "echo-module-xyz" -> "echo")
            String moduleName = extractModuleName(event.moduleId());
            
            // Send SSE notification
            try {
                var sseInstance = Arc.container().instance(ModuleDeploymentSSE.class);
                if (sseInstance.isAvailable()) {
                    sseInstance.get().notifyModuleRegistered(moduleName);
                }
            } catch (Exception e) {
                LOG.debugf("Failed to send SSE notification: %s", e.getMessage());
            }
        }
    }
    
    private String extractModuleName(String moduleId) {
        if (moduleId == null) return "unknown";
        
        // Handle patterns like "echo-module-xyz" -> "echo"
        if (moduleId.contains("-module-")) {
            return moduleId.substring(0, moduleId.indexOf("-module"));
        }
        
        // Handle patterns like "echo-module" -> "echo"
        if (moduleId.endsWith("-module")) {
            return moduleId.substring(0, moduleId.length() - 7);
        }
        
        return moduleId;
    }
}