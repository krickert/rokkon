package com.rokkon.engine.api;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.consul.service.PipelineDefinitionService;
import com.rokkon.pipeline.consul.model.PipelineDefinitionSummary;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
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

import java.util.List;
import java.util.Map;

/**
 * REST API for global pipeline definition management.
 * Pipeline definitions are templates that can be instantiated in multiple clusters.
 */
@Path("/api/v1/pipelines/definitions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Pipeline Definitions", description = "Global pipeline definition management")
public class PipelineDefinitionResource {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineDefinitionResource.class);

    @Inject
    PipelineDefinitionService pipelineDefinitionService;

    @GET
    @Operation(
        summary = "List all pipeline definitions",
        description = "Returns all global pipeline definitions available for instantiation"
    )
    @APIResponse(
        responseCode = "200",
        description = "List of pipeline definitions",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                type = SchemaType.ARRAY,
                implementation = PipelineDefinitionSummary.class
            )
        )
    )
    public Uni<List<PipelineDefinitionSummary>> listDefinitions() {
        LOG.debug("Listing all pipeline definitions");
        return pipelineDefinitionService.listDefinitions();
    }

    @GET
    @Path("/{pipelineId}")
    @Operation(
        summary = "Get a pipeline definition",
        description = "Returns a specific pipeline definition by ID"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Pipeline definition found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = PipelineConfig.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Pipeline definition not found"
        )
    })
    public Uni<Response> getDefinition(
            @Parameter(description = "Pipeline definition ID", required = true)
            @PathParam("pipelineId") String pipelineId) {

        LOG.debug("Getting pipeline definition '{}'", pipelineId);

        return pipelineDefinitionService.getDefinition(pipelineId)
            .map(definition -> {
                if (definition != null) {
                    return Response.ok(definition).build();
                } else {
                    return Response.status(Status.NOT_FOUND)
                        .entity(Map.of("error", "Pipeline definition not found"))
                        .build();
                }
            });
    }

    @POST
    @Path("/{pipelineId}")
    @Operation(
        summary = "Create a pipeline definition",
        description = "Creates a new global pipeline definition"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "201",
            description = "Pipeline definition created successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Validation failed",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "409",
            description = "Pipeline definition already exists",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiResponse.class)
            )
        )
    })
    public Uni<Response> createDefinition(
            @Parameter(description = "Pipeline definition ID", required = true)
            @PathParam("pipelineId") String pipelineId,

            @Parameter(description = "Pipeline definition", required = true)
            PipelineConfig definition) {

        LOG.info("Creating pipeline definition '{}'", pipelineId);

        return pipelineDefinitionService.createDefinition(pipelineId, definition)
            .map(result -> {
                if (result.valid()) {
                    return Response.status(Status.CREATED)
                        .entity(new ApiResponse(true, "Pipeline definition created successfully", null, null))
                        .build();
                } else {
                    Status status = result.errors().stream()
                        .anyMatch(e -> e.contains("already exists")) 
                        ? Status.CONFLICT : Status.BAD_REQUEST;

                    return Response.status(status)
                        .entity(new ApiResponse(false, "Validation failed", result.errors(), result.warnings()))
                        .build();
                }
            });
    }

    @PUT
    @Path("/{pipelineId}")
    @Operation(
        summary = "Update a pipeline definition",
        description = "Updates an existing pipeline definition"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Pipeline definition updated successfully"
        ),
        @APIResponse(
            responseCode = "400",
            description = "Validation failed"
        ),
        @APIResponse(
            responseCode = "404",
            description = "Pipeline definition not found"
        )
    })
    public Uni<Response> updateDefinition(
            @PathParam("pipelineId") String pipelineId,
            PipelineConfig definition) {

        LOG.info("Updating pipeline definition '{}'", pipelineId);

        return pipelineDefinitionService.updateDefinition(pipelineId, definition)
            .map(result -> {
                if (result.valid()) {
                    return Response.ok(new ApiResponse(true, "Pipeline definition updated successfully", null, null))
                        .build();
                } else {
                    Status status = result.errors().stream()
                        .anyMatch(e -> e.contains("not found")) 
                        ? Status.NOT_FOUND : Status.BAD_REQUEST;

                    return Response.status(status)
                        .entity(new ApiResponse(false, "Update failed", result.errors(), result.warnings()))
                        .build();
                }
            });
    }

    @DELETE
    @Path("/{pipelineId}")
    @Operation(
        summary = "Delete a pipeline definition",
        description = "Deletes a pipeline definition. Will fail if instances exist."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "204",
            description = "Pipeline definition deleted successfully"
        ),
        @APIResponse(
            responseCode = "404",
            description = "Pipeline definition not found"
        ),
        @APIResponse(
            responseCode = "409",
            description = "Pipeline definition has active instances"
        )
    })
    public Uni<Response> deleteDefinition(
            @PathParam("pipelineId") String pipelineId) {

        LOG.info("Deleting pipeline definition '{}'", pipelineId);

        return pipelineDefinitionService.deleteDefinition(pipelineId)
            .map(result -> {
                if (result.valid()) {
                    return Response.noContent().build();
                } else {
                    if (result.errors().stream().anyMatch(e -> e.contains("not found"))) {
                        return Response.status(Status.NOT_FOUND)
                            .entity(new ApiResponse(false, "Pipeline definition not found", result.errors(), null))
                            .build();
                    } else if (result.errors().stream().anyMatch(e -> e.contains("instances"))) {
                        return Response.status(Status.CONFLICT)
                            .entity(new ApiResponse(false, "Cannot delete pipeline with active instances", result.errors(), null))
                            .build();
                    } else {
                        return Response.status(Status.BAD_REQUEST)
                            .entity(new ApiResponse(false, "Delete failed", result.errors(), null))
                            .build();
                    }
                }
            });
    }


    /**
     * API response wrapper
     */
    @Schema(description = "API response")
    public record ApiResponse(
        @Schema(description = "Success indicator", examples = {"true"})
        boolean success,

        @Schema(description = "Response message", examples = {"Pipeline definition created successfully"})
        String message,

        @Schema(description = "Error messages if any", examples = {"['Pipeline with ID document-processing already exists']"})
        List<String> errors,

        @Schema(description = "Warning messages if any", examples = {"[]"})
        List<String> warnings
    ) {}
}