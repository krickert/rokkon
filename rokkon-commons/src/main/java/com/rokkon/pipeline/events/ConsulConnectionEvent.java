package com.rokkon.pipeline.events;

/**
 * Event fired when Consul connection status changes.
 */
public class ConsulConnectionEvent {
    
    public enum Type {
        CONNECTED,
        DISCONNECTED,
        CONNECTION_FAILED
    }
    
    private final Type type;
    private final ConsulConnectionConfig config;
    private final String message;
    
    public ConsulConnectionEvent(Type type, ConsulConnectionConfig config, String message) {
        this.type = type;
        this.config = config;
        this.message = message;
    }
    
    public Type getType() {
        return type;
    }
    
    public ConsulConnectionConfig getConfig() {
        return config;
    }
    
    public String getMessage() {
        return message;
    }
    
    /**
     * Simple configuration record for Consul connection.
     */
    public static record ConsulConnectionConfig(String host, int port, boolean connected) {}
}