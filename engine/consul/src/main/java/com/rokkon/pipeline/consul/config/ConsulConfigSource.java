package com.rokkon.pipeline.consul.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;
import java.util.Optional;

/**
 * Configuration properties for Consul-based settings.
 * These are simple properties that can be managed through consul-config.
 * Complex data like pipelines and module registrations remain in KV store.
 */
@ConfigMapping(prefix = "rokkon", namingStrategy = NamingStrategy.KEBAB_CASE)
public interface ConsulConfigSource {
    
    /**
     * Engine configuration
     */
    @WithName("engine")
    EngineConfig engine();
    
    /**
     * Consul-specific configuration
     */
    @WithName("consul")
    ConsulConfig consul();
    
    /**
     * Module management configuration
     */
    @WithName("modules")
    ModulesConfig modules();
    
    /**
     * Default cluster configuration
     */
    @WithName("default-cluster")
    DefaultClusterConfig defaultCluster();
    
    interface EngineConfig {
        /**
         * gRPC server port
         */
        @WithDefault("49000")
        int grpcPort();
        
        /**
         * REST API port
         */
        @WithDefault("8080")
        int restPort();
        
        /**
         * Engine instance ID (auto-generated if not set)
         */
        Optional<String> instanceId();
        
        /**
         * Enable debug logging
         */
        @WithDefault("false")
        boolean debug();
    }
    
    interface ConsulConfig {
        /**
         * Cleanup configuration
         */
        @WithName("cleanup")
        CleanupConfig cleanup();
        
        /**
         * Health check configuration
         */
        @WithName("health")
        HealthConfig health();
        
        interface CleanupConfig {
            /**
             * Enable automatic zombie instance cleanup
             */
            @WithDefault("true")
            boolean enabled();
            
            /**
             * Interval between cleanup runs
             */
            @WithDefault("5m")
            Duration interval();
            
            /**
             * How long to wait before considering an unhealthy instance a zombie
             */
            @WithDefault("2m")
            Duration zombieThreshold();
            
            /**
             * Enable cleanup of stale whitelist entries
             */
            @WithDefault("true")
            boolean cleanupStaleWhitelist();
        }
        
        interface HealthConfig {
            /**
             * Health check interval for registered modules
             */
            @WithDefault("10s")
            Duration checkInterval();
            
            /**
             * Time after which to deregister failed services
             */
            @WithDefault("1m")
            Duration deregisterAfter();
            
            /**
             * Timeout for health check connections
             */
            @WithDefault("5s")
            Duration timeout();
        }
    }
    
    interface ModulesConfig {
        /**
         * Enable automatic module discovery from Consul services
         */
        @WithDefault("false")
        boolean autoDiscover();
        
        /**
         * Service name prefix for auto-discovery
         */
        @WithDefault("module-")
        String servicePrefix();
        
        /**
         * Require modules to be explicitly whitelisted before use
         */
        @WithDefault("true")
        boolean requireWhitelist();
        
        /**
         * Default module connection timeout
         */
        @WithDefault("30s")
        Duration connectionTimeout();
        
        /**
         * Maximum number of instances per module type
         */
        @WithDefault("10")
        int maxInstancesPerModule();
    }
    
    interface DefaultClusterConfig {
        /**
         * Name of the default cluster
         */
        @WithDefault("default")
        String name();
        
        /**
         * Auto-create default cluster on startup
         */
        @WithDefault("true")
        boolean autoCreate();
        
        /**
         * Description for auto-created default cluster
         */
        @WithDefault("Default cluster for Rokkon pipelines")
        String description();
    }
}