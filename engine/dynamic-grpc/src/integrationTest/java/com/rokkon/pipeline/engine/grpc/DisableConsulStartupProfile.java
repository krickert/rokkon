package com.rokkon.pipeline.engine.grpc;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class DisableConsulStartupProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "consul.enabled", "false",
            "quarkus.consul-config.enabled", "false"
        );
    }
}