package com.rokkon.pipeline.engine.dev.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.List;
import java.util.Optional;

/**
 * Configuration for module auto-deployment in dev mode
 */
@ConfigMapping(prefix = "pipeline.dev.modules")
public interface DevModeModuleConfig {
    
    /**
     * Auto-deployment configuration
     */
    @WithName("auto-deploy")
    AutoDeploy autoDeploy();
    
    /**
     * Health check configuration
     */
    @WithName("health")
    HealthConfig health();
    
    interface AutoDeploy {
        /**
         * Whether auto-deployment is enabled
         */
        @WithDefault("true")
        boolean enabled();
        
        /**
         * List of modules to auto-deploy
         */
        List<ModuleConfig> modules();
        
        /**
         * Restart configuration
         */
        Restart restart();
        
        interface ModuleConfig {
            /**
             * Module name
             */
            String name();
            
            /**
             * Number of instances to deploy
             */
            @WithDefault("1")
            int instances();
            
            /**
             * Whether this module should be deployed
             */
            @WithDefault("true")
            boolean enabled();
            
            /**
             * Optional memory limit (e.g., "512m", "1g")
             */
            Optional<String> memoryLimit();
            
            /**
             * Optional CPU limit (e.g., "0.5", "2")
             */
            Optional<String> cpuLimit();
        }
        
        interface Restart {
            /**
             * Whether automatic restart is enabled
             */
            @WithDefault("true")
            boolean enabled();
            
            /**
             * Maximum restart attempts
             */
            @WithDefault("3")
            int maxAttempts();
            
            /**
             * Backoff between restart attempts in seconds
             */
            @WithDefault("30")
            int backoffSeconds();
        }
    }
    
    interface HealthConfig {
        /**
         * How often to check module health
         */
        @WithDefault("30s")
        String checkInterval();
        
        /**
         * Timeout for health checks
         */
        @WithDefault("10s")
        String timeout();
    }
}