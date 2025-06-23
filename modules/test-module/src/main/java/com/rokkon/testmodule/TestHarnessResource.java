package com.rokkon.testmodule;

import com.google.protobuf.util.JsonFormat;
import com.rokkon.test.ModuleStatus;
import com.rokkon.test.TestHarness;
import io.quarkus.grpc.GrpcService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/**
 * REST endpoint for the test harness.
 * Provides HTTP access to module status and testing capabilities.
 */
@Path("/test-harness")
public class TestHarnessResource {
    
    @Inject
    @GrpcService
    TestHarnessServiceImpl testHarness;
    
    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public String getStatus() throws Exception {
        ModuleStatus status = testHarness.getModuleStatus(com.google.protobuf.Empty.getDefaultInstance())
                .await().indefinitely();
        
        // Convert protobuf to JSON
        return JsonFormat.printer()
                .includingDefaultValueFields()
                .print(status);
    }
    
    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getHealth() {
        return Map.of(
                "status", "UP",
                "module", "test-harness",
                "grpcPort", 49094,
                "httpPort", 8094
        );
    }
    
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Test Harness Module - Use /test-harness/status for module status";
    }
}