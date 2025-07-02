# Module REST Component Requirements

## Overview
Create a reusable REST component that provides a standard HTTP/JSON interface for all gRPC modules, enabling easy testing and integration.

## Core Requirements

### 1. Base Component Location
- Create at `/commons/rest-module`
- Should be a library dependency, not a deployable module
- Available to all modules via: `implementation(project(":commons:rest-module"))`

### 2. Core Functionality
- **Abstract base class**: `BaseModuleResource`
- **Dynamic path configuration**: Use `@Path("/api/${module.name}")` 
- **Schema-driven**: Everything flows from `getServiceRegistration()`
- **Standard endpoints**:
  - `GET /info` - Returns module info and configuration schema
  - `POST /process` - Process documents with JSON input/output
  - `GET /health` - Simple health check
  - `POST /batch` - Process multiple documents (integrate with SampleDataLoader)

### 3. Schema Integration
- Read schema from `ServiceRegistrationResponse.getJsonConfigSchema()`
- Expose schema in `/info` endpoint for frontend/UI consumption
- Use schema for request validation if provided
- Support dynamic configuration based on schema

### 4. JSON/Protobuf Handling
- Automatic conversion between JSON and Protobuf messages
- Use `JsonFormat` for proper protobuf serialization
- Handle nested structures and custom config (`google.protobuf.Struct`)
- Preserve all protobuf field types and defaults

### 5. Configuration
- Module name from configuration: `@ConfigProperty(name = "module.name")`
- Support both hardcoded and dynamic module names
- Allow modules to override any default behavior

### 6. Testing Support
- Integration with `SampleDataLoader` for test data generation
- Batch processing endpoints for testing 100+ documents
- Schema-aware test data generation
- Support for isolated module testing

### 7. Implementation Pattern

#### Base Class (in commons/rest-module):
```java
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public abstract class BaseModuleResource {
    
    @ConfigProperty(name = "module.name", defaultValue = "${quarkus.application.name}")
    String moduleName;
    
    @Inject
    JsonProtobufConverter converter;
    
    @Inject
    SampleDataLoader sampleDataLoader;
    
    // Subclasses must provide their gRPC service
    protected abstract PipeStepProcessor getService();
    
    @GET
    @Path("/info")
    public Uni<Response> getServiceInfo() {
        // Standard implementation using getServiceRegistration()
    }
    
    @POST
    @Path("/process")
    public Uni<Response> processDocument(Map<String, Object> input) {
        // Standard JSON->Protobuf->JSON processing
    }
    
    @POST
    @Path("/batch")
    public Uni<Response> processBatch(BatchRequest request) {
        // Process multiple documents using SampleDataLoader
    }
    
    @GET
    @Path("/health")
    public Uni<Response> health() {
        // Simple health check
    }
}
```

#### Module Implementation:
```java
@Path("/api/${module.name:chunker}")
@Tag(name = "Chunker Service", description = "Document chunking service")
public class ChunkerResource extends BaseModuleResource {
    
    @Inject
    @GrpcService
    PipeStepProcessor chunkerService;
    
    @Override
    protected PipeStepProcessor getService() {
        return chunkerService;
    }
    
    // Optional: Add module-specific endpoints if needed
}
```

### 8. Utilities Needed
- `JsonProtobufConverter` - Bidirectional JSON/Protobuf conversion
- `ModuleRestConfig` - Configuration holder
- `BatchProcessor` - Handle batch operations with progress
- `SchemaValidator` - Optional schema-based validation

### 9. Benefits
- **Consistency**: All modules expose the same REST interface
- **Less code**: Modules only need ~10 lines to get full REST API
- **Schema-driven**: Frontend can auto-generate forms
- **Testing**: Easy module testing without full pipeline
- **Documentation**: OpenAPI/Swagger comes free

### 10. Future Extensions
- WebSocket support for streaming operations
- Metrics endpoints
- Module-specific configuration UI
- GraphQL interface (if needed)

### 11. Non-Java Module Support
- The same REST interface can be provided by the module-proxy
- Python/Go/Rust modules only need to implement gRPC
- Proxy handles REST translation using the same patterns

## Success Criteria
1. Reduce module REST code by 90%
2. All modules have consistent REST APIs
3. Frontend can discover and use any module via schema
4. Testing any module in isolation is trivial
5. Adding REST to a new module takes <5 minutes