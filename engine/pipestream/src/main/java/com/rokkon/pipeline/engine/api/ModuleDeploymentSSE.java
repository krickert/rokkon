package com.rokkon.pipeline.engine.api;

import io.quarkus.arc.profile.IfBuildProfile;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;

/**
 * Server-Sent Events endpoint for real-time module deployment updates
 */
@Path("/api/v1/module-deployment")
@ApplicationScoped
@IfBuildProfile("dev")
public class ModuleDeploymentSSE {
    
    private static final Logger LOG = Logger.getLogger(ModuleDeploymentSSE.class);
    
    // Broadcast processor to handle multiple subscribers
    private final BroadcastProcessor<DeploymentEvent> eventProcessor = BroadcastProcessor.create();
    
    @GET
    @Path("/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<DeploymentEvent> stream() {
        LOG.info("New SSE client connected for deployment events");
        return eventProcessor;
    }
    
    /**
     * Send deployment started event to all connected clients
     */
    public void notifyDeploymentStarted(String moduleName) {
        DeploymentEvent event = new DeploymentEvent(
            "deployment_started",
            moduleName,
            "started",
            "Starting module deployment..."
        );
        eventProcessor.onNext(event);
    }
    
    /**
     * Send deployment progress event to all connected clients
     */
    public void notifyDeploymentProgress(String moduleName, String status, String message) {
        DeploymentEvent event = new DeploymentEvent(
            "deployment_progress",
            moduleName,
            status,
            message
        );
        eventProcessor.onNext(event);
    }
    
    /**
     * Send deployment completed event to all connected clients
     */
    public void notifyDeploymentCompleted(String moduleName, boolean success, String message) {
        DeploymentEvent event = new DeploymentEvent(
            success ? "deployment_success" : "deployment_failed",
            moduleName,
            success ? "deployed" : "failed",
            message
        );
        eventProcessor.onNext(event);
    }
    
    /**
     * Send module registered event to all connected clients
     */
    public void notifyModuleRegistered(String moduleName) {
        DeploymentEvent event = new DeploymentEvent(
            "module_registered",
            moduleName,
            "registered",
            "Module successfully registered with engine"
        );
        eventProcessor.onNext(event);
    }
    
    /**
     * Send module undeploying event to all connected clients
     */
    public void notifyModuleUndeploying(String moduleName) {
        DeploymentEvent event = new DeploymentEvent(
            "module_undeploying",
            moduleName,
            "undeploying",
            "Module is being undeployed..."
        );
        eventProcessor.onNext(event);
    }
    
    /**
     * Send module undeployed event to all connected clients
     */
    public void notifyModuleUndeployed(String moduleName) {
        DeploymentEvent event = new DeploymentEvent(
            "module_undeployed",
            moduleName,
            "undeployed",
            "Module undeployed successfully"
        );
        eventProcessor.onNext(event);
    }
    
    /**
     * Event class for deployment updates
     */
    public static class DeploymentEvent {
        public String type;
        public String module;
        public String status;
        public String message;
        public long timestamp;
        
        // Default constructor for JSON
        public DeploymentEvent() {}
        
        public DeploymentEvent(String type, String module, String status, String message) {
            this.type = type;
            this.module = module;
            this.status = status;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }
}