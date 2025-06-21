# Full Test Path Documentation

## Overview
This document captures the complete test path for the Rokkon Engine ecosystem, from initial setup through full pipeline execution.

## Architecture Principles

### Module Design
- Modules are "dumb" gRPC services - they only implement `PipeStepProcessor` interface
- Modules don't know about Consul or the engine
- All registration/orchestration happens in the engine

### Registration vs Whitelisting
- **Registration**: Makes a module "known" to the system (service discovery)
- **Whitelisting**: Authorizes a module for use in a specific pipeline
- Whitelisting is pipeline-scoped, not global

## Test Path Steps

### NOTE:
- As we test each of these steps, ensure that we capture these in TestSeedingService as all these steps build up and require the previous test to work.
- Make sure all test have a base, QuarkusTest and QuarkusIntegrationTest

### ✅ Step 0: Consul Starts
- Verify Consul is running and accessible
- Services can be injected
- Foundation for all subsequent operations

### ✅ Step 1: Create Clusters
- Create "default" cluster
- Create "test-cluster" 
- Clusters provide namespace/environment isolation
- Each cluster can have its own pipelines and configurations

### ✅ Step 2: Start Container
- test-module container starts via TestModuleContainerResource
- Exposes gRPC service on port 9090
- Implements PipeStepProcessor interface with:
  - `ProcessData()` - Main processing logic
  - `GetServiceRegistration()` - Returns module metadata
  - `RegistrationCheck()` - Validation endpoint

### ✅ Step 3: Register Container
**Current Implementation (Simplified)**:
- Direct Consul registration via HTTP API
- Registers as gRPC service with health checks

**Full Implementation TODOs**:
1. CLI tool calls test-module's `GetServiceRegistration()`
2. CLI calls engine-registration gRPC service
3. Engine-registration:
   - Calls `RegistrationCheck()` on module
   - Validates module functionality
   - Registers in Consul
   - Emits registration events

### ⏳ Step 4: Create Empty Pipeline
- Use PipelineConfigService to create pipeline
- Pipeline has name but no steps initially
- Creates context for whitelisting

### ⏳ Step 5: Whitelist & Add First Step
1. Whitelist test-module for the pipeline
2. Add first pipeline step using test-module
3. Configure step with:
   - Input/output mappings
   - Transport (Kafka/gRPC)
   - Error handling

### ⏳ Step 6: Test Module Execution
1. Create ProcessRequest with test data
2. Make gRPC call to test-module
3. Verify ProcessResponse
4. Use existing test data from ProtobufTestDataHelper

### ⏳ Step 7: Two Module Pipeline
1. Add second test-module step
2. Configure data flow between steps
3. Test end-to-end execution

## Test Data Available

From ProtobufTestDataHelper:
- Tika request streams
- Tika response documents  
- Parser output documents
- Chunker input/output
- Embedder input/output

## gRPC Testing Approach

### Direct Module Testing
```java
// Build ProcessRequest with test data
ProcessRequest request = ProcessRequest.newBuilder()
    .setDocument(testDoc)
    .setConfiguration(config)
    .build();

// Call module directly
ProcessResponse response = testModuleClient.processData(request)
    .await().indefinitely();
```

### Pipeline Execution Testing
- Submit document to pipeline
- Track execution through steps
- Verify transformations at each stage

## Event Architecture (Future)

### Phase 1 (Current): Direct Service Calls
- Services inject each other directly
- Synchronous execution
- Simple to understand and debug

### Phase 2: Quarkus Event Bus
- Replace direct calls with events
- `@Observes` and `Event<T>` for intra-JVM
- Better decoupling, same JVM

### Phase 3: Kafka Events
- Cross-service events
- Audit trail
- Async processing
- DLQ for error handling

## Key Implementation Files

### Core Services
- `ModuleRegistrationService` - Handles module registration in engine-consul
- `ModuleRegistrationServiceImpl` - gRPC implementation in engine-registration  
- `ConsulModuleRegistry` - Writes to Consul
- `PipelineConfigService` - Pipeline CRUD operations
- `ModuleWhitelistService` - Module authorization

### Test Infrastructure
- `TestSeedingService` - Orchestrates test setup
- `MethodicalBuildUpTest` - Validates each step
- `TestModuleContainerResource` - Manages test-module container

## Network Considerations

### Test Environment
- Tests use exposed ports (localhost:random-port)
- TestModuleContainerResource handles port mapping

### Docker Environment  
- Modules use Docker network names
- Fixed internal ports (9090)
- Consul health checks use internal addresses

### Production Environment
- Kubernetes service discovery
- Internal DNS names
- No port mapping needed

## Current Status & Next Steps

### Completed
- Basic infrastructure (Consul, clusters)
- Module container management
- Simplified registration

### In Progress
- Empty pipeline creation
- Module whitelisting
- gRPC test execution

### TODO
1. Implement full registration flow with engine-registration
2. Add RegistrationCheck validation
3. Create pipeline with steps
4. Execute gRPC calls with test data
5. Verify end-to-end data flow

## Testing Philosophy

### Methodical Approach
- Each step builds on previous
- Validate at each stage
- No "big bang" integration

### Reusable Components
- TestSeedingService for consistent setup
- Base test classes for unit/integration
- Shared test data via ProtobufTestDataHelper

### Progressive Enhancement
- Start with direct calls
- Add events when working
- Enhance with Kafka later