package com.rokkon.engine.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

/**
 * Resource to provide engine information to the frontend.
 */
@Path("/api/v1/engine/info")
@Produces(MediaType.APPLICATION_JSON)
public class EngineInfoResource {
    
    @ConfigProperty(name = "quarkus.application.name", defaultValue = "Rokkon Engine")
    String applicationName;
    
    @ConfigProperty(name = "quarkus.application.version", defaultValue = "unknown")
    String applicationVersion;
    
    @ConfigProperty(name = "rokkon.cluster.name", defaultValue = "default")
    String clusterName;
    
    @ConfigProperty(name = "rokkon.engine.name", defaultValue = "engine-1")
    String engineName;
    
    @GET
    public Response getEngineInfo() {
        return Response.ok(Map.of(
            "applicationName", applicationName,
            "applicationVersion", applicationVersion,
            "clusterName", clusterName,
            "engineName", engineName,
            "consulConfigSpace", "config/" + applicationName.toLowerCase().replace(" ", "-")
        )).build();
    }
}