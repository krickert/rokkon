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

import java.util.Map;
import java.util.Set;

@Path("/api/v1/modules")
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
        EngineConnectionConfig engineConnection,  // Keep for future extensibility (TLS, auth, etc.)
        String jsonSchema  // Optional JSON schema from module
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
    @Operation(summary = "Register a module", 
               description = "Registers a module in the registry. Once registered, it's available to all clusters.")
    public Uni<Response> registerModule(@Valid RegisterModuleRequest request) {
        LOG.infof("Registering module: %s at %s:%d", 
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
            engineConn.port(),
            request.jsonSchema()
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
               description = "Returns all registered modules")
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
               description = "Removes a module from the registry")
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
    @Path("/{serviceName}/archive")
    @Operation(summary = "Archive an unhealthy service", 
               description = "Moves an unhealthy service to archive section in Consul for audit purposes")
    public Uni<Response> archiveUnhealthyService(
            @PathParam("serviceName") String serviceName,
            @QueryParam("reason") @DefaultValue("Service unhealthy") String reason) {
        
        LOG.infof("Archiving unhealthy service: %s, reason: %s", serviceName, reason);
        
        return moduleRegistry.archiveService(serviceName, reason)
            .onItem().transform(success -> {
                if (success) {
                    return Response.ok(Map.of(
                        "success", true, 
                        "message", "Service archived successfully",
                        "serviceName", serviceName
                    )).build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of(
                            "success", false, 
                            "message", "Service not found or already archived"
                        ))
                        .build();
                }
            })
            .onFailure().recoverWithItem(t -> {
                LOG.errorf(t, "Failed to archive service %s", serviceName);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                        "success", false, 
                        "message", "Failed to archive service: " + t.getMessage()
                    ))
                    .build();
            });
    }
}