# Rokkon Engine Developer Notes

## Pipeline Save Functionality Analysis (June 2025)

### Issue: "Method Not Allowed" When Saving Pipelines

#### Problem Summary
When attempting to save a pipeline from the frontend dashboard, users receive a "Failed to save pipeline: Method Not Allowed" error. This is due to TWO issues:

1. **Endpoint Mismatch**: The frontend makes a `POST` request to `/api/v1/pipelines/definitions` (base path), but the backend only implements `POST /api/v1/pipelines/definitions/{pipelineId}`.

2. **Field Name Mismatch**: The frontend sends step data with incorrect field names that don't match the backend's expected schema.

#### Root Causes

**Issue 1 - Missing Endpoint**: 
- Frontend expects: `POST /api/v1/pipelines/definitions` (standard RESTful pattern)
- Backend provides: `POST /api/v1/pipelines/definitions/{pipelineId}` only
- Result: 405 Method Not Allowed

**Issue 2 - Schema Mismatch**:
- Frontend sends:
  ```javascript
  steps: [{
    name: "step1",
    module: "test-module",
    config: {...}
  }]
  ```
- Backend expects:
  ```javascript
  steps: [{
    step_name: "step1",
    module_name: "test-module",
    module_version: "1.0.0",
    customConfig: {...}
  }]
  ```

### Pipeline Save Flow

#### Frontend Components

1. **User Interface** (`/META-INF/resources/index.html`)
   - Pipeline builder modal with form fields
   - Save button triggers `Pipelines.savePipeline()`

2. **Pipeline Management** (`/META-INF/resources/js/pipelines.js`)
   ```javascript
   savePipeline() {
       const pipelineData = {
           name: pipelineName,
           description: description,
           steps: this.currentPipeline.steps || []
       };
       
       if (isNewPipeline) {
           API.createPipeline(pipelineData)  // POST to base path
       } else {
           API.updatePipeline(name, pipelineData)  // PUT with ID
       }
   }
   ```

3. **API Client** (`/META-INF/resources/js/api.js`)
   ```javascript
   createPipeline(pipeline) {
       return this.post('/api/v1/pipelines/definitions', pipeline);
   }
   
   updatePipeline(name, pipeline) {
       return this.put(`/api/v1/pipelines/definitions/${name}`, pipeline);
   }
   ```

#### Backend Components

1. **REST Resource** (`com.rokkon.engine.api.PipelineDefinitionResource`)
   - Current endpoints:
     - `GET /api/v1/pipelines/definitions` - List all pipelines
     - `GET /api/v1/pipelines/definitions/{pipelineId}` - Get specific pipeline
     - `POST /api/v1/pipelines/definitions/{pipelineId}` - Create with ID in path ⚠️
     - `PUT /api/v1/pipelines/definitions/{pipelineId}` - Update pipeline
     - `DELETE /api/v1/pipelines/definitions/{pipelineId}` - Delete pipeline

2. **Service Layer** (`com.rokkon.pipeline.consul.service.PipelineDefinitionService`)
   - Handles CRUD operations
   - Validates pipeline configurations
   - Stores in Consul KV store

3. **Consul Storage**
   - Pipeline definitions stored at: `pipelines/definitions/{pipelineId}`
   - JSON serialized PipelineConfig objects

### Data Flow

```
Frontend                    Backend                     Storage
--------                    -------                     -------
Pipeline Form
    |
    v
savePipeline()
    |
    v
API.createPipeline()
    |
    | POST /api/v1/pipelines/definitions
    | Body: { name, description, steps }
    |
    v
PipelineDefinitionResource
    |
    | ❌ No handler for base path POST
    |
    v
405 Method Not Allowed
```

### Solutions

**Solution 1 - Add Missing Endpoint**:
Add a POST endpoint at the base path in `PipelineDefinitionResource.java`:

```java
@POST
@Operation(summary = "Create a pipeline definition")
public Uni<Response> createDefinition(PipelineConfig definition) {
    // Extract ID from request body
    String pipelineId = definition.getName();
    
    // Validate
    if (pipelineId == null || pipelineId.isBlank()) {
        return Uni.createFrom().item(
            Response.status(Status.BAD_REQUEST)
                .entity(new ApiResponse(false, "Pipeline name is required"))
                .build()
        );
    }
    
    // Delegate to existing service method
    return pipelineDefinitionService.createDefinition(pipelineId, definition)
        .map(result -> /* handle response */);
}
```

**Solution 2 - Fix Field Name Mapping**:
Update the frontend `pipelines.js` to use the correct field names:

```javascript
// In savePipeline() method
const steps = [];
for (const step of this.currentPipeline.steps) {
    steps.push({
        step_name: step.name,          // Changed from 'name'
        module_name: step.module,      // Changed from 'module'
        module_version: "1.0.0",       // Add required field
        customConfig: step.config      // Changed from 'config'
    });
}
```

