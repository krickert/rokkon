# TODO: REST Endpoint Migration from Consul to Engine Module

## Overview
Move all REST API endpoints from the consul module (service library) to the engine module (application).
The consul module should only contain services that interact with Consul, not HTTP endpoints.

## REST Resources to Move

### 1. ClusterResource
- **File**: `/engine/consul/src/main/java/com/rokkon/pipeline/consul/api/ClusterResource.java`
- **Endpoints**: 
  - `GET /api/v1/clusters` - List all clusters
  - `POST /api/v1/clusters/{name}` - Create cluster
  - `GET /api/v1/clusters/{name}` - Get cluster
  - `DELETE /api/v1/clusters/{name}` - Delete cluster
- **Dependencies**: 
  - `ClusterService` (stays in consul module)
  - `ValidationResult` (from validators module)
- **Tests**: `ClusterResourceTest.java` (5 tests, all passing)

### 2. PipelineConfigResource  
- **File**: `/engine/consul/src/main/java/com/rokkon/pipeline/consul/api/PipelineConfigResource.java`
- **Endpoints**:
  - `POST /api/v1/clusters/{cluster}/pipelines/{pipeline}` - Create pipeline
  - `GET /api/v1/clusters/{cluster}/pipelines/{pipeline}` - Get pipeline
  - `PUT /api/v1/clusters/{cluster}/pipelines/{pipeline}` - Update pipeline
  - `DELETE /api/v1/clusters/{cluster}/pipelines/{pipeline}` - Delete pipeline
- **Dependencies**:
  - `PipelineConfigService` (stays in consul module)
  - `ClusterService` (stays in consul module)
  - `ValidationService` (from validators module)
- **Tests**: `PipelineConfigResourceTest.java` (9 tests, 6 passing, 3 failing)

### 3. GlobalModuleResource
- **File**: `/engine/consul/src/main/java/com/rokkon/pipeline/consul/api/GlobalModuleResource.java`
- **Endpoints**: (need to check)
- **Dependencies**: `GlobalModuleRegistryService`
- **Tests**: (need to check)

### 4. PipelineDefinitionResource
- **File**: `/engine/consul/src/main/java/com/rokkon/pipeline/consul/api/PipelineDefinitionResource.java`
- **Endpoints**: (need to check)
- **Tests**: (need to check)

### 5. PipelineInstanceResource
- **File**: `/engine/consul/src/main/java/com/rokkon/pipeline/consul/api/PipelineInstanceResource.java`
- **Endpoints**: (need to check)
- **Tests**: (need to check)

## Migration Steps Pattern

### For each resource:

1. **Copy Resource Class**
   - [ ] Copy from `com.rokkon.pipeline.consul.api` to `com.rokkon.engine.api`
   - [ ] Update package declaration
   - [ ] Keep all imports as-is initially

2. **Update Dependencies**
   - [ ] Add consul module dependency to engine if not present
   - [ ] Ensure all service classes are accessible
   - [ ] Check for any circular dependencies

3. **Copy Test Class**
   - [ ] Copy test from consul to engine test directory
   - [ ] Update package declaration
   - [ ] Update any test-specific configuration

4. **Verify Tests Pass**
   - [ ] Run tests in new location
   - [ ] Fix any configuration issues
   - [ ] Ensure all tests still pass

5. **Remove from Consul Module**
   - [ ] Delete original resource class
   - [ ] Delete original test class
   - [ ] Verify consul module still compiles

## Start with ClusterResource

Let's begin with `ClusterResource` as our first migration since all its tests are passing:

### TODO: Migrate ClusterResource
- [x] Copy ClusterResource.java to engine module
- [x] Copy ClusterResourceTest.java to engine module
- [x] Update imports and packages
- [ ] Run tests and verify they pass - BLOCKED
- [x] Remove from consul module

### Issues Encountered

**ClassNotFoundException**: `com.rokkon.search.grpc.MutinyModuleRegistrationGrpc$ModuleRegistrationImplBase`

The engine is trying to load the ModuleRegistrationServiceImpl from the consul module, which has a gRPC service that should actually be in the engine module. This reveals an architectural issue:

1. The consul module contains both business logic services AND gRPC services
2. The engine depends on consul for business logic but shouldn't pull in gRPC services
3. gRPC services belong in the engine module (the application layer)

### Next Steps

Before continuing with REST endpoint migration, we need to:
1. Move ModuleRegistrationServiceImpl from consul to engine module
2. Ensure consul module only contains business logic services
3. Engine module should contain all gRPC services and REST endpoints

## Configuration Changes Needed

### Engine Module
- [ ] Ensure HTTP/REST dependencies are present
- [ ] Add dependency on consul module for services
- [ ] Configure OpenAPI/Swagger
- [ ] Configure health endpoints

### Test Configuration
- [ ] Copy relevant test configuration
- [ ] Ensure ConsulTestResource is available
- [ ] Configure test ports

## Benefits After Migration
1. Clean separation of concerns
2. Consul module becomes a pure service library
3. All HTTP endpoints in the application module
4. Easier to test and maintain
5. Can potentially reuse consul services in other applications