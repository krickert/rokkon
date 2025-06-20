package com.rokkon.pipeline.consul.api;

import com.rokkon.pipeline.consul.service.GlobalModuleRegistryService;
import com.rokkon.pipeline.consul.service.GlobalModuleRegistryService.ModuleRegistration;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Path("/api/v1/global-modules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Global Module Registry", description = "Global module registration and management")
public class GlobalModuleResource {
    
    private static final Logger LOG = Logger.getLogger(GlobalModuleResource.class);
    
    @Inject
    GlobalModuleRegistryService moduleRegistry;
    
    public record RegisterModuleRequest(
        @NotBlank String moduleName,
        @NotBlank String implementationId,
        @NotBlank String host,
        int port,
        @NotBlank String serviceType,
        String version,
        Map<String, String> metadata,
        EngineConnectionConfig engineConnection
    ) {}
    
    public record EngineConnectionConfig(
        String host,
        int port
    ) {
        // If not provided, defaults to the main host/port
        public static EngineConnectionConfig defaultFrom(String host, int port) {
            return new EngineConnectionConfig(host, port);
        }
    }
    
    public record ModuleResponse(
        boolean success,
        String message,
        ModuleRegistration module
    ) {
        public static ModuleResponse success(ModuleRegistration module) {
            return new ModuleResponse(true, "Module registered successfully", module);
        }
        
        public static ModuleResponse failure(String message) {
            return new ModuleResponse(false, message, null);
        }
    }
    
    @POST
    @Path("/register")
    @Operation(summary = "Register a module globally", 
               description = "Registers a module in the global registry. Modules can then be enabled for specific clusters.")
    public Uni<Response> registerModule(@Valid RegisterModuleRequest request) {
        LOG.infof("Registering module globally: %s at %s:%d", 
                  request.moduleName(), request.host(), request.port());
        
        // Use engine connection config if provided, otherwise default to main host/port
        EngineConnectionConfig engineConn = request.engineConnection() != null ? 
            request.engineConnection() : 
            EngineConnectionConfig.defaultFrom(request.host(), request.port());
        
        return moduleRegistry.registerModule(
            request.moduleName(),
            request.implementationId(),
            request.host(),
            request.port(),
            request.serviceType(),
            request.version() != null ? request.version() : "1.0.0",
            request.metadata(),
            engineConn.host(),
            engineConn.port()
        )
        .onItem().transform(registration -> 
            Response.ok(ModuleResponse.success(registration)).build()
        )
        .onFailure().recoverWithItem(t -> {
            LOG.errorf(t, "Failed to register module %s", request.moduleName());
            
            // Check if it's a conflict (duplicate registration)
            if (t instanceof WebApplicationException wae) {
                int status = wae.getResponse().getStatus();
                if (status == Response.Status.CONFLICT.getStatusCode()) {
                    return Response.status(Response.Status.CONFLICT)
                        .entity(ModuleResponse.failure(wae.getMessage()))
                        .build();
                } else if (status == Response.Status.BAD_REQUEST.getStatusCode()) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ModuleResponse.failure(t.getMessage()))
                        .build();
                }
            }
            
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ModuleResponse.failure(t.getMessage()))
                .build();
        });
    }
    
    @GET
    @Operation(summary = "List all registered modules", 
               description = "Returns all globally registered modules as an ordered set (no duplicates)")
    public Uni<Set<ModuleRegistration>> listModules() {
        LOG.info("Listing all registered modules");
        return moduleRegistry.listRegisteredModules();
    }
    
    @GET
    @Path("/{moduleId}")
    @Operation(summary = "Get a specific module", 
               description = "Returns details of a specific module by ID")
    public Uni<Response> getModule(@PathParam("moduleId") String moduleId) {
        LOG.infof("Getting module: %s", moduleId);
        
        return moduleRegistry.getModule(moduleId)
            .onItem().transform(module -> {
                if (module != null) {
                    return Response.ok(module).build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Module not found"))
                        .build();
                }
            });
    }
    
    @DELETE
    @Path("/{moduleId}")
    @Operation(summary = "Deregister a module", 
               description = "Removes a module from the global registry")
    public Uni<Response> deregisterModule(@PathParam("moduleId") String moduleId) {
        LOG.infof("Deregistering module: %s", moduleId);
        
        return moduleRegistry.deregisterModule(moduleId)
            .onItem().transform(success -> {
                if (success) {
                    return Response.ok(Map.of("success", true, "message", "Module deregistered"))
                        .build();
                } else {
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(Map.of("success", false, "message", "Failed to deregister module"))
                        .build();
                }
            });
    }
    
    @POST
    @Path("/{moduleId}/clusters/{clusterName}/enable")
    @Operation(summary = "Enable module for a cluster", 
               description = "Enables a globally registered module for use by a specific cluster")
    public Uni<Response> enableForCluster(
            @PathParam("moduleId") String moduleId,
            @PathParam("clusterName") String clusterName) {
        
        LOG.infof("Enabling module %s for cluster %s", moduleId, clusterName);
        
        return moduleRegistry.enableModuleForCluster(moduleId, clusterName)
            .onItem().transform(v -> 
                Response.ok(Map.of("success", true, 
                                  "message", "Module enabled for cluster"))
                    .build()
            )
            .onFailure().recoverWithItem(t -> 
                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("success", false, "message", t.getMessage()))
                    .build()
            );
    }
    
    @DELETE
    @Path("/{moduleId}/clusters/{clusterName}/enable")
    @Operation(summary = "Disable module for a cluster", 
               description = "Disables a module for a specific cluster")
    public Uni<Response> disableForCluster(
            @PathParam("moduleId") String moduleId,
            @PathParam("clusterName") String clusterName) {
        
        LOG.infof("Disabling module %s for cluster %s", moduleId, clusterName);
        
        return moduleRegistry.disableModuleForCluster(moduleId, clusterName)
            .onItem().transform(v -> 
                Response.ok(Map.of("success", true, 
                                  "message", "Module disabled for cluster"))
                    .build()
            );
    }
    
    @GET
    @Path("/clusters/{clusterName}/enabled")
    @Operation(summary = "List enabled modules for a cluster", 
               description = "Returns all modules enabled for a specific cluster as an ordered set")
    public Uni<Set<String>> listEnabledModules(@PathParam("clusterName") String clusterName) {
        LOG.infof("Listing enabled modules for cluster: %s", clusterName);
        return moduleRegistry.listEnabledModulesForCluster(clusterName);
    }
}