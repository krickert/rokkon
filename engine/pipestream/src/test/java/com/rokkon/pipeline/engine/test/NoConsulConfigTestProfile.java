package com.rokkon.pipeline.engine.test;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.HashMap;
import java.util.Map;

/**
 * Test profile that disables Consul configuration for unit tests.
 * 
 * This profile is critical because the engine tries to fetch configuration
 * from Consul at startup. Without this profile, unit tests fail because:
 * 1. They try to connect to Consul (which isn't running)
 * 2. Configuration fetching happens BEFORE CDI context, so we can't mock it
 * 
 * This profile provides all required configuration directly, bypassing Consul.
 */
public class NoConsulConfigTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        
        // Disable Consul completely
        config.put("quarkus.consul-config.enabled", "false");
        config.put("quarkus.consul.enabled", "false");
        config.put("quarkus.consul.devservices.enabled", "false");
        
        // Provide required engine configuration
        config.put("rokkon.cluster.name", "test-cluster");
        config.put("rokkon.engine.name", "test-engine");
        config.put("rokkon.engine.startup.create-cluster", "false");
        
        // Module whitelist configuration
        config.put("rokkon.modules.whitelist[0]", "echo");
        config.put("rokkon.modules.whitelist[1]", "test-module");
        config.put("rokkon.modules.whitelist[2]", "chunker");
        config.put("rokkon.modules.whitelist[3]", "parser");
        
        // gRPC configuration - use random ports for tests
        config.put("quarkus.grpc.server.port", "0");
        config.put("quarkus.grpc.server.test-port", "0");
        config.put("quarkus.grpc.server.use-separate-server", "false");
        
        // HTTP configuration - use random ports
        config.put("quarkus.http.test-port", "0");
        config.put("quarkus.http.test-ssl-port", "0");
        
        // Disable Stork service discovery (we'll mock it)
        config.put("quarkus.stork.service-discovery.enabled", "false");
        
        // Disable health checks for unit tests
        config.put("quarkus.health.extensions.enabled", "false");
        config.put("quarkus.health.liveness.enabled", "false");
        config.put("quarkus.health.readiness.enabled", "false");
        
        // Use mock validators for unit tests
        config.put("test.validators.mode", "empty");
        config.put("test.validators.use-real", "false");
        
        // Set test logging
        config.put("quarkus.log.level", "INFO");
        config.put("quarkus.log.category.\"com.rokkon\".level", "DEBUG");
        config.put("quarkus.log.category.\"io.quarkus.consul\".level", "WARN");
        
        // Disable scheduled tasks for tests
        config.put("quarkus.scheduler.enabled", "false");
        
        // Cache configuration
        config.put("quarkus.cache.caffeine.\"grpc-channels\".expire-after-write", "5M");
        config.put("quarkus.cache.caffeine.\"grpc-channels\".maximum-size", "100");
        
        return config;
    }
    
    @Override
    public String getConfigProfile() {
        return "test-no-consul";
    }
}