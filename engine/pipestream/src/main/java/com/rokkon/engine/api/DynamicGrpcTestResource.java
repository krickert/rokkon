package com.rokkon.engine.api;

import com.rokkon.pipeline.engine.grpc.DynamicGrpcClientFactory;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.MutinyPipeStepProcessorGrpc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ServiceMetadata;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Test endpoint to verify dynamic gRPC service discovery and routing.
 * This directly tests the DynamicGrpcClientFactory without needing pipeline infrastructure.
 */
@Path("/api/v1/test/dynamic-grpc")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DynamicGrpcTestResource {
    
    private static final Logger LOG = LoggerFactory.getLogger(DynamicGrpcTestResource.class);
    
    @Inject
    DynamicGrpcClientFactory grpcClientFactory;
    
    /**
     * Test dynamic gRPC by calling a service discovered from Consul.
     * 
     * @param serviceName The service name to call (e.g., "pipeline-engine", "test-echo")
     * @param testMessage Optional test message to send
     * @return Response with the result
     */
    @POST
    @Path("/{serviceName}")
    public Uni<Response> testDynamicGrpc(
            @PathParam("serviceName") String serviceName,
            @QueryParam("message") @DefaultValue("Hello from dynamic gRPC test") String testMessage) {
        
        LOG.info("Testing dynamic gRPC call to service: {}", serviceName);
        
        // Create a test document
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setBody(testMessage)
                .setSourceUri("test://dynamic-grpc")
                .putMetadata("test_timestamp", Instant.now().toString())
                .putMetadata("target_service", serviceName)
                .build();
        
        // Create minimal metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-dynamic-grpc")
                .setPipeStepName("test-step")
                .setStreamId(UUID.randomUUID().toString())
                .setCurrentHopNumber(1)
                .putContextParams("test_service", serviceName)
                .putContextParams("test_timestamp", Instant.now().toString())
                .build();
        
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(metadata)
                .build();
        
        // Use the dynamic gRPC factory to get a client for the service
        return grpcClientFactory.getMutinyClientForService(serviceName)
                .flatMap(client -> {
                    LOG.info("Successfully got gRPC client for service: {}", serviceName);
                    
                    // Call the service
                    return client.processData(request)
                            .map(response -> {
                                LOG.info("Received response from {}: success={}", 
                                        serviceName, response.getSuccess());
                                
                                // Build response
                                Map<String, Object> result = Map.of(
                                    "service", serviceName,
                                    "success", response.getSuccess(),
                                    "outputDocId", response.hasOutputDoc() ? 
                                            response.getOutputDoc().getId() : "none",
                                    "outputBody", response.hasOutputDoc() ? 
                                            response.getOutputDoc().getBody() : "none",
                                    "logs", response.getProcessorLogsList(),
                                    "timestamp", Instant.now().toString()
                                );
                                
                                return Response.ok(result).build();
                            });
                })
                .onFailure().recoverWithItem(error -> {
                    LOG.error("Failed to call service {}", serviceName, error);
                    
                    Map<String, Object> errorResult = Map.of(
                        "service", serviceName,
                        "success", false,
                        "error", error.getMessage(),
                        "errorType", error.getClass().getSimpleName(),
                        "timestamp", Instant.now().toString()
                    );
                    
                    return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                            .entity(errorResult)
                            .build();
                });
    }
    
    /**
     * Test endpoint to verify the test resource itself is working.
     */
    @GET
    @Path("/health")
    public Response testHealth() {
        return Response.ok(Map.of(
            "status", "UP",
            "service", "DynamicGrpcTestResource",
            "grpcFactoryAvailable", grpcClientFactory != null
        )).build();
    }
    
    /**
     * List available services that can be called.
     */
    @GET
    @Path("/services")
    public Uni<Response> listAvailableServices() {
        // For now, just return known services
        // In a real implementation, this could query Consul
        return Uni.createFrom().item(Response.ok(Map.of(
            "availableServices", Map.of(
                "pipeline-engine", "The engine itself (for self-test)",
                "test-echo", "Echo test service"
            ),
            "usage", "POST to /api/v1/test/dynamic-grpc/{serviceName}"
        )).build());
    }
}