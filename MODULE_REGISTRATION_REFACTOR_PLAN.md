# Module Registration Refactor Plan

## Overview
This document outlines the comprehensive plan to refactor the module registration system in Rokkon Engine. The goal is to simplify the registration flow, remove unnecessary complexity, and create a single source of truth for module registration.

## Current State Problems
1. Multiple registration endpoints (GlobalModuleResource and ModuleRegistrationResource)
2. Complex whitelist/visibility system that adds no value
3. Streaming registration code that's overly complex
4. Missing health checks in CLI validation
5. No proper service layer separation
6. Schema validation not enforced

## Target Architecture

### Registration Flow
```
1. Module Container Startup
   └── Module starts (implements PipeStepProcessor)
   └── CLI runs in container
       └── Calls module.GetServiceRegistration()
       └── Validates module health
       └── Sends registration to Engine

2. Engine Registration (GlobalModuleResource)
   └── Receives registration request
   └── Validates connectivity to module
   └── Validates schema (if provided)
   └── Stores schema (first time only)
   └── Registers in Consul
   └── Returns success/failure
```

### Key Principles
- **Single Entry Point**: GlobalModuleResource is the ONLY registration endpoint
- **Dumb Modules**: Modules only implement PipeStepProcessor, unaware of engine
- **Schema Validation**: Any schema mismatch throws IllegalArgumentException
- **No Complexity**: No whitelists, no visibility levels, no permissions (for now)
- **Simple Rule**: Registered = Available to everyone

## Implementation Steps

### Phase 1: Clean Up GlobalModuleResource
**Priority: HIGH**

1. **Remove Visibility/Whitelist Logic**
   - Remove `ModuleVisibility` enum usage
   - Remove `isGatekeeperNode` checks
   - Remove public/private/restricted endpoints
   - Remove whitelist validation
   - Simplify `RegisterModuleRequest` to essential fields only

2. **Extract Service Layer**
   - Create `ModuleRegistrationServiceImpl` in engine-consul
   - Move all business logic from resource to service
   - Resource should only handle HTTP concerns
   - Service handles validation, Consul interaction, schema storage

3. **Simplify Data Model**
   ```java
   public record RegisterModuleRequest(
       String moduleName,
       String implementationId,
       String host,
       int port,
       String serviceType,  // Always "grpc" for now
       String version,
       Map<String, String> metadata,
       String jsonSchema    // Optional schema from module
   ) {}
   ```

### Phase 2: Add Proper Validation
**Priority: HIGH**

1. **Schema Validation**
   - If module provides schema, validate it's proper JSON Schema
   - Store schema on first registration
   - On subsequent registrations, compare schemas
   - Throw `IllegalArgumentException` on mismatch
   - Log schema version changes for audit

2. **Health Check Implementation**
   - CLI must perform gRPC health check before registration
   - Engine must verify connectivity to module
   - Use standard gRPC health check protocol
   - Fail fast if module unreachable

3. **Module Validation**
   - Verify module implements required RPCs
   - Test `ProcessData` with dummy request
   - Ensure `GetServiceRegistration` returns valid data

### Phase 3: Remove Duplicate Code
**Priority: HIGH**

1. **Delete ModuleRegistrationResource**
   - Remove from rokkon-engine project
   - Update any references to use GlobalModuleResource

2. **Clean Streaming Registration**
   - Delete `StreamingRegistrationServiceImpl` (both copies)
   - Remove `module_registration_streaming.proto`
   - Remove all CLI registration message types
   - Simplify to REST-based registration only

3. **Remove Test Artifacts**
   - Delete test scripts (test-*.sh)
   - Remove CLI project if not needed
   - Clean up any temporary proto files

### Phase 4: Implement Core Features
**Priority: MEDIUM**

1. **Schema Storage Service**
   ```java
   public interface SchemaStorageService {
       Uni<Void> storeSchema(String moduleName, String schema);
       Uni<Optional<String>> getSchema(String moduleName);
       Uni<Boolean> validateSchema(String moduleName, String newSchema);
   }
   ```

2. **Module Connectivity Check**
   - Create reusable health check client
   - Implement timeout handling
   - Log connectivity issues
   - Return clear error messages

3. **Idempotent Registration**
   - Check if module already registered
   - If yes, validate schema matches
   - Update metadata if needed
   - Return success (not error) for re-registration

### Phase 5: Integration
**Priority: MEDIUM**

1. **Update CLI Tool**
   - Simple HTTP client to call GlobalModuleResource
   - Add proper health check before registration
   - Clear error messages for failures
   - Retry logic with backoff

2. **Consul Integration**
   - Ensure proper service registration format
   - Set up health check configuration
   - Handle Consul errors gracefully
   - Implement proper cleanup on deregistration

## API Changes

### Before (Multiple Endpoints)
```
POST /api/v1/modules/register          (rokkon-engine)
POST /api/v1/global-modules/register   (engine-consul)
GET  /api/v1/global-modules/public
POST /api/v1/global-modules/{id}/clusters/{cluster}/enable
```

### After (Single Endpoint)
```
POST /api/v1/modules                   # Register module
GET  /api/v1/modules                   # List all modules
GET  /api/v1/modules/{moduleId}        # Get module details
DELETE /api/v1/modules/{moduleId}      # Deregister module
POST /api/v1/modules/{moduleId}/archive # Archive unhealthy module
```

## Error Handling

### Registration Errors
- **400 Bad Request**: Invalid module data, failed validation
- **409 Conflict**: Schema mismatch with existing registration
- **503 Service Unavailable**: Cannot reach module, Consul down
- **500 Internal Error**: Unexpected errors

### Error Response Format
```json
{
  "success": false,
  "error": {
    "code": "SCHEMA_MISMATCH",
    "message": "Module schema does not match stored schema",
    "details": {
      "moduleName": "chunker",
      "expectedSchema": "...",
      "providedSchema": "..."
    }
  }
}
```

## Testing Strategy

1. **Unit Tests**
   - Schema validation logic
   - Service layer business logic
   - Error handling paths

2. **Integration Tests**
   - Full registration flow
   - Consul interaction
   - Health check validation
   - Schema storage and retrieval

3. **Container Tests**
   - CLI to module communication
   - Module to engine registration
   - Network connectivity scenarios

## Migration Path

1. **Week 1**: Implement new GlobalModuleResource with service layer
2. **Week 2**: Add validation and health checks
3. **Week 3**: Remove old code and clean up
4. **Week 4**: Testing and documentation

## Success Criteria

- [ ] Single registration endpoint operational
- [ ] Schema validation working with proper errors
- [ ] Health checks implemented in CLI and engine
- [ ] All duplicate code removed
- [ ] Tests passing with >80% coverage
- [ ] No whitelist/visibility complexity
- [ ] Clean, understandable codebase

## Notes

- Keep it simple - complexity can be added later if needed
- Fail fast with clear error messages
- Make registration idempotent
- Focus on developer experience
- Document all decisions

## Future Considerations

- Authentication/authorization (later)
- Multi-tenancy support (later)
- Module versioning strategy (later)
- Hot-reload capabilities (later)