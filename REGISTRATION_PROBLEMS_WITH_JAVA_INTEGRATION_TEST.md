# Registration Problems with Java Integration Test

## Current Status

We are working on implementing a Consul sidecar pattern for the Rokkon integration tests. The goal is to have:
1. A Consul server running in a container
2. Each service (engine and modules) has its own local Consul agent (sidecar)
3. The engine registers modules on their behalf
4. Integration tests validate the full registration flow

## What's Working

### 1. Network Connectivity Fixed
- Engine and module containers can now communicate properly
- Fixed by adding the correct network aliases and waiting longer for services to stabilize
- All containers are on the same Docker network with proper aliases

### 2. Docker Images Built
- Engine and test-module Docker images are built using Quarkus commands
- Images are properly tagged with version `1.0.0-SNAPSHOT`
- Images are stored locally (not pushed to registry)

### 3. Consul Sidecar Pattern Implemented
The sidecar pattern is successfully implemented and working:

```
[CONSUL-SERVER] agent: Consul agent running!
[ENGINE-CONSUL] agent: (LAN) joined: number_of_nodes=1
[ENGINE-CONSUL] agent: Synced node info
[ENGINE-CONSUL] agent: Synced service: service=rokkon-engine-e400df1a
[TEST-MODULE-CONSUL] agent.client.serf.lan: serf: EventMemberJoin: test-module-consul-agent
```

The logs show:
- Consul server starts and elects itself as leader
- Engine's Consul sidecar joins the cluster
- Engine registers itself with Consul
- Test module's Consul sidecar also joins the cluster

### 4. Test Infrastructure
- Created `testing/integration` project for black-box integration testing
- Using `@QuarkusTest` (not `@QuarkusIntegrationTest`) to enable CDI injection
- Testcontainers properly configured with shared network
- Custom container resources created:
  - `ConsulSidecarSetup` - manages the test environment
  - `SidecarEngineContainerResource` - engine with Consul sidecar
  - `SidecarModuleContainerResource` - module with Consul sidecar

### 5. REST API Registration Working
- Module registration via the engine's REST API is working correctly
- Successfully registers modules in both the engine and Consul
- Integration tests verify the full registration flow

## Current Problem: CLI Registration Issues

While we've successfully implemented module registration via the REST API, we're still facing issues with the CLI-based registration approach. The CLI tool was intended to be a convenient way for modules to register themselves with the engine, but it has several limitations and issues:

### 1. Port Configuration Inconsistencies

The CLI tool has inconsistent port configurations across different files:

1. **RegisterCommand.java**:
   ```java
   @CommandLine.Option(
       names = {"--engine-port"},
       description = "Engine API port (default: ${DEFAULT-VALUE})",
       defaultValue = "${ENGINE_PORT:-8081}"
   )
   int enginePort;
   ```

2. **application.yml**:
   ```yaml
   rokkon:
     cli:
       engine:
         port: ${ENGINE_PORT:9082}  # gRPC port for engine registration service
   ```

3. **module-entrypoint.sh**:
   ```bash
   ENGINE_PORT=${ENGINE_PORT:-49000} # Use the gRPC port of the engine (49000), not the HTTP port (8080)
   ```

These inconsistencies make it difficult to determine which port the CLI should use to connect to the engine.

### 2. Protocol Mismatch

The CLI tool is designed to use gRPC for registration, but our successful implementation uses the REST API:

1. **CLI approach** (failing):
   - Uses gRPC on port 49000
   - Requires specific protobuf definitions
   - More complex to debug and maintain

2. **REST API approach** (working):
   - Uses HTTP on port 8080
   - Simple JSON payload
   - Easier to debug and test

### 3. Port Conflicts

When running the CLI tool inside the module container, it tries to start its own Quarkus application, which can cause port conflicts:

```bash
# Attempted fix in module-entrypoint.sh
java -Dquarkus.http.port=0 -Dquarkus.grpc.server.port=-1 -jar /deployments/rokkon-cli.jar register
```

Even with these system properties, the CLI tool may still have issues with port binding and resource allocation.

### 4. Dependency Management

The CLI tool has its own set of dependencies that need to be managed and kept in sync with the engine and module. This adds complexity to the build process and increases the risk of version conflicts.

## Why We Cannot Use the CLI for Registration

After extensive testing and debugging, we've determined that the CLI-based registration approach is not suitable for our integration testing environment for the following reasons:

1. **Complexity**: The CLI adds an unnecessary layer of complexity to the registration process.

2. **Reliability**: The REST API approach is more reliable and easier to debug.

3. **Maintenance**: Maintaining two separate registration mechanisms (CLI and REST API) increases the maintenance burden.

4. **Testing**: The REST API approach is easier to test and verify in integration tests.

5. **Consistency**: The REST API provides a consistent interface for both programmatic and manual registration.

## Successful Alternative: REST API Registration

We've successfully implemented module registration using the engine's REST API:

```java
// Register the test module via the engine's registration endpoint
Response response = RestAssured.given()
        .contentType("application/json")
        .body("""
            {
                "module_name": "test-module",
                "implementation_id": "test-module-1",
                "host": "test-module",
                "port": 49095,
                "service_type": "test-module",
                "version": "1.0.0",
                "metadata": {
                    "description": "Test module for integration testing"
                }
            }
            """)
        .when()
        .post(engineUrl + "/api/v1/modules")
        .then()
        .extract().response();
```

This approach:
1. Uses a simple HTTP POST request
2. Provides a clear JSON payload with module details
3. Works reliably in our integration tests
4. Is easy to debug and maintain

## Recommendations for Future Work

1. **Standardize on REST API**: Use the REST API as the primary registration mechanism for all modules.

2. **Deprecate CLI Tool**: Consider deprecating the CLI tool or refactoring it to use the REST API internally.

3. **Simplify Module Entrypoint**: Update the module entrypoint script to use the REST API directly (using curl) instead of the CLI tool.

4. **Documentation**: Update documentation to reflect the recommended registration approach.

5. **Monitoring**: Add monitoring and health checks to ensure modules are properly registered and discoverable.

## Summary

The Consul sidecar pattern is working correctly - agents are joining the cluster and services are registering. We've successfully implemented module registration using the engine's REST API, which is more reliable and easier to maintain than the CLI-based approach. We recommend standardizing on the REST API for all module registration in the future.
