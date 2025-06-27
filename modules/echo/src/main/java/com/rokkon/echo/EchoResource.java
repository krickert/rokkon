package com.rokkon.echo;

import com.google.protobuf.util.JsonFormat;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.*;
import com.rokkon.search.util.SampleDataLoader;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Map;

@Path("/api/echo")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Echo Service", description = "Test endpoint for the Echo gRPC module")
public class EchoResource {
    
    private static final Logger LOG = Logger.getLogger(EchoResource.class);
    
    @GrpcClient("echoService")
    PipeStepProcessor echoService;
    
    @POST
    @Path("/process")
    @Operation(summary = "Process a document through the Echo service",
               description = "Sends a document to the Echo gRPC service which will echo it back unchanged")
    @APIResponses({
        @APIResponse(responseCode = "200", 
                     description = "Document processed successfully",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON)),
        @APIResponse(responseCode = "400", 
                     description = "Bad request - invalid input",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON)),
        @APIResponse(responseCode = "500", 
                     description = "Internal server error",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON))
    })
    public Uni<Response> processDocument(
            @RequestBody(description = "Document to process", required = true,
                        content = @Content(schema = @Schema(implementation = EchoRequest.class)))
            Map<String, Object> input) {
        LOG.infof("REST endpoint received: %s", input);
        
        try {
            // Build PipeDoc from input
            PipeDoc.Builder docBuilder = PipeDoc.newBuilder()
                    .setId(input.getOrDefault("id", "test-doc-" + System.currentTimeMillis()).toString())
                    .setDocumentType(input.getOrDefault("type", "test").toString());
            
            // Add content if provided
            if (input.containsKey("content")) {
                docBuilder.setBody(input.get("content").toString());
            }
            
            // Add metadata if provided
            if (input.containsKey("metadata") && input.get("metadata") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> metadata = (Map<String, String>) input.get("metadata");
                docBuilder.putAllMetadata(metadata);
            }
            
            // Build the request
            ProcessRequest request = ProcessRequest.newBuilder()
                    .setDocument(docBuilder.build())
                    .setConfig(ProcessConfiguration.newBuilder().build())
                    .setMetadata(ServiceMetadata.newBuilder()
                            .setPipelineName("web-test")
                            .setPipeStepName("echo")
                            .setStreamId("web-" + System.currentTimeMillis())
                            .setCurrentHopNumber(1)
                            .build())
                    .build();
            
            // Call the gRPC service
            return echoService.processData(request)
                    .map(response -> {
                        try {
                            // Convert response to JSON
                            String jsonResponse = JsonFormat.printer()
                                    .includingDefaultValueFields()
                                    .print(response);
                            
                            return Response.ok(jsonResponse).build();
                        } catch (Exception e) {
                            LOG.error("Failed to convert response to JSON", e);
                            return Response.serverError()
                                    .entity(Map.of("error", "Failed to format response"))
                                    .build();
                        }
                    })
                    .onFailure().recoverWithItem(throwable -> {
                        LOG.error("Failed to process document", throwable);
                        return Response.serverError()
                                .entity(Map.of("error", throwable.getMessage()))
                                .build();
                    });
                    
        } catch (Exception e) {
            LOG.error("Failed to build request", e);
            return Uni.createFrom().item(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", e.getMessage()))
                            .build()
            );
        }
    }
    
    @GET
    @Path("/info")
    @Operation(summary = "Get Echo service information",
               description = "Returns basic information about the Echo module")
    @APIResponse(responseCode = "200", 
                 description = "Service information retrieved successfully",
                 content = @Content(mediaType = MediaType.APPLICATION_JSON))
    public Uni<Response> getServiceInfo() {
        // Call without test request - just get basic info
        RegistrationRequest request = RegistrationRequest.newBuilder().build();
        
        return echoService.getServiceRegistration(request)
                .map(registration -> Response.ok(Map.of(
                        "moduleName", registration.getModuleName(),
                        "hasSchema", registration.hasJsonConfigSchema(),
                        "healthCheckPassed", registration.getHealthCheckPassed(),
                        "healthCheckMessage", registration.getHealthCheckMessage()
                )).build())
                .onFailure().recoverWithItem(throwable -> 
                    Response.serverError()
                            .entity(Map.of("error", throwable.getMessage()))
                            .build()
                );
    }
    
    @GET
    @Path("/health")
    @Operation(summary = "Health check",
               description = "Simple health check endpoint")
    @APIResponse(responseCode = "200", 
                 description = "Service is healthy",
                 content = @Content(mediaType = MediaType.APPLICATION_JSON))
    public Response health() {
        return Response.ok(Map.of("status", "UP")).build();
    }
    
    @POST
    @Path("/process-sample")
    @Operation(summary = "Process a sample document",
               description = "Processes one of the pre-loaded sample documents through the Echo service")
    @APIResponses({
        @APIResponse(responseCode = "200", 
                     description = "Sample document processed successfully",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON)),
        @APIResponse(responseCode = "500", 
                     description = "Internal server error",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON))
    })
    public Uni<Response> processSampleDocument(
            @QueryParam("sample") @DefaultValue("1") int sampleNumber) {
        
        try {
            // Load the sample PipeDoc from ProcessResponse (tika response)
            String filename = "sample-pipestream-" + sampleNumber + ".bin";
            PipeDoc sampleDoc = SampleDataLoader.loadSamplePipeDocFromResponse(filename);
            
            if (sampleDoc == null) {
                return Uni.createFrom().item(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "Sample " + sampleNumber + " does not contain a document"))
                            .build()
                );
            }
            
            // Build the request with the sample document
            ProcessRequest request = ProcessRequest.newBuilder()
                    .setDocument(sampleDoc)
                    .setConfig(ProcessConfiguration.newBuilder().build())
                    .setMetadata(ServiceMetadata.newBuilder()
                            .setPipelineName("sample-test")
                            .setPipeStepName("echo")
                            .setStreamId("sample-" + System.currentTimeMillis())
                            .setCurrentHopNumber(1)
                            .build())
                    .build();
            
            // Call the gRPC service
            return echoService.processData(request)
                    .map(response -> {
                        try {
                            // Convert response to JSON
                            String jsonResponse = JsonFormat.printer()
                                    .includingDefaultValueFields()
                                    .print(response);
                            
                            return Response.ok(jsonResponse).build();
                        } catch (Exception e) {
                            LOG.error("Failed to convert response to JSON", e);
                            return Response.serverError()
                                    .entity(Map.of("error", "Failed to format response"))
                                    .build();
                        }
                    })
                    .onFailure().recoverWithItem(throwable -> {
                        LOG.error("Failed to process sample document", throwable);
                        return Response.serverError()
                                .entity(Map.of("error", throwable.getMessage()))
                                .build();
                    });
                    
        } catch (IOException e) {
            LOG.error("Failed to load sample document", e);
            return Uni.createFrom().item(
                    Response.serverError()
                            .entity(Map.of("error", "Failed to load sample: " + e.getMessage()))
                            .build()
            );
        }
    }
    
    // Schema classes for OpenAPI documentation
    @Schema(name = "EchoRequest", description = "Request payload for echo processing")
    public static class EchoRequest {
        @Schema(description = "Document ID", example = "test-doc-1", required = true)
        public String id;
        
        @Schema(description = "Document type", example = "test", required = true)
        public String type;
        
        @Schema(description = "Document content", example = "This is test content", required = true)
        public String content;
        
        @Schema(description = "Document metadata", example = "{\"author\": \"John Doe\"}")
        public Map<String, String> metadata;
    }
}