package com.rokkon.search.config.pipeline.api;

import com.rokkon.search.config.pipeline.model.PipelineConfig;
import com.rokkon.search.config.pipeline.service.ConfigValidationService;
import com.rokkon.search.config.pipeline.service.SchemaValidationService;
import com.rokkon.search.config.pipeline.service.validation.ValidationResult;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST API for configuration validation and utility operations.
 */
@Path("/api/v1/validation")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Validation & Utilities", description = "Configuration validation and utility operations")
public class ValidationResource {
    
    @Inject
    ConfigValidationService configValidationService;
    
    @Inject
    SchemaValidationService schemaValidationService;
    
    @POST
    @Path("/pipeline")
    @Operation(
        summary = "Validate pipeline configuration",
        description = "Validates a pipeline configuration without storing it. " +
                     "Performs structural validation, naming convention checks, and schema validation."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Validation completed",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ValidationResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid request format"
        )
    })
    public Uni<Response> validatePipeline(
            @Parameter(description = "Pipeline ID for validation context", example = "test-pipeline")
            @QueryParam("pipelineId") @DefaultValue("validation-test") String pipelineId,
            
            @Parameter(description = "Pipeline configuration to validate", required = true)
            PipelineConfig config) {
        
        return Uni.createFrom().completionStage(
            configValidationService.validatePipelineStructure(pipelineId, config)
        ).map(validationResult -> 
            Response.ok(new ValidationResponse(
                validationResult.valid(),
                validationResult.errors(),
                validationResult.warnings(),
                "STRUCTURAL_VALIDATION"
            )).build()
        );
    }
    
    @POST
    @Path("/service-name")
    @Operation(
        summary = "Validate service name",
        description = "Validates that a service name follows Rokkon naming conventions."
    )
    public Response validateServiceName(
            @Parameter(description = "Service name to validate", required = true)
            ServiceNameRequest request) {
        
        ValidationResult result = configValidationService.validateServiceName(request.serviceName());
        
        return Response.ok(new ValidationResponse(
            result.valid(),
            result.errors(),
            result.warnings(),
            "SERVICE_NAME_VALIDATION"
        )).build();
    }
    
    @POST
    @Path("/kafka-topic")
    @Operation(
        summary = "Validate Kafka topic name",
        description = "Validates that a Kafka topic name follows naming conventions."
    )
    public Response validateKafkaTopic(
            @Parameter(description = "Topic name to validate", required = true)
            TopicNameRequest request) {
        
        ValidationResult result = configValidationService.validateKafkaTopic(request.topicName());
        
        return Response.ok(new ValidationResponse(
            result.valid(),
            result.errors(),
            result.warnings(),
            "TOPIC_NAME_VALIDATION"
        )).build();
    }
    
    @POST
    @Path("/json")
    @Operation(
        summary = "Validate JSON syntax",
        description = "Validates that a JSON string is well-formed."
    )
    public Uni<Response> validateJson(
            @Parameter(description = "JSON content to validate", required = true)
            JsonValidationRequest request) {
        
        return Uni.createFrom().completionStage(
            schemaValidationService.isValidJson(request.jsonContent())
        ).map(isValid -> {
            if (isValid) {
                return Response.ok(new ValidationResponse(
                    true,
                    java.util.List.of(),
                    java.util.List.of(),
                    "JSON_SYNTAX_VALIDATION"
                )).build();
            } else {
                return Response.ok(new ValidationResponse(
                    false,
                    java.util.List.of("Invalid JSON syntax"),
                    java.util.List.of(),
                    "JSON_SYNTAX_VALIDATION"
                )).build();
            }
        });
    }
    
    @GET
    @Path("/schemas/pipeline-cluster")
    @Operation(
        summary = "Get pipeline cluster schema",
        description = "Returns the JSON Schema for pipeline cluster configurations."
    )
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> getPipelineClusterSchema() {
        return Uni.createFrom().completionStage(
            schemaValidationService.getPipelineClusterConfigSchema()
        ).map(schema -> Response.ok(schema).build());
    }
    
    @GET
    @Path("/schemas/pipeline-step")
    @Operation(
        summary = "Get pipeline step schema", 
        description = "Returns the JSON Schema for pipeline step configurations."
    )
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> getPipelineStepSchema() {
        return Uni.createFrom().completionStage(
            schemaValidationService.getPipelineStepConfigSchema()
        ).map(schema -> Response.ok(schema).build());
    }
    
    // Request/Response DTOs
    
    @Schema(description = "Validation result response")
    public record ValidationResponse(
            @Schema(description = "Whether validation passed") boolean valid,
            @Schema(description = "List of validation errors") java.util.List<String> errors,
            @Schema(description = "List of validation warnings") java.util.List<String> warnings,
            @Schema(description = "Type of validation performed") String validationType
    ) {}
    
    @Schema(description = "Service name validation request")
    public record ServiceNameRequest(
            @Schema(description = "Service name to validate", example = "my-search-service") String serviceName
    ) {}
    
    @Schema(description = "Topic name validation request")
    public record TopicNameRequest(
            @Schema(description = "Kafka topic name to validate", example = "processed-documents") String topicName
    ) {}
    
    @Schema(description = "JSON validation request")
    public record JsonValidationRequest(
            @Schema(description = "JSON content to validate") String jsonContent
    ) {}
}