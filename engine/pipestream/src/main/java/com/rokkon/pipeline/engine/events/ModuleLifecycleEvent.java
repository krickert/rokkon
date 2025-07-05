package com.rokkon.pipeline.engine.events;

import java.time.Instant;

/**
 * Event fired when module lifecycle state changes
 */
public class ModuleLifecycleEvent {
    
    public enum EventType {
        DEPLOYING,
        DEPLOYED,
        DEPLOYMENT_FAILED,
        UNDEPLOYING,
        UNDEPLOYED,
        REGISTERED,
        DEREGISTERED,
        HEALTH_CHECK_PASSED,
        HEALTH_CHECK_FAILED
    }
    
    private final String moduleName;
    private final EventType eventType;
    private final String message;
    private final Instant timestamp;
    private final String moduleId;  // Optional - for registered modules
    private final Throwable error;  // Optional - for failures
    
    public ModuleLifecycleEvent(String moduleName, EventType eventType, String message) {
        this(moduleName, eventType, message, null, null);
    }
    
    public ModuleLifecycleEvent(String moduleName, EventType eventType, String message, String moduleId) {
        this(moduleName, eventType, message, moduleId, null);
    }
    
    public ModuleLifecycleEvent(String moduleName, EventType eventType, String message, String moduleId, Throwable error) {
        this.moduleName = moduleName;
        this.eventType = eventType;
        this.message = message;
        this.moduleId = moduleId;
        this.error = error;
        this.timestamp = Instant.now();
    }
    
    // Getters
    public String getModuleName() {
        return moduleName;
    }
    
    public EventType getEventType() {
        return eventType;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public String getModuleId() {
        return moduleId;
    }
    
    public Throwable getError() {
        return error;
    }
    
    public boolean isError() {
        return error != null || eventType == EventType.DEPLOYMENT_FAILED || eventType == EventType.HEALTH_CHECK_FAILED;
    }
    
    @Override
    public String toString() {
        return "ModuleLifecycleEvent{" +
               "moduleName='" + moduleName + '\'' +
               ", eventType=" + eventType +
               ", message='" + message + '\'' +
               ", timestamp=" + timestamp +
               ", moduleId='" + moduleId + '\'' +
               ", error=" + error +
               '}';
    }
}