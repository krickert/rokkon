package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import com.rokkon.pipeline.engine.event.ConsulStatusEvent;
import com.rokkon.pipeline.events.ConsulConnectionEvent;
import io.quarkus.runtime.LaunchMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Instant;

@ApplicationScoped
public class ConsulHealthService {
    
    public enum ConsulStatus {
        UP,
        DOWN,
        UNKNOWN,
        STARTING  // New state for when dev container is starting
    }
    
    public static class ConsulHealthStatus {
        private final ConsulStatus status;
        private final Instant lastChecked;
        private final String error;
        
        public ConsulHealthStatus(ConsulStatus status, Instant lastChecked, String error) {
            this.status = status;
            this.lastChecked = lastChecked;
            this.error = error;
        }
        
        public ConsulStatus getStatus() {
            return status;
        }
        
        public Instant getLastChecked() {
            return lastChecked;
        }
        
        public String getError() {
            return error;
        }
    }
    
    @Inject
    Event<ConsulStatusEvent> statusEvent;
    
    @Inject
    ConsulConnectionManager connectionManager;
    
    private volatile ConsulHealthStatus currentStatus = 
        new ConsulHealthStatus(ConsulStatus.UNKNOWN, Instant.now(), null);
    
    public void updateStatus(ConsulStatus status, String error) {
        ConsulStatus previousStatus = this.currentStatus.status;
        this.currentStatus = new ConsulHealthStatus(status, Instant.now(), error);
        
        // Fire event if status changed
        if (previousStatus != status) {
            boolean isDevMode = LaunchMode.current().equals(LaunchMode.DEVELOPMENT);
            statusEvent.fire(new ConsulStatusEvent(status, error, isDevMode));
        }
    }
    
    public ConsulHealthStatus getStatus() {
        return currentStatus;
    }
    
    public boolean isHealthy() {
        return currentStatus.status == ConsulStatus.UP;
    }
    
    // Listen for connection events from ConsulConnectionManager
    void onConsulConnectionEvent(@Observes ConsulConnectionEvent event) {
        switch (event.getType()) {
            case CONNECTED -> updateStatus(ConsulStatus.UP, null);
            case DISCONNECTED -> updateStatus(ConsulStatus.DOWN, "Disconnected");
            case CONNECTION_FAILED -> updateStatus(ConsulStatus.DOWN, event.getMessage());
        }
    }
}