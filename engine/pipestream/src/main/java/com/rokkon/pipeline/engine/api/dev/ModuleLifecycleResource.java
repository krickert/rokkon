package com.rokkon.pipeline.engine.api.dev;

import com.rokkon.pipeline.engine.dev.SimpleModuleLifecycleService;
import io.quarkus.runtime.LaunchMode;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

/**
 * REST API for module lifecycle management in dev mode.
 * This provides a simpler interface compared to the old deployment endpoints.
 */
@Path("/api/v1/dev/lifecycle")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Dev Mode Module Lifecycle", description = "Manage module lifecycle in development mode")
public class ModuleLifecycleResource {
    
    @Inject
    SimpleModuleLifecycleService lifecycleService;
    
    @Inject
    LaunchMode launchMode;
    
    /**
     * Get status of all modules
     */
    @GET
    @Path("/status")
    @Operation(summary = "Get module lifecycle status", 
               description = "Returns the current status of all configured modules")
    public Uni<Map<String, String>> getModuleStatuses() {
        if (!launchMode.isDevOrTest()) {
            throw new WebApplicationException("Only available in dev mode", 
                Response.Status.FORBIDDEN);
        }
        
        return Uni.createFrom().item(lifecycleService.getModuleStatuses());
    }
    
}