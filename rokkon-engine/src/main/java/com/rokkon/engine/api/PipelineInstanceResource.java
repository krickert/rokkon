package com.rokkon.engine.api;

import com.rokkon.pipeline.config.model.PipelineInstance;
import com.rokkon.pipeline.consul.service.PipelineInstanceService;
import com.rokkon.pipeline.consul.model.CreateInstanceRequest;
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
 * REST API for pipeline instance management in clusters.
 * Pipeline instances are deployments of pipeline definitions in specific clusters.
 */
@Path("/api/v1/clusters/{clusterName}/pipeline-instances")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Pipeline Instances", description = "Pipeline instance lifecycle management")
public class PipelineInstanceResource {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineInstanceResource.class);

    @Inject
    PipelineInstanceService pipelineInstanceService;

    @GET
    @Operation(
        summary = "List pipeline instances in a cluster",
        description = "Returns all pipeline instances deployed in the specified cluster"
    )
    @APIResponse(
        responseCode = "200",
        description = "List of pipeline instances",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                type = SchemaType.ARRAY,
                implementation = PipelineInstance.class
            )
        )
    )
    public Uni<List<PipelineInstance>> listInstances(
            @Parameter(description = "Cluster name", required = true)
            @PathParam("clusterName") String clusterName) {

        LOG.debug("Listing pipeline instances in cluster '{}'", clusterName);
        return pipelineInstanceService.listInstances(clusterName);
    }

    @GET
    @Path("/{instanceId}")
    @Operation(
        summary = "Get a pipeline instance",
        description = "Returns details of a specific pipeline instance"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Pipeline instance found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = PipelineInstance.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Pipeline instance not found"
        )
    })
    public Uni<Response> getInstance(
            @Parameter(description = "Cluster name", required = true)
            @PathParam("clusterName") String clusterName,

            @Parameter(description = "Instance ID", required = true)
            @PathParam("instanceId") String instanceId) {

        LOG.debug("Getting pipeline instance '{}' in cluster '{}'", instanceId, clusterName);

        return pipelineInstanceService.getInstance(clusterName, instanceId)
            .map(instance -> {
                if (instance != null) {
                    return Response.ok(instance).build();
                } else {
                    return Response.status(Status.NOT_FOUND)
                        .entity(Map.of("error", "Pipeline instance not found"))
                        .build();
                }
            });
    }

    @POST
    @Operation(
        summary = "Create a pipeline instance",
        description = "Creates a new pipeline instance from a definition"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "201",
            description = "Pipeline instance created successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = CreateInstanceResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Validation failed"
        ),
        @APIResponse(
            responseCode = "404",
            description = "Pipeline definition not found"
        ),
        @APIResponse(
            responseCode = "409",
            description = "Instance ID already exists"
        )
    })
    public Uni<Response> createInstance(
            @Parameter(description = "Cluster name", required = true)
            @PathParam("clusterName") String clusterName,

            @Parameter(description = "Create instance request", required = true)
            CreateInstanceRequest request) {

        LOG.info("Creating pipeline instance '{}' in cluster '{}' from definition '{}'", 
            request.instanceId(), clusterName, request.pipelineDefinitionId());

        return pipelineInstanceService.createInstance(clusterName, request)
            .map(result -> {
                if (result.valid()) {
                    return Response.status(Status.CREATED)
                        .entity(new CreateInstanceResponse(
                            true, 
                            "Pipeline instance created successfully",
                            request.instanceId(),
                            null,
                            null
                        ))
                        .build();
                } else {
                    Status status = Status.BAD_REQUEST;
                    if (result.errors().stream().anyMatch(e -> e.contains("not found"))) {
                        status = Status.NOT_FOUND;
                    } else if (result.errors().stream().anyMatch(e -> e.contains("already exists"))) {
                        status = Status.CONFLICT;
                    }

                    return Response.status(status)
                        .entity(new CreateInstanceResponse(
                            false,
                            "Failed to create instance",
                            null,
                            result.errors(),
                            result.warnings()
                        ))
                        .build();
                }
            });
    }

    @PUT
    @Path("/{instanceId}")
    @Operation(
        summary = "Update a pipeline instance",
        description = "Updates an existing pipeline instance configuration"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Pipeline instance updated successfully"
        ),
        @APIResponse(
            responseCode = "400",
            description = "Validation failed"
        ),
        @APIResponse(
            responseCode = "404",
            description = "Pipeline instance not found"
        )
    })
    public Uni<Response> updateInstance(
            @PathParam("clusterName") String clusterName,
            @PathParam("instanceId") String instanceId,
            PipelineInstance instance) {

        LOG.info("Updating pipeline instance '{}' in cluster '{}'", instanceId, clusterName);

        return pipelineInstanceService.updateInstance(clusterName, instanceId, instance)
            .map(result -> {
                if (result.valid()) {
                    return Response.ok(Map.of(
                        "success", true,
                        "message", "Pipeline instance updated successfully"
                    )).build();
                } else {
                    Status status = result.errors().stream()
                        .anyMatch(e -> e.contains("not found")) 
                        ? Status.NOT_FOUND : Status.BAD_REQUEST;

                    return Response.status(status)
                        .entity(Map.of(
                            "success", false,
                            "message", "Update failed",
                            "errors", result.errors(),
                            "warnings", result.warnings()
                        ))
                        .build();
                }
            });
    }

    @DELETE
    @Path("/{instanceId}")
    @Operation(
        summary = "Delete a pipeline instance",
        description = "Deletes a pipeline instance. The instance must be stopped first."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "204",
            description = "Pipeline instance deleted successfully"
        ),
        @APIResponse(
            responseCode = "404",
            description = "Pipeline instance not found"
        ),
        @APIResponse(
            responseCode = "409",
            description = "Pipeline instance is still running"
        )
    })
    public Uni<Response> deleteInstance(
            @PathParam("clusterName") String clusterName,
            @PathParam("instanceId") String instanceId) {

        LOG.info("Deleting pipeline instance '{}' from cluster '{}'", instanceId, clusterName);

        return pipelineInstanceService.deleteInstance(clusterName, instanceId)
            .map(result -> {
                if (result.valid()) {
                    return Response.noContent().build();
                } else {
                    if (result.errors().stream().anyMatch(e -> e.contains("not found"))) {
                        return Response.status(Status.NOT_FOUND)
                                .entity(Map.of("errors", List.of("Pipeline instance not found")))
                                .build();
                    } else if (result.errors().stream().anyMatch(e -> e.contains("running"))) {
                        return Response.status(Status.CONFLICT)
                                .entity(Map.of("errors", List.of("Cannot delete running instance")))
                                .build();
                    } else {
                        return Response.status(Status.BAD_REQUEST)
                                .entity(Map.of("errors", result.errors()))
                                .build();
                    }
                }
            });
    }

    @POST
    @Path("/{instanceId}/start")
    @Operation(
        summary = "Start a pipeline instance",
        description = "Starts a stopped pipeline instance"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Pipeline instance started successfully"
        ),
        @APIResponse(
            responseCode = "404",
            description = "Pipeline instance not found"
        ),
        @APIResponse(
            responseCode = "409",
            description = "Pipeline instance is already running"
        )
    })
    public Uni<Response> startInstance(
            @PathParam("clusterName") String clusterName,
            @PathParam("instanceId") String instanceId) {

        LOG.info("Starting pipeline instance '{}' in cluster '{}'", instanceId, clusterName);

        return pipelineInstanceService.startInstance(clusterName, instanceId)
            .map(result -> {
                if (result.valid()) {
                    return Response.ok(Map.of(
                        "success", true,
                        "message", "Pipeline instance started successfully"
                    )).build();
                } else {
                    Status status = Status.BAD_REQUEST;
                    if (result.errors().stream().anyMatch(e -> e.contains("not found"))) {
                        status = Status.NOT_FOUND;
                    } else if (result.errors().stream().anyMatch(e -> e.contains("already running"))) {
                        status = Status.CONFLICT;
                    }

                    return Response.status(status)
                        .entity(Map.of(
                            "success", false,
                                "message", "Failed to start instance",
                                "errors", result.errors()
                        ))
                        .build();
                }
            });
    }

    @POST
    @Path("/{instanceId}/stop")
    @Operation(
        summary = "Stop a pipeline instance",
        description = "Stops a running pipeline instance"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Pipeline instance stopped successfully"
        ),
        @APIResponse(
            responseCode = "404",
            description = "Pipeline instance not found"
        ),
        @APIResponse(
            responseCode = "409",
            description = "Pipeline instance is not running"
        )
    })
    public Uni<Response> stopInstance(
            @PathParam("clusterName") String clusterName,
            @PathParam("instanceId") String instanceId) {

        LOG.info("Stopping pipeline instance '{}' in cluster '{}'", instanceId, clusterName);

        return pipelineInstanceService.stopInstance(clusterName, instanceId)
            .map(result -> {
                if (result.valid()) {
                    return Response.ok(Map.of(
                        "success", true,
                        "message", "Pipeline instance stopped successfully"
                    )).build();
                } else {
                    Status status = Status.BAD_REQUEST;
                    if (result.errors().stream().anyMatch(e -> e.contains("not found"))) {
                        status = Status.NOT_FOUND;
                    } else if (result.errors().stream().anyMatch(e -> e.contains("not running"))) {
                        status = Status.CONFLICT;
                    }

                    return Response.status(status)
                        .entity(Map.of(
                            "success", false,
                                "message", "Failed to start instance",
                                "errors", result.errors()
                        ))
                        .build();
                }
            });
    }


    /**
     * Response for instance creation
     */
    @Schema(description = "Create instance response")
    public record CreateInstanceResponse(
        @Schema(description = "Success indicator", examples = {"true"})
        boolean success,

        @Schema(description = "Response message", examples = {"Pipeline instance created successfully"})
        String message,

        @Schema(description = "Created instance ID", examples = {"prod-pipeline-1"})
        String instanceId,

        @Schema(description = "Error messages if any", examples = {"['Instance ID already exists']"})
        List<String> errors,

        @Schema(description = "Warning messages if any", examples = {"['Configuration may need tuning for production use']"})
        List<String> warnings
    ) {}
}