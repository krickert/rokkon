package com.rokkon.search.config.pipeline.api;

import com.rokkon.search.config.pipeline.model.PipelineConfig;
import com.rokkon.search.config.pipeline.service.PipelineConfigService;
import com.rokkon.search.config.pipeline.service.validation.ValidationResult;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;

/**
 * REST API for pipeline configuration management.
 * Provides CRUD operations with comprehensive validation and Swagger documentation.
 */
@Path("/api/v1/clusters/{clusterName}/pipelines")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Pipeline Configuration", description = "CRUD operations for pipeline configurations")
public class PipelineConfigResource {
    
    private static final Logger LOG = LoggerFactory.getLogger(PipelineConfigResource.class);
    
    @Inject
    PipelineConfigService pipelineConfigService;
    
    @POST
    @Path("/{pipelineId}")
    @Operation(
        summary = "Create a new pipeline",
        description = "Creates a new pipeline configuration with comprehensive validation. " +
                     "The pipeline will be validated for structural integrity, naming conventions, " +
                     "and JSON schema compliance before being stored."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "201",
            description = "Pipeline created successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Validation failed or invalid pipeline configuration",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "409",
            description = "Pipeline already exists",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        )
    })
    public Uni<Response> createPipeline(
            @Parameter(description = "Cluster name", required = true, example = "production-cluster")
            @PathParam("clusterName") String clusterName,
            
            @Parameter(description = "Pipeline ID", required = true, example = "document-processing")
            @PathParam("pipelineId") String pipelineId,
            
            @Parameter(description = "Pipeline configuration", required = true)
            PipelineConfig config,
            
            @Parameter(description = "User or system that initiated this operation", example = "admin@company.com")
            @HeaderParam("X-Initiated-By") @DefaultValue("api-user") String initiatedBy) {
        
        LOG.info("Creating pipeline '{}' in cluster '{}' initiated by '{}'", pipelineId, clusterName, initiatedBy);
        
        return Uni.createFrom().completionStage(
            pipelineConfigService.createPipeline(clusterName, pipelineId, config, initiatedBy)
        ).map(validationResult -> {
            if (validationResult.valid()) {
                return Response.status(Response.Status.CREATED)
                        .entity(new ApiResponse("Pipeline created successfully", Map.of(
                                "clusterName", clusterName,
                                "pipelineId", pipelineId,
                                "warnings", validationResult.warnings()
                        )))
                        .build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiErrorResponse(
                                "Validation failed",
                                validationResult.errors(),
                                "VALIDATION_ERROR"
                        ))
                        .build();
            }
        }).onFailure().recoverWithItem(throwable -> {
            LOG.error("Error creating pipeline '{}' in cluster '{}': {}", pipelineId, clusterName, throwable.getMessage(), throwable);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse(
                            "Internal server error",
                            java.util.List.of(throwable.getMessage()),
                            "INTERNAL_ERROR"
                    ))
                    .build();
        });
    }
    
    @PUT
    @Path("/{pipelineId}")
    @Operation(
        summary = "Update an existing pipeline",
        description = "Updates an existing pipeline configuration. The new configuration " +
                     "will be validated and impact analysis will be performed before applying changes."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Pipeline updated successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Validation failed or invalid pipeline configuration",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Pipeline not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        )
    })
    public Uni<Response> updatePipeline(
            @PathParam("clusterName") String clusterName,
            @PathParam("pipelineId") String pipelineId,
            PipelineConfig config,
            @HeaderParam("X-Initiated-By") @DefaultValue("api-user") String initiatedBy) {
        
        LOG.info("Updating pipeline '{}' in cluster '{}' initiated by '{}'", pipelineId, clusterName, initiatedBy);
        
        return Uni.createFrom().completionStage(
            pipelineConfigService.updatePipeline(clusterName, pipelineId, config, initiatedBy)
        ).map(validationResult -> {
            if (validationResult.valid()) {
                return Response.ok()
                        .entity(new ApiResponse("Pipeline updated successfully", Map.of(
                                "clusterName", clusterName,
                                "pipelineId", pipelineId,
                                "warnings", validationResult.warnings()
                        )))
                        .build();
            } else {
                // Check if it's a not found vs validation error
                boolean notFound = validationResult.errors().stream()
                        .anyMatch(error -> error.contains("not found"));
                
                Response.Status status = notFound ? Response.Status.NOT_FOUND : Response.Status.BAD_REQUEST;
                return Response.status(status)
                        .entity(new ApiErrorResponse(
                                notFound ? "Pipeline not found" : "Validation failed",
                                validationResult.errors(),
                                notFound ? "NOT_FOUND" : "VALIDATION_ERROR"
                        ))
                        .build();
            }
        });
    }
    
    @GET
    @Path("/{pipelineId}")
    @Operation(
        summary = "Get a pipeline configuration",
        description = "Retrieves the current configuration for a specific pipeline."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Pipeline configuration retrieved successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = PipelineConfig.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Pipeline not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        )
    })
    public Uni<Response> getPipeline(
            @PathParam("clusterName") String clusterName,
            @PathParam("pipelineId") String pipelineId) {
        
        return Uni.createFrom().completionStage(
            pipelineConfigService.getPipeline(clusterName, pipelineId)
        ).map(optionalConfig -> {
            if (optionalConfig.isPresent()) {
                return Response.ok(optionalConfig.get()).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiErrorResponse(
                                "Pipeline not found",
                                java.util.List.of("Pipeline '" + pipelineId + "' not found in cluster '" + clusterName + "'"),
                                "NOT_FOUND"
                        ))
                        .build();
            }
        });
    }
    
    @GET
    @Operation(
        summary = "List all pipelines in a cluster",
        description = "Retrieves all pipeline configurations for the specified cluster."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Pipeline list retrieved successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(
                    type = SchemaType.OBJECT,
                    implementation = Map.class,
                    description = "Map of pipeline ID to pipeline configuration"
                )
            )
        )
    })
    public Uni<Response> listPipelines(@PathParam("clusterName") String clusterName) {
        return Uni.createFrom().completionStage(
            pipelineConfigService.listPipelines(clusterName)
        ).map(pipelines -> Response.ok(pipelines).build());
    }
    
    @DELETE
    @Path("/{pipelineId}")
    @Operation(
        summary = "Delete a pipeline",
        description = "Deletes a pipeline configuration. This will trigger events for " +
                     "cascading cleanup of dependent resources like Kafka topics."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Pipeline deleted successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Pipeline not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        )
    })
    public Uni<Response> deletePipeline(
            @PathParam("clusterName") String clusterName,
            @PathParam("pipelineId") String pipelineId,
            @HeaderParam("X-Initiated-By") @DefaultValue("api-user") String initiatedBy) {
        
        LOG.info("Deleting pipeline '{}' from cluster '{}' initiated by '{}'", pipelineId, clusterName, initiatedBy);
        
        return Uni.createFrom().completionStage(
            pipelineConfigService.deletePipeline(clusterName, pipelineId, initiatedBy)
        ).map(validationResult -> {
            if (validationResult.valid()) {
                return Response.ok()
                        .entity(new ApiResponse("Pipeline deleted successfully", Map.of(
                                "clusterName", clusterName,
                                "pipelineId", pipelineId
                        )))
                        .build();
            } else {
                boolean notFound = validationResult.errors().stream()
                        .anyMatch(error -> error.contains("not found"));
                
                Response.Status status = notFound ? Response.Status.NOT_FOUND : Response.Status.BAD_REQUEST;
                return Response.status(status)
                        .entity(new ApiErrorResponse(
                                notFound ? "Pipeline not found" : "Deletion failed",
                                validationResult.errors(),
                                notFound ? "NOT_FOUND" : "DELETION_ERROR"
                        ))
                        .build();
            }
        });
    }
    
    @GET
    @Path("/{pipelineId}/impact-analysis")
    @Operation(
        summary = "Analyze deletion impact",
        description = "Analyzes what would be affected if this pipeline is deleted. " +
                     "Shows dependent pipelines, orphaned topics, and services."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Impact analysis completed",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = PipelineConfigService.DependencyImpactAnalysis.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Pipeline not found"
        )
    })
    public Uni<Response> analyzeDeletionImpact(
            @PathParam("clusterName") String clusterName,
            @PathParam("pipelineId") String pipelineId) {
        
        return Uni.createFrom().completionStage(
            pipelineConfigService.analyzeDeletionImpact(clusterName, pipelineId, PipelineConfigService.DependencyType.PIPELINE)
        ).map(analysis -> Response.ok(analysis).build());
    }
    
    // Response DTOs for API documentation
    
    @Schema(description = "Successful API response")
    public record ApiResponse(
            @Schema(description = "Success message") String message,
            @Schema(description = "Additional response data") Map<String, Object> data
    ) {}
    
    @Schema(description = "API error response")
    public record ApiErrorResponse(
            @Schema(description = "Error message") String message,
            @Schema(description = "List of specific errors") java.util.List<String> errors,
            @Schema(description = "Error code for programmatic handling") String errorCode
    ) {}
}