package com.rokkon.pipeline.engine.api;

import io.quarkus.arc.profile.IfBuildProfile;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

/**
 * REST API for module lifecycle management.
 * Provides operations for deploying, monitoring, and managing modules in dev mode.
 */
@Path("/api/v1/module-management")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Module Management", description = "Module lifecycle and deployment management")
public class ModuleManagementResource {

    private static final Logger LOG = Logger.getLogger(ModuleManagementResource.class);

    // TODO: Inject services when implemented
    // @Inject
    // ModuleDeploymentService deploymentService;
    
    // @Inject
    // ModuleStatusService statusService;

    /**
     * Response for module operations
     */
    @Schema(description = "Module operation response")
    public record ModuleOperationResponse(
        @Schema(description = "Success indicator", example = "true")
        boolean success,
        
        @Schema(description = "Response message", example = "Module deployed successfully")
        String message,
        
        @Schema(description = "Additional details", implementation = Object.class)
        Map<String, Object> details
    ) {}

    /**
     * Module status information
     */
    @Schema(description = "Module status")
    public record ModuleStatus(
        @Schema(description = "Module name", example = "echo")
        String name,
        
        @Schema(description = "Current status", example = "running", enumeration = {"deployed", "running", "stopped", "error"})
        String status,
        
        @Schema(description = "Health status", example = "healthy", enumeration = {"healthy", "unhealthy", "unknown"})
        String health,
        
        @Schema(description = "Container ID if deployed", example = "abc123def456")
        String containerId,
        
        @Schema(description = "Sidecar container ID", example = "def456ghi789")
        String sidecarId,
        
        @Schema(description = "Assigned ports")
        Map<String, Integer> ports
    ) {}

    /**
     * Available module information
     */
    @Schema(description = "Available module definition")
    public record AvailableModule(
        @Schema(description = "Module name", example = "echo")
        String name,
        
        @Schema(description = "Module type", example = "PROCESSOR")
        String type,
        
        @Schema(description = "Docker image", example = "rokkon/echo-module:latest")
        String image,
        
        @Schema(description = "Required memory", example = "1G")
        String memory,
        
        @Schema(description = "Default ports")
        Map<String, Integer> defaultPorts,
        
        @Schema(description = "Whether module is available for deployment", example = "true")
        boolean available
    ) {}

