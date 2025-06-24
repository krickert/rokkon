# Methodical Test Progress & TODOs

## Current Status

### Completed Steps ✓
- **Step 0**: Consul starts and is accessible
- **Step 1**: Create clusters (default and test-cluster) with initial PipelineClusterConfig
- **Step 2**: Start container and verify test-module is running
- **Step 3**: Register the container using Vertx Consul client
- **Step 4**: Create empty pipeline
- **Step 5**: Whitelist module and add first pipeline step
- **Test Structure**: Properly implemented with base class, @QuarkusTest, and @QuarkusIntegrationTest
- **Validator Fix**: Relaxed overly restrictive INITIAL_PIPELINE/SINK requirements

### In Progress
- **Step 6**: Run test-module 2x to ensure it runs twice

## Architecture Recap

### Module Registration Flow
1. Module container starts (dumb gRPC service)
2. CLI/Test calls engine-registration gRPC service
3. Engine-registration:
   - Calls RegistrationCheck() on module
   - Validates module works
   - Registers in Consul
4. Module is now "available" but not whitelisted

### Whitelisting Flow (Separate from Registration)
1. Create a pipeline (can be empty)
2. Whitelist module for that specific pipeline
3. Module can now be used in pipeline steps

## Key Learnings
- Whitelisting is **pipeline-scoped**, not global
- Registration makes module "available"
- Whitelisting authorizes module for specific pipeline
- RegistrationCheck() prevents broken modules from entering system

## TODOs for Step 3 (Container Registration)

### Small Tasks to Complete Step 3:
1. **TODO**: Add engine-registration to engine-consul's dependencies
   ```gradle
   implementation project(':engine-registration')
   ```

2. **TODO**: Create a gRPC client for ModuleRegistration in TestSeedingServiceImpl
   ```java
   @GrpcClient("engine-registration") 
   ModuleRegistration moduleRegistrationClient;
   ```

3. **TODO**: Build ModuleInfo for test-module
   ```java
   ModuleInfo moduleInfo = ModuleInfo.newBuilder()
       .setServiceName("test-module")
       .setServiceId("test-module-" + UUID.randomUUID())
       .setHost("test-module") // Docker network name
       .setPort(9090)
       .build();
   ```

4. **TODO**: Call registerModule via gRPC
   ```java
   return moduleRegistrationClient.registerModule(moduleInfo)
       .map(response -> response.getSuccess());
   ```

5. **TODO**: Add Consul health check verification
   - Query `/v1/health/service/test-module`
   - Verify status is "passing"

## TODOs for Remaining Steps

### Step 4: Create Empty Pipeline ✓
- **DONE**: Implemented seedStep4_EmptyPipelineCreated()
- Creates empty PipelineConfig with no steps
- Validators confirmed to allow empty pipelines
- Pipeline cleanup added to teardown

### Step 5: Whitelist Module for Pipeline ✓
- **DONE**: Implemented seedStep5_FirstPipelineStepAdded()
- Whitelists test-module for the pipeline
- Creates PipelineStepConfig with PIPELINE type
- Updates pipeline configuration with the new step

### Step 6: Two Module Pipeline
- **TODO**: Implement seedStep6_TwoModulePipeline()
- Add second test-module step
- Verify both steps would execute

## Infrastructure TODOs

### For Full Implementation:
1. **TODO**: Start engine-registration service alongside engine-consul in tests
2. **TODO**: Configure gRPC endpoints in application.yml
3. **TODO**: Create Docker Compose for full stack testing
4. **TODO**: Implement proper CLI tool for production use
5. **TODO**: Implement REST API endpoints for services to enable integration tests
6. **TODO**: Create REST clients in MethodicalBuildUpIT for full integration testing

## Event-Driven Architecture (Phase 2)

### When Direct Calls Work:
1. **TODO**: Replace direct service calls with Quarkus Event Bus
2. **TODO**: Define events in common module
3. **TODO**: Make engine-consul observe events instead of direct calls

### Benefits:
- Decoupling between services
- Audit trail
- Easy testing with @InjectMock Event<T>

## Current Blockers
- Need engine-registration running in test environment
- Need gRPC client configuration
- Need to decide: mock vs real service for tests

## Next Immediate Steps
1. Get Step 3 working with simplified approach (direct Consul write)
2. Implement Step 4 (empty pipeline)
3. Then tackle proper gRPC integration