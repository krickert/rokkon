package com.rokkon.pipeline.engine.api;

import com.rokkon.pipeline.engine.service.ConsulHealthService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/api/consul")
public class ConsulStatusResource {
    
    @Inject
    ConsulHealthService consulHealthService;
    
    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConsulStatus() {
        ConsulHealthService.ConsulHealthStatus status = consulHealthService.getStatus();
        
        Map<String, Object> response = Map.of(
            "status", status.getStatus().toString(),
            "lastChecked", status.getLastChecked().toString(),
            "error", status.getError() != null ? status.getError() : ""
        );
        
        return Response.ok(response).build();
    }
}