    @GET
    @Path("/available")
    @Operation(
        summary = "List available modules",
        description = "Returns a list of all modules available for deployment"
    )
    @APIResponse(
        responseCode = "200",
        description = "List of available modules",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                type = SchemaType.ARRAY,
                implementation = AvailableModule.class
            )
        )
    )
    public Uni<List<AvailableModule>> listAvailableModules() {
        LOG.info("Listing available modules");
        
        // TODO: Implement when service is ready
        // For now, return static list
        return Uni.createFrom().item(List.of(
            new AvailableModule("echo", "PROCESSOR", "rokkon/echo-module:latest", "1G", 
                Map.of("http", 39090, "grpc", 49090), true),
            new AvailableModule("test", "PROCESSOR", "rokkon/test-module:latest", "1G", 
                Map.of("http", 39095, "grpc", 49095), true),
            new AvailableModule("parser", "PROCESSOR", "rokkon/parser-module:latest", "1G", 
                Map.of("http", 39093, "grpc", 49093), true),
            new AvailableModule("chunker", "PROCESSOR", "pipeline/chunker:latest", "4G", 
                Map.of("http", 39092, "grpc", 49092), true),
            new AvailableModule("embedder", "PROCESSOR", "pipeline/embedder:latest", "8G", 
                Map.of("http", 39094, "grpc", 49094), true)
        ));
    }

    @GET
    @Path("/deployed")
    @Operation(
        summary = "List deployed modules",
        description = "Returns a list of currently deployed modules with their status"
    )
    @APIResponse(
        responseCode = "200",
        description = "List of deployed modules",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                type = SchemaType.ARRAY,
                implementation = ModuleStatus.class
            )
        )
    )
    public Uni<List<ModuleStatus>> listDeployedModules() {
        LOG.info("Listing deployed modules");
        
        // TODO: Implement when service is ready
        return Uni.createFrom().item(List.of());
    }

    @GET
    @Path("/{moduleName}/status")
    @Operation(
        summary = "Get module status",
        description = "Returns detailed status information for a specific module"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Module status retrieved successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ModuleStatus.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Module not found"
        )
    })
    public Uni<Response> getModuleStatus(
            @Parameter(description = "Module name", required = true, example = "echo")
            @PathParam("moduleName") String moduleName) {
        
        LOG.infof("Getting status for module: %s", moduleName);
        
        // TODO: Implement when service is ready
        return Uni.createFrom().item(
            Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Module not found: " + moduleName))
                .build()
        );
    }

    @POST
    @Path("/{moduleName}/deploy")
    @Operation(
        summary = "Deploy a module",
        description = "Deploys a module with its sidecar container (dev mode only)"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "202",
            description = "Module deployment initiated",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ModuleOperationResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid module or configuration"
        ),
        @APIResponse(
            responseCode = "409",
            description = "Module already deployed"
        ),
        @APIResponse(
            responseCode = "503",
            description = "Service not available (not in dev mode)"
        )
    })
    public Uni<Response> deployModule(
            @Parameter(description = "Module name", required = true, example = "echo")
            @PathParam("moduleName") String moduleName) {
        
        LOG.infof("Request to deploy module: %s", moduleName);
        
        // Check if we're in dev mode
        String profile = System.getProperty("quarkus.profile", "prod");
        if (!"dev".equals(profile)) {
            return Uni.createFrom().item(
                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(new ModuleOperationResponse(false, 
                        "Module deployment is only available in dev mode", null))
                    .build()
            );
        }
        
        // TODO: Implement when service is ready
        return Uni.createFrom().item(
            Response.status(Response.Status.ACCEPTED)
                .entity(new ModuleOperationResponse(true, 
                    "Module deployment initiated", 
                    Map.of("module", moduleName, "status", "deploying")))
                .build()
        );
    }

    @DELETE
    @Path("/{moduleName}")
    @Operation(
        summary = "Stop a module",
        description = "Stops a deployed module and removes its containers"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Module stopped successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ModuleOperationResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Module not found"
        )
    })
    public Uni<Response> stopModule(
            @Parameter(description = "Module name", required = true, example = "echo")
            @PathParam("moduleName") String moduleName) {
        
        LOG.infof("Request to stop module: %s", moduleName);
        
        // TODO: Implement when service is ready
        return Uni.createFrom().item(
            Response.status(Response.Status.NOT_FOUND)
                .entity(new ModuleOperationResponse(false, 
                    "Module not found: " + moduleName, null))
                .build()
        );
    }

    @POST
    @Path("/{moduleName}/test")
    @Operation(
        summary = "Test a module",
        description = "Runs health and integration tests for a deployed module"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Test completed",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ModuleOperationResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Module not found"
        )
    })
    public Uni<Response> testModule(
            @Parameter(description = "Module name", required = true, example = "echo")
            @PathParam("moduleName") String moduleName) {
        
        LOG.infof("Request to test module: %s", moduleName);
        
        // TODO: Implement when service is ready
        return Uni.createFrom().item(
            Response.ok(new ModuleOperationResponse(true, 
                "Module test completed", 
                Map.of("module", moduleName, "status", "passed", "tests", 3)))
                .build()
        );
    }

    @GET
    @Path("/{moduleName}/logs")
    @Operation(
        summary = "Get module logs",
        description = "Retrieves recent logs from a deployed module (dev mode only)"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Logs retrieved successfully",
            content = @Content(
                mediaType = MediaType.TEXT_PLAIN
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Module not found"
        ),
        @APIResponse(
            responseCode = "503",
            description = "Service not available (not in dev mode)"
        )
    })
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<Response> getModuleLogs(
            @Parameter(description = "Module name", required = true, example = "echo")
            @PathParam("moduleName") String moduleName,
            
            @Parameter(description = "Number of lines to retrieve", example = "100")
            @QueryParam("lines") @DefaultValue("100") int lines) {
        
        LOG.infof("Request to get logs for module: %s (last %d lines)", moduleName, lines);
        
        // Check if we're in dev mode
        String profile = System.getProperty("quarkus.profile", "prod");
        if (!"dev".equals(profile)) {
            return Uni.createFrom().item(
                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("Log retrieval is only available in dev mode")
                    .build()
            );
        }
        
        // TODO: Implement when service is ready
        return Uni.createFrom().item(
            Response.ok("Sample logs for module: " + moduleName + "\n" +
                      "2024-01-01 10:00:00 INFO Module started\n" +
                      "2024-01-01 10:00:01 INFO Connected to engine\n" +
                      "2024-01-01 10:00:02 INFO Ready to process requests\n")
                .build()
        );
    }
}