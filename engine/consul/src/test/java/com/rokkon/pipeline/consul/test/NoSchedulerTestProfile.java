package com.rokkon.pipeline.consul.test;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class NoSchedulerTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.scheduler.enabled", "false"
        );
    }
}
