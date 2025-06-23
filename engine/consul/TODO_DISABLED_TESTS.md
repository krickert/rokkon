# TODO: Disabled Tests in engine/consul

## Summary
- **Total**: 50 @Disabled tests
- **RE-ENABLE**: 14 tests (REST API tests with port binding issues)
- **MOVE TO INTEGRATION**: 33 tests (require real Consul)
- **DISCARD**: 2 tests (trivial/incomplete)
- **KEEP DISABLED**: 1 test (not implemented)

## Action Plan

### 1. RE-ENABLE These Tests (14 tests)
These are REST API tests that failed due to port binding. Should work with proper test config.

#### ClusterResourceTest.java (5 tests)
- [ ] `testCreateClusterViaRest` - Creates cluster via REST POST
- [ ] `testGetClusterViaRest` - Retrieves cluster via REST GET
- [ ] `testGetNonExistentCluster` - Tests 404 for missing cluster
- [ ] `testDeleteClusterViaRest` - Tests cluster deletion
- [ ] `testCreateDuplicateCluster` - Tests 400 for duplicate cluster

#### PipelineConfigResourceTest.java (9 tests)
- [ ] `testCreatePipeline` - Creates pipeline via REST POST
- [ ] `testGetPipeline` - Retrieves pipeline via REST GET
- [ ] `testUpdatePipeline` - Updates pipeline configuration
- [ ] `testDeletePipeline` - Deletes pipeline
- [ ] `testCreateInvalidPipeline` - Tests validation (no SINK)
- [ ] `testConcurrentPipelineCreation` - Tests 409 conflict
- [ ] `testOpenApiEndpoint` - Verifies OpenAPI spec endpoint
- [ ] `testSwaggerUIEndpoint` - Verifies Swagger UI endpoint
- [ ] `testHealthEndpoint` - Verifies health check endpoint

**Fix**: Update test configuration to properly handle Quarkus port binding

### 2. MOVE TO INTEGRATION Tests (33 tests)
These require real Consul and should be in a separate integration test suite.

#### Create IntegrationTest profile with:
```java
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
```

#### Tests to move:

**MethodicalBuildUpTestBase.java** (6 tests)
- [ ] `testConsulStarts` - Verifies Consul connectivity
- [ ] `testCreateCluster` - Creates clusters in Consul
- [ ] `testContainerAccess` - Tests container connectivity
- [ ] `testRegisterContainer` - Module registration flow
- [ ] `testCreateEmptyPipeline` - Pipeline creation
- [ ] `testAddFirstPipelineStep` - Pipeline step addition

**ClusterServiceTestBase.java** (6 tests)
- [ ] `testCreateCluster` - Cluster service operations
- [ ] `testGetCluster`
- [ ] `testClusterExists`
- [ ] `testCreateDuplicateCluster`
- [ ] `testDeleteCluster`
- [ ] `testEmptyClusterName`

**ModuleWhitelistServiceTest.java** (6 tests)
- [ ] `testWhitelistModuleNotInConsul`
- [ ] `testWhitelistModuleSuccess`
- [ ] `testListWhitelistedModules`
- [ ] `testRemoveModuleFromWhitelist`
- [ ] `testCantCreatePipelineWithNonWhitelistedModule`
- [ ] `testCantRemoveWhitelistedModuleInUse`

**ModuleWhitelistServiceTestBase.java** (7 tests)
- [ ] `testWhitelistModuleSuccess`
- [ ] `testWhitelistModuleNotInConsul`
- [ ] `testWhitelistModuleTwice`
- [ ] `testRemoveModuleFromWhitelist`
- [ ] `testRemoveModuleInUse`
- [ ] `testWhitelistValidatesAgainstPipelines`
- [ ] `testListWhitelistedModules`

**PipelineConfigServiceTestBase.java** (8 tests)
- [ ] `testCreateSimplePipeline`
- [ ] `testCreatePipelineWithConsul`
- [ ] `testUpdatePipeline`
- [ ] `testDeletePipeline`
- [ ] `testListPipelines`
- [ ] `testConcurrentPipelineCreation`
- [ ] `testConcurrentPipelineUpdates`
- [ ] `testCreateCluster` (placeholder)

### 3. DISCARD These Tests (2 tests)
These provide no value and should be removed.

- [ ] `PipelineConfigServiceTestBase.testServiceInjection` - Just checks != null
- [ ] `PipelineConfigServiceTestBase.testConsulKeyGeneration` - String contains check

### 4. KEEP DISABLED (1 test)
Not implemented yet, keep for future development.

- [ ] `MethodicalBuildUpTestBase.testRunModuleTwice` - Pipeline execution test

## Implementation Steps

### Step 1: Fix REST API Tests
1. Add proper test configuration for port binding
2. Re-enable all 14 REST API tests
3. Verify they pass with container reuse

### Step 2: Create Integration Test Structure
1. Create `src/integrationTest/java` source set
2. Add `@QuarkusIntegrationTest` profile
3. Move 33 Consul-dependent tests
4. Configure to run separately from unit tests

### Step 3: Clean Up
1. Delete 2 trivial tests
2. Document the 1 disabled test for future implementation

### Step 4: Update CI/CD
1. Run unit tests first (fast)
2. Run integration tests separately (slower, needs Consul)
3. Consider parallel execution for integration tests

## Benefits
- **Faster feedback**: Unit tests run without containers
- **Better organization**: Clear separation of concerns
- **Improved CI/CD**: Can fail fast on unit tests
- **Container reuse**: Integration tests share Consul container