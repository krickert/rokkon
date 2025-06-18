package com.rokkon.search.config.pipeline.consul;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class ConsulTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.log.level", "INFO",
            "quarkus.log.category.\"com.rokkon\".level", "DEBUG",
            "quarkus.consul-config.enabled", "false", // We'll use manual HTTP calls for testing
            "quarkus.devservices.enabled", "false"     // We'll manage Consul container manually
        );
    }
    
    @Override
    public String getConfigProfile() {
        return "consul-test";
    }
}