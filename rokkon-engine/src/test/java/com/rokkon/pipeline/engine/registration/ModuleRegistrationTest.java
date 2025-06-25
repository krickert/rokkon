package com.rokkon.pipeline.engine.registration;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Unit test for module registration using Quarkus dev mode.
 * This test runs with a real Consul instance via Testcontainers.
 */
@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
class ModuleRegistrationTest extends ModuleRegistrationTestBase {
    
    @ConfigProperty(name = "quarkus.http.test-port")
    int httpTestPort;
    
    @ConfigProperty(name = "quarkus.grpc.server.test-port", defaultValue = "0")
    int grpcTestPort;
    
    @Override
    protected String getBaseUrl() {
        return "http://localhost:" + httpTestPort;
    }
    
    @Override
    protected String getWorkingModuleHost() {
        return "localhost";
    }
    
    @Override
    protected int getWorkingModulePort() {
        // In unit tests, we don't have a real module running
        // Return a port that will fail validation (expected behavior)
        return 9090;
    }
    
    @Override
    protected String getHealthCheckHost() {
        return "localhost";
    }
    
    @Override
    protected int getHealthCheckPort() {
        // For unit tests, health checks will fail (no real module)
        return 9090;
    }
    
    @Override
    protected String getUnreachableHost() {
        return "non-existent-host-" + System.currentTimeMillis() + ".invalid";
    }
    
    @Override
    protected int getClosedPort() {
        // A high port unlikely to be in use
        return 54321;
    }
    
    @Override
    protected int getNonGrpcPort() {
        // Use the HTTP test port (not gRPC)
        return httpTestPort;
    }
}