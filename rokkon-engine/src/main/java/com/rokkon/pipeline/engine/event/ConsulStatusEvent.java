package com.rokkon.pipeline.engine.event;

import com.rokkon.pipeline.engine.service.ConsulHealthService;

public class ConsulStatusEvent {
    
    private final ConsulHealthService.ConsulStatus status;
    private final String error;
    private final boolean isDevMode;
    
    public ConsulStatusEvent(ConsulHealthService.ConsulStatus status, String error, boolean isDevMode) {
        this.status = status;
        this.error = error;
        this.isDevMode = isDevMode;
    }
    
    public ConsulHealthService.ConsulStatus getStatus() {
        return status;
    }
    
    public String getError() {
        return error;
    }
    
    public boolean isDevMode() {
        return isDevMode;
    }
}