**Alternative Quick Fix**:
Add a DTO mapper in the backend to handle both field name formats:

```java
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PipelineStepConfigDTO {
    @JsonAlias({"name", "step_name"})
    private String stepName;
    
    @JsonAlias({"module", "module_name"})
    private String moduleName;
    
    @JsonAlias({"config", "customConfig"})
    private Map<String, Object> customConfig;
    
    // ... getters, setters, conversion methods
}
```

### Pipeline Configuration Model

```java
public class PipelineConfig {
    private String name;                    // Pipeline identifier
    private String description;             // Human-readable description
    private List<PipelineStepConfig> steps; // Ordered list of steps
    private Map<String, String> metadata;   // Additional metadata
    // ... other fields
}

public class PipelineStepConfig {
    private String step_name;               // Step identifier
    private String module_name;             // Module to execute
    private String module_version;          // Module version
    private Map<String, Object> customConfig; // Step-specific config
    private Map<String, String> configParams; // Simple key-value params
    // ... other fields
}
```

### Validation

The pipeline save process includes two-tier validation:

1. **Creation Validation** (Lenient)
   - Allows draft pipelines
   - Basic structure checks
   - Name uniqueness

2. **Deployment Validation** (Strict)
   - Module availability
   - Configuration completeness
   - Dependency resolution

### Related Systems

1. **Module Registration**
   - Modules must be registered before use in pipelines
   - Registration data stored in `rokkon/modules/registered/{moduleId}`
   - Includes optional JSON schema for configuration validation

2. **Dashboard Auto-Refresh**
   - Recently fixed to preserve UI state during refresh
   - Tracks expanded/collapsed states
   - 60-second refresh interval

### Testing the Fix

After implementing the solution:

1. Create a new pipeline:
   ```bash
   curl -X POST http://localhost:8080/api/v1/pipelines/definitions \
     -H "Content-Type: application/json" \
     -d '{
       "name": "test-pipeline",
       "description": "Test pipeline",
       "steps": []
     }'
   ```

2. Verify in Consul:
   ```bash
   curl http://localhost:8500/v1/kv/pipelines/definitions/test-pipeline
   ```

3. Test from UI:
   - Open dashboard at http://localhost:8080
   - Navigate to Pipelines tab
   - Click "Create Pipeline"
   - Fill form and save

### Future Enhancements

1. **Schema-Driven Configuration**
   - Leverage module JSON schemas for step configuration validation
   - Generate dynamic forms based on schemas
   - Real-time validation feedback

2. **Pipeline Versioning**
   - Track pipeline definition changes
   - Allow rollback to previous versions
   - Compare versions side-by-side

3. **Import/Export**
   - Export pipeline definitions as JSON/YAML
   - Import from files
   - Share pipeline templates

### Common Issues

1. **Consul Connection**
   - Ensure Consul is running and accessible
   - Check `CONSUL_HOST` and `CONSUL_PORT` environment variables
   - Verify with: `curl http://localhost:8500/v1/status/leader`

2. **Module Not Found**
   - Verify module is registered: `curl http://localhost:8080/api/v1/modules`
   - Check module health status
   - Ensure module service is running

3. **Validation Errors**
   - Check browser console for detailed error messages
   - Review validation logic in `ValidationService`
   - Ensure required fields are populated

---

## Module Registration and Schema Storage

### Overview
Modules can optionally provide JSON schemas that describe their configuration requirements. This enables the UI to provide better validation and form generation.

### Schema Storage Flow

1. **Module Registration**
   - Module calls `ModuleRegistration.RegisterModule` RPC
   - Includes `jsonSchema` in metadata or implements `GetServiceRegistration`
   - Schema must be valid JSON Schema v7

2. **Schema Validation**
   - `GlobalModuleRegistryService` validates schema format
   - Stores in `ModuleRegistration` record

3. **Storage Location**
   - Path: `rokkon/modules/registered/{moduleId}`
   - Contains full `ModuleRegistration` object with `jsonSchema` field

### Current Status
- Infrastructure exists for schema storage
- Test modules don't provide schemas (jsonSchema = null)
- Frontend could leverage schemas for dynamic forms (future enhancement)

---

## Recent Fixes and Improvements

### Dashboard State Preservation (June 2025)
- Fixed auto-refresh losing expanded/collapsed states
- Added state tracking for module instances and gRPC services
- Increased refresh interval to 60 seconds

### Module Registration Testing
- Verified gRPC registration works correctly
- Multiple instances of same module type supported
- Host resolution (localhost, krick, etc.) working properly

### Frontend Modularization
- Refactored from 939-line monolithic HTML to modular structure
- Separate JS modules for different concerns
- Improved maintainability and testability