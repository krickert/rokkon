package com.rokkon.pipeline.consul.api;

import com.rokkon.pipeline.config.model.PipelineModuleConfiguration;
import com.rokkon.pipeline.consul.model.ModuleWhitelistRequest;
import com.rokkon.pipeline.consul.model.ModuleWhitelistResponse;
import com.rokkon.pipeline.consul.service.ModuleWhitelistService;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * REST API for managing module whitelisting in clusters.
 */
@Path("/api/v1/clusters/{clusterName}/modules")
@Tag(name = "Module Whitelist", description = "Manage module whitelisting for clusters")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ModuleWhitelistResource {
    
    @Inject
    ModuleWhitelistService whitelistService;
    
    @POST
    @Path("/whitelist")
    @Operation(summary = "Add module to whitelist", 
               description = "Adds a module to the cluster's whitelist after verifying it exists in Consul")
    @APIResponse(responseCode = "200", description = "Module whitelisted successfully",
                 content = @Content(schema = @Schema(implementation = ModuleWhitelistResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request or module not found",
                 content = @Content(schema = @Schema(implementation = ModuleWhitelistResponse.class)))
    public Uni<Response> whitelistModule(
            @PathParam("clusterName") @NotBlank String clusterName,
            @Valid ModuleWhitelistRequest request) {
        
        return whitelistService.whitelistModule(clusterName, request)
            .map(response -> {
                if (response.success()) {
                    return Response.ok(response).build();
                } else {
                    return Response.status(Response.Status.BAD_REQUEST)
                                 .entity(response)
                                 .build();
                }
            });
    }
    
    @DELETE
    @Path("/whitelist/{grpcServiceName}")
    @Operation(summary = "Remove module from whitelist", 
               description = "Removes a module from the cluster's whitelist if not in use")
    @APIResponse(responseCode = "200", description = "Module removed from whitelist",
                 content = @Content(schema = @Schema(implementation = ModuleWhitelistResponse.class)))
    @APIResponse(responseCode = "400", description = "Module in use or other error",
                 content = @Content(schema = @Schema(implementation = ModuleWhitelistResponse.class)))
    public Uni<Response> removeFromWhitelist(
            @PathParam("clusterName") @NotBlank String clusterName,
            @PathParam("grpcServiceName") @NotBlank String grpcServiceName) {
        
        return whitelistService.removeModuleFromWhitelist(clusterName, grpcServiceName)
            .map(response -> {
                if (response.success()) {
                    return Response.ok(response).build();
                } else {
                    return Response.status(Response.Status.BAD_REQUEST)
                                 .entity(response)
                                 .build();
                }
            });
    }
    
    @GET
    @Path("/whitelist")
    @Operation(summary = "List whitelisted modules", 
               description = "Returns all modules whitelisted for the cluster")
    @APIResponse(responseCode = "200", description = "List of whitelisted modules",
                 content = @Content(schema = @Schema(implementation = PipelineModuleConfiguration.class)))
    public Uni<List<PipelineModuleConfiguration>> listWhitelistedModules(
            @PathParam("clusterName") @NotBlank String clusterName) {
        
        return whitelistService.listWhitelistedModules(clusterName);
    }
}