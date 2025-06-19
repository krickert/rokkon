package com.rokkon.pipeline.engine.api;

import com.rokkon.pipeline.engine.model.ModuleRegistrationRequest;
import com.rokkon.pipeline.engine.model.ModuleRegistrationResponse;
import com.rokkon.pipeline.engine.service.ModuleRegistrationService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

@Path("/api/v1/modules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Module Registration", description = "Module registration operations")
public class ModuleRegistrationResource {
    
    private static final Logger LOG = Logger.getLogger(ModuleRegistrationResource.class);
    
    @Inject
    ModuleRegistrationService registrationService;
    
    @POST
    @Path("/register")
    @Operation(summary = "Register a new module", 
               description = "Registers a new module with validation, health check, and Consul registration")
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Module registered successfully",
            content = @Content(schema = @Schema(implementation = ModuleRegistrationResponse.class))
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid request or validation failed",
            content = @Content(schema = @Schema(implementation = ModuleRegistrationResponse.class))
        ),
        @APIResponse(
            responseCode = "500",
            description = "Internal server error during registration",
            content = @Content(schema = @Schema(implementation = ModuleRegistrationResponse.class))
        )
    })
    public Uni<Response> registerModule(@Valid ModuleRegistrationRequest request) {
        LOG.infof("Received module registration request for: %s at %s:%d", 
                  request.moduleName(), request.host(), request.port());
        
        return registrationService.registerModule(request)
            .map(response -> {
                if (response.success()) {
                    LOG.infof("Module '%s' registered successfully with ID: %s", 
                              request.moduleName(), response.moduleId());
                    return Response.ok(response).build();
                } else {
                    LOG.errorf("Module '%s' registration failed: %s", 
                               request.moduleName(), response.message());
                    return Response.status(Response.Status.BAD_REQUEST)
                                   .entity(response)
                                   .build();
                }
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.errorf(throwable, "Unexpected error during module registration for: %s", 
                           request.moduleName());
                ModuleRegistrationResponse errorResponse = ModuleRegistrationResponse.failure(
                    "Internal error during module registration: " + throwable.getMessage()
                );
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                               .entity(errorResponse)
                               .build();
            });
    }
}