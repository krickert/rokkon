package com.rokkon.modules.echo;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Health check for the echo service.
 * The engine can use this to verify the module is ready before registering it.
 */
@Readiness
@ApplicationScoped
public class EchoHealthCheck implements HealthCheck {
    
    @Override
    public HealthCheckResponse call() {
        // Echo service is always ready - it's just echoing data
        return HealthCheckResponse.named("echo-service")
                .status(true)
                .withData("version", "1.0.0")
                .withData("module-type", "echo-processor")
                .withData("grpc-port", "9001")
                .build();
    }
}