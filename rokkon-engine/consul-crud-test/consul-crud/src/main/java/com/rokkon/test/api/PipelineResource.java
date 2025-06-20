package com.rokkon.test.api;

import com.rokkon.test.model.PipelineConfig;
import com.rokkon.test.service.PipelineConfigService;
import com.rokkon.test.validation.ValidationResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * REST endpoint for pipeline CRUD operations
 */
@Path("/api/pipelines")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PipelineResource {
    
    @Inject
    PipelineConfigService pipelineService;
    
    @POST
    @Path("/{pipelineId}")
    public Response createPipeline(@PathParam("pipelineId") String pipelineId, PipelineConfig config) {
        ValidationResult result = pipelineService.createPipeline(pipelineId, config);
        if (result.isValid()) {
            return Response.ok().entity(new ApiResponse(true, "Pipeline created successfully")).build();
        } else {
            return Response.status(400).entity(new ApiResponse(false, result.getErrors())).build();
        }
    }
    
    @GET
    @Path("/{pipelineId}")
    public Response getPipeline(@PathParam("pipelineId") String pipelineId) {
        return pipelineService.getPipeline(pipelineId)
                .map(config -> Response.ok(config).build())
                .orElse(Response.status(404).entity(new ApiResponse(false, "Pipeline not found")).build());
    }
    
    @PUT
    @Path("/{pipelineId}")
    public Response updatePipeline(@PathParam("pipelineId") String pipelineId, PipelineConfig config) {
        ValidationResult result = pipelineService.updatePipeline(pipelineId, config);
        if (result.isValid()) {
            return Response.ok().entity(new ApiResponse(true, "Pipeline updated successfully")).build();
        } else {
            return Response.status(400).entity(new ApiResponse(false, result.getErrors())).build();
        }
    }
    
    @DELETE
    @Path("/{pipelineId}")
    public Response deletePipeline(@PathParam("pipelineId") String pipelineId) {
        ValidationResult result = pipelineService.deletePipeline(pipelineId);
        if (result.isValid()) {
            return Response.ok().entity(new ApiResponse(true, "Pipeline deleted successfully")).build();
        } else {
            return Response.status(400).entity(new ApiResponse(false, result.getErrors())).build();
        }
    }
    
    @GET
    public Response listPipelines() {
        List<String> pipelines = pipelineService.listPipelines();
        return Response.ok(pipelines).build();
    }
    
    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(new ApiResponse(true, "Service is healthy")).build();
    }
    
    public static class ApiResponse {
        public boolean success;
        public String message;
        public List<String> errors;
        
        public ApiResponse() {}
        
        public ApiResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public ApiResponse(boolean success, List<String> errors) {
            this.success = success;
            this.errors = errors;
        }
    }
}