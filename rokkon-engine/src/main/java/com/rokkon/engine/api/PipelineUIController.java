package com.rokkon.engine.api;

import com.rokkon.pipeline.consul.service.PipelineDefinitionService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UI-specific controller for pipeline operations.
 * Provides simplified endpoints optimized for frontend interaction.
 */
@Path("/api/v1/ui/pipelines")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class PipelineUIController {
    
    private static final Logger LOG = LoggerFactory.getLogger(PipelineUIController.class);
    
    @Inject
    PipelineDefinitionService pipelineDefinitionService;
    
    /**
     * Simple DTO for creating a pipeline from the UI
     */
    public static class CreatePipelineRequest {
        @NotBlank(message = "Pipeline ID is required")
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", 
                message = "Pipeline ID must be lowercase alphanumeric with hyphens")
        public String pipelineId;
        
        @NotBlank(message = "Pipeline name is required")
        public String pipelineName;
        
        public String pipelineDescription;
        
        // Getters and setters
        public String getPipelineId() { return pipelineId; }
        public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }
        
        public String getPipelineName() { return pipelineName; }
        public void setPipelineName(String pipelineName) { this.pipelineName = pipelineName; }
        
        public String getPipelineDescription() { return pipelineDescription; }
        public void setPipelineDescription(String pipelineDescription) { this.pipelineDescription = pipelineDescription; }
    }
    
    /**
     * Simple response for UI operations
     */
    public static class UIResponse {
        public final boolean success;
        public final String message;
        public final String pipelineId;
        
        public UIResponse(boolean success, String message, String pipelineId) {
            this.success = success;
            this.message = message;
            this.pipelineId = pipelineId;
        }
        
        public static UIResponse success(String message, String pipelineId) {
            return new UIResponse(true, message, pipelineId);
        }
        
        public static UIResponse error(String message) {
            return new UIResponse(false, message, null);
        }
    }
    
    @POST
    @Path("/create")
    public Uni<Response> createPipeline(@Valid CreatePipelineRequest request) {
        LOG.info("UI: Creating pipeline '{}' with name '{}'", request.pipelineId, request.pipelineName);
        
        // First check if pipeline already exists
        return pipelineDefinitionService.definitionExists(request.pipelineId)
            .onItem().transformToUni(exists -> {
                if (exists) {
                    LOG.warn("Pipeline '{}' already exists", request.pipelineId);
                    return Uni.createFrom().item(
                        Response.status(Response.Status.CONFLICT)
                            .entity(UIResponse.error("A pipeline with this ID already exists"))
                            .build()
                    );
                }
                
                // Create a minimal pipeline config (note: description is stored separately in metadata)
                var config = new com.rokkon.pipeline.config.model.PipelineConfig(
                    request.pipelineName,
                    new java.util.HashMap<>()  // Empty steps - can be added later
                );
                
                // Create the pipeline
                return pipelineDefinitionService.createDefinition(request.pipelineId, config)
                    .map(result -> {
                        if (result.valid()) {
                            LOG.info("Successfully created pipeline '{}'", request.pipelineId);
                            return Response.status(Response.Status.CREATED)
                                .entity(UIResponse.success(
                                    "Pipeline created successfully", 
                                    request.pipelineId
                                ))
                                .build();
                        } else {
                            LOG.warn("Validation failed for pipeline '{}': {}", 
                                request.pipelineId, result.errors());
                            return Response.status(Response.Status.BAD_REQUEST)
                                .entity(UIResponse.error(
                                    String.join(", ", result.errors())
                                ))
                                .build();
                        }
                    });
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.error("Failed to create pipeline", throwable);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(UIResponse.error("Internal error: " + throwable.getMessage()))
                    .build();
            });
    }
    
    @GET
    @Path("/check/{pipelineId}")
    public Uni<Response> checkPipelineExists(@PathParam("pipelineId") String pipelineId) {
        return pipelineDefinitionService.definitionExists(pipelineId)
            .map(exists -> {
                return Response.ok(new UIResponse(
                    !exists,  // success = true if pipeline doesn't exist
                    exists ? "Pipeline ID already in use" : "Pipeline ID is available",
                    pipelineId
                )).build();
            });
    }
}