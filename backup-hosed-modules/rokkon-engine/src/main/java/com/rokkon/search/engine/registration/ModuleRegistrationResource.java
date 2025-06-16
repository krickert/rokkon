package com.rokkon.search.engine.registration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API for module registration.
 * 
 * This is the endpoint that Docker CLI tools call to register
 * new modules with the engine after health checks pass.
 */
@Path("/api/modules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Module Registration", description = "API for registering processing modules with the engine")
public class ModuleRegistrationResource {
    
    private static final Logger LOG = LoggerFactory.getLogger(ModuleRegistrationResource.class);
    
    @Inject
    ModuleRegistrationService registrationService;
    
    @Inject
    DynamicGrpcClientManager clientManager;
    
    /**
     * Register a new module with the engine
     */
    @POST
    @Path("/register")
    @Operation(
        summary = "Register a new processing module",
        description = "Validates and registers a new PipeStepProcessor module with the engine. " +
                     "Performs health checks, validates gRPC interface, and registers with Consul."
    )
    @ApiResponse(
        responseCode = "200", 
        description = "Module successfully registered",
        content = @Content(schema = @Schema(implementation = ModuleRegistrationResponse.class))
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid registration request or validation failed"
    )
    @ApiResponse(
        responseCode = "500", 
        description = "Internal server error during registration"
    )
    public Response registerModule(
            @Schema(description = "Module registration details")
            ModuleRegistrationRequest request) {
        
        LOG.info("📥 Module registration request received: {} at {}:{}", 
                request.moduleName(), request.host(), request.port());
        
        try {
            ModuleRegistrationResponse response = registrationService.registerModule(request);
            
            if (response.success()) {
                LOG.info("✅ Module registration successful: {}", request.moduleName());
                return Response.ok(response).build();
            } else {
                LOG.warn("❌ Module registration failed: {} - {}", request.moduleName(), response.message());
                return Response.status(Response.Status.BAD_REQUEST)
                              .entity(response)
                              .build();
            }
            
        } catch (Exception e) {
            LOG.error("💥 Unexpected error during module registration: {}", request.moduleName(), e);
            
            ModuleRegistrationResponse errorResponse = ModuleRegistrationResponse.failure(
                "Internal server error during registration",
                java.util.List.of(e.getMessage())
            );
            
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                          .entity(errorResponse)
                          .build();
        }
    }
    
    /**
     * Get information about all registered modules
     */
    @GET
    @Path("/registered")
    @Operation(
        summary = "List all registered modules",
        description = "Returns information about all currently registered modules"
    )
    public Response getRegisteredModules() {
        LOG.debug("📋 Listing registered modules");
        
        var moduleIds = clientManager.getRegisteredModuleIds();
        var result = java.util.Map.of(
            "count", clientManager.getRegisteredModuleCount(),
            "moduleIds", moduleIds
        );
        
        return Response.ok(result).build();
    }
    
    /**
     * Test connectivity to a registered module
     */
    @POST
    @Path("/test/{moduleId}")
    @Operation(
        summary = "Test module connectivity", 
        description = "Tests gRPC connectivity to a registered module"
    )
    public Response testModuleConnectivity(@PathParam("moduleId") String moduleId) {
        LOG.debug("🔍 Testing connectivity to module: {}", moduleId);
        
        if (!clientManager.hasClient(moduleId)) {
            return Response.status(Response.Status.NOT_FOUND)
                          .entity(java.util.Map.of("error", "Module not found: " + moduleId))
                          .build();
        }
        
        boolean connected = clientManager.testModuleConnectivity(moduleId);
        
        var result = java.util.Map.of(
            "moduleId", moduleId,
            "connected", connected,
            "timestamp", java.time.Instant.now()
        );
        
        return Response.ok(result).build();
    }
    
    /**
     * Deregister a module (for testing/cleanup)
     */
    @DELETE
    @Path("/deregister/{moduleId}")
    @Operation(
        summary = "Deregister a module",
        description = "Removes a module registration and cleans up resources"
    )
    public Response deregisterModule(@PathParam("moduleId") String moduleId) {
        LOG.info("🗑️ Deregistering module: {}", moduleId);
        
        if (!clientManager.hasClient(moduleId)) {
            return Response.status(Response.Status.NOT_FOUND)
                          .entity(java.util.Map.of("error", "Module not found: " + moduleId))
                          .build();
        }
        
        try {
            clientManager.removeClient(moduleId);
            
            var result = java.util.Map.of(
                "success", true,
                "message", "Module deregistered successfully",
                "moduleId", moduleId
            );
            
            return Response.ok(result).build();
            
        } catch (Exception e) {
            LOG.error("Error deregistering module: {}", moduleId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                          .entity(java.util.Map.of("error", "Failed to deregister module: " + e.getMessage()))
                          .build();
        }
    }
}