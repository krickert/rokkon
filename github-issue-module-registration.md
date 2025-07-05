# Module Registration Not Persisting to Consul KV - Registration Claims Success but Data Not Stored

## Problem Description

Module registration is reporting success but the module data is not being persisted to Consul KV store. This causes modules to disappear when the engine restarts, even though the architecture is designed to use Consul as the source of truth for module state.

## Current Behavior

1. Echo module deploys successfully via Docker
2. Registration sidecar reports "Module registered successfully"  
3. API endpoint `/api/v1/modules` returns empty array
4. No data found in Consul KV under the expected prefix

## Expected Behavior

1. Module registration should persist to Consul KV at path `{kvPrefix}/modules/registered/{moduleId}`
2. Registered modules should survive engine restarts
3. `/api/v1/modules` should return modules from Consul KV

## Investigation Details

### What We Found

1. **GlobalModuleRegistryService IS designed to use Consul KV** (not in-memory storage)
   - Implementation in `GlobalModuleRegistryServiceImpl.java`
   - Stores at KV path: `{kvPrefix}/modules/registered/{moduleId}`
   - Currently using "rokkon" as kvPrefix (needs to change to "pipeline")

2. **Registration Flow:**
   - Module starts and registration sidecar calls engine's registration API
   - Engine should store in Consul KV via GlobalModuleRegistryService
   - `/api/v1/modules` queries GlobalModuleRegistryService which reads from Consul KV

3. **Current State Verification:**
   ```bash
   # Echo module is running
   docker ps --filter "name=echo"
   # Shows: echo-registrar, echo-module-app, consul-agent-echo

   # Registration claims success
   docker logs echo-registrar
   # Shows: "Module registered successfully!"

   # But no modules in API
   curl http://localhost:39001/api/v1/modules
   # Returns: []

   # No data in Consul KV
   curl http://localhost:8501/v1/kv/rokkon?keys
   # Returns: null
   ```

### Module State Matrix (Dev Mode)

| State | Docker | Consul Health | App Registry | UI Action |
|-------|--------|---------------|--------------|-----------|
| Zombie Type 1 | ✓ | ✗ | ✗ | Cleanup needed |
| Ready to Register | ✓ | ✓ (healthy) | ✗ | Show "Register" button |
| Unhealthy | ✓ | ✓ (unhealthy) | ✓ | Show as unhealthy |
| Zombie Type 2 | ✓ | ✗ | ✓ | Cleanup needed |
| Healthy | ✓ | ✓ (healthy) | ✓ | Normal state |

### Architecture Clarifications

- Module cards are GENERIC - display any service that registers
- Dev mode features (Deploy/Undeploy buttons) only show when `isDevMode=true`
- Registration persistence should work in both dev and prod modes
- Docker state is queried from Docker API (not tracked in memory)
- Consul is the source of truth for both health and registration

## Root Cause Possibilities

1. Registration endpoint not properly wired to call GlobalModuleRegistryService
2. Consul KV write failing silently
3. Missing error handling in registration flow
4. Possible transaction/async issue where success is reported before KV write completes

## Required Fixes

1. **Debug Registration Flow**
   - Add detailed logging to track registration request through the system
   - Verify GlobalModuleRegistryService is being called with correct data
   - Check for silent failures in Consul KV operations

2. **Update KV Prefix**
   - Change KV prefix from "rokkon" to "pipeline" throughout codebase
   - Ensure all Consul KV paths use consistent prefix

3. **Error Handling**
   - Add proper error handling for KV operations
   - Ensure registration sidecar receives accurate success/failure response
   - Log all failures with context

4. **UI State Management**
   - Implement "Ready to Register" state (Consul healthy but not in KV)
   - Add proper state transitions in dashboard

5. **Testing**
   - Add integration tests for module registration persistence
   - Test engine restart scenarios
   - Verify Consul KV state after registration

## Additional Context

- Using unique port allocation strategy (39100, 39101, etc.) for module instances
- All instances now get host ports (no more Docker internal IP issues)
- Registration should use host IP + allocated port
- Dev mode uses two-container Consul setup: server (port 38500) + agent (host network on 8501)

## Steps to Reproduce

1. Start engine in dev mode: `./gradlew :engine:pipestream:quarkusDev`
2. Deploy echo module via dashboard or API
3. Wait for "Module registered successfully!" in registrar logs
4. Check `/api/v1/modules` endpoint - returns empty array
5. Check Consul KV at `http://localhost:8501/v1/kv/rokkon?keys` - returns null
6. Restart engine - module disappears from dashboard

## Definition of Done

- [ ] Module registration persists to Consul KV
- [ ] Registered modules survive engine restarts
- [ ] `/api/v1/modules` returns modules from Consul KV
- [ ] Proper error handling and logging throughout registration flow
- [ ] KV prefix changed from "rokkon" to "pipeline"
- [ ] Integration tests for registration persistence
- [ ] Dashboard shows correct state for all module conditions

## Priority

**High** - Core functionality broken, prevents proper module management in production

## Labels

- bug
- backend
- consul
- module-management
- priority-high