package com.rokkon.engine.api;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.model.PipelineDefinitionSummary;
import com.rokkon.pipeline.config.service.PipelineDefinitionService;
import io.quarkus.runtime.LaunchMode;
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
import java.util.HashMap;
import java.util.Collections;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService;
import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.config.model.StepType; // Assuming this is the correct StepType
import com.rokkon.pipeline.config.model.PipelineStepConfig.ProcessorInfo;
import com.rokkon.pipeline.config.model.PipelineStepConfig.JsonConfigOptions;


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

    @Inject
    GlobalModuleRegistryService moduleRegistryService;

    @Inject
    ObjectMapper objectMapper;

    @POST
    @Operation(summary = "Create a pipeline definition from DTO", description = "Creates a new global pipeline definition using a list-based step DTO.")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Pipeline definition created successfully"),
            @APIResponse(responseCode = "400", description = "Invalid request payload or validation failed"),
            @APIResponse(responseCode = "500", description = "Internal server error during transformation or module lookup")
    })
    public Uni<Response> createDefinitionFromDTO(PipelineDefinitionRequestDTO pipelineRequest) {
        if (pipelineRequest == null || pipelineRequest.name() == null || pipelineRequest.name().isBlank()) {
            return Uni.createFrom().item(Response.status(Status.BAD_REQUEST)
                    .entity(new ApiResponse(false, "Pipeline name is required in the request.", null, null))
                    .build());
        }
        LOG.info("Received request to create pipeline definition '{}' from DTO", pipelineRequest.name());

        // Transform DTO to PipelineConfig
        // This involves asynchronous calls to moduleRegistryService, so the whole transformation needs to be reactive.

        List<Uni<PipelineStepConfig>> stepConfigsUnis = pipelineRequest.steps().stream()
            .map(dtoStep -> moduleRegistryService.getModule(dtoStep.module())
                    .onItem().ifNull().failWith(() -> {
                        LOG.warn("Module '{}' not found in registry for step '{}'", dtoStep.module(), dtoStep.name());
                        return new WebApplicationException(
                            Response.status(Status.BAD_REQUEST)
                                .entity(new ApiResponse(false, "Module " + dtoStep.module() + " not found for step " + dtoStep.name(), null, null))
                                .type(MediaType.APPLICATION_JSON)
                                .build()
                        );
                    })
                    .onItem().transform(moduleRegistration -> {
                        StepType stepType = convertServiceTypeToStepType(moduleRegistration.serviceType(), dtoStep.module());

                        ProcessorInfo processorInfo = new ProcessorInfo(dtoStep.module(), null); // Assuming module ID is grpcServiceName

                        Map<String, String> configParams = new HashMap<>();
                        configParams.put("module_version", moduleRegistration.version() != null ? moduleRegistration.version() : "1.0.0");
                        // Potentially add other metadata from moduleRegistration.metadata() if needed

                        JsonConfigOptions customConfigOptions = new JsonConfigOptions(
                                objectMapper.valueToTree(dtoStep.config() != null ? dtoStep.config() : Collections.emptyMap()),
                                configParams
                        );

                        return new PipelineStepConfig(
                                dtoStep.name(),
                                stepType,
                                "Step " + dtoStep.name(), // Default description
                                null, // customConfigSchemaId
                                customConfigOptions,
                                Collections.emptyList(), // kafkaInputs
                                Collections.emptyMap(), // outputs
                                0, // maxRetries
                                1000L, // retryBackoffMs
                                30000L, // maxRetryBackoffMs
                                2.0, // retryBackoffMultiplier
                                null, // stepTimeoutMs
                                processorInfo
                        );
                    })
                    .onFailure().invoke(e -> LOG.error("Failed to process step {} (module {})", dtoStep.name(), dtoStep.module(), e))
            )
            .collect(Collectors.toList());

        // Check if we're in production mode
        boolean isProduction = LaunchMode.current() == LaunchMode.NORMAL;
        
        // Handle empty pipelines in non-production environments
        Uni<PipelineConfig> pipelineConfigUni;
        if (stepConfigsUnis.isEmpty()) {
            if (isProduction) {
                return Uni.createFrom().failure(new IllegalArgumentException(
                    "Pipeline must have at least one step in production mode"));
            }
            // Allow empty pipelines in dev/test mode
            LOG.warn("Creating pipeline '{}' with no steps (non-production mode)", pipelineRequest.name());
            Map<String, PipelineStepConfig> emptyStepsMap = new HashMap<>();
            pipelineConfigUni = Uni.createFrom().item(new PipelineConfig(pipelineRequest.name(), emptyStepsMap));
        } else {
            pipelineConfigUni = Uni.combine().all().unis(stepConfigsUnis)
            .with(listOfStepConfigs -> {
                Map<String, PipelineStepConfig> pipelineStepsMap = new HashMap<>();
                for (Object stepConfigObj : listOfStepConfigs) {
                    PipelineStepConfig stepConfig = (PipelineStepConfig) stepConfigObj;
                    pipelineStepsMap.put(stepConfig.stepName(), stepConfig);
                }
                // Note: PipelineConfig model from engine/models has only (name, pipelineSteps map)
                // Description and metadata from DTO are not directly part of this specific PipelineConfig constructor.
                // If description needs to be persisted, PipelineDefinitionService.createDefinition would need to handle it.
                return new PipelineConfig(pipelineRequest.name(), pipelineStepsMap);
            });
        }
        
        return pipelineConfigUni.onItem().transformToUni(pipelineConfig -> {
                LOG.info("Successfully transformed DTO to PipelineConfig for '{}'. Calling service.", pipelineConfig.name());
                return pipelineDefinitionService.createDefinition(pipelineConfig.name(), pipelineConfig)
                    .map(result -> {
                        if (result.valid()) {
                            return Response.status(Status.CREATED)
                                    .entity(new ApiResponse(true, "Pipeline definition created successfully.", null, null))
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
            })
            .onFailure().recoverWithItem(e -> {
                LOG.error("Error creating pipeline definition '{}' from DTO", pipelineRequest.name(), e);
                if (e instanceof WebApplicationException wae) {
                    return wae.getResponse();
                }
                return Response.status(Status.INTERNAL_SERVER_ERROR)
                        .entity(new ApiResponse(false, "Failed to create pipeline: " + e.getMessage(), null, null))
                        .build();
            });
    }

    private StepType convertServiceTypeToStepType(String serviceType, String moduleName) {
        if (serviceType == null) {
            LOG.warn("Module '{}' has null serviceType. Defaulting to StepType.PIPELINE.", moduleName);
            return StepType.PIPELINE; // Default or handle as error
        }
        try {
            // Assuming serviceType string matches one of the StepType enum names directly
            // e.g. "PIPELINE", "SINK". This needs to be robust.
            // The StepType enum is: PIPELINE, INITIAL_PIPELINE, SINK
            String upperServiceType = serviceType.toUpperCase();
            switch (upperServiceType) {
                case "PIPELINE":
                    return StepType.PIPELINE;
                case "SINK":
                    return StepType.SINK;
                case "INITIAL_PIPELINE": // If module registry can return this
                    return StepType.INITIAL_PIPELINE;
                // What about "PROCESSOR", "CONNECTOR", "ROUTER" from dev notes?
                // They are not in the current StepType enum.
                default:
                    LOG.warn("Unknown or unmapped serviceType '{}' for module '{}'. Defaulting to StepType.PIPELINE. Review mapping.", serviceType, moduleName);
                    return StepType.PIPELINE; // Fallback, needs review
            }
        } catch (IllegalArgumentException e) {
            LOG.warn("Failed to parse serviceType '{}' for module '{}' into a StepType enum. Defaulting to StepType.PIPELINE. Error: {}", serviceType, moduleName, e.getMessage());
            return StepType.PIPELINE; // Default on error
        }
    }

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