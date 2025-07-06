# Rokkon Integration Tests

This module contains black-box integration tests that verify the complete Rokkon system works correctly with all components running in Docker containers.

## Overview

The integration tests spin up a complete environment including:
- Consul server (for service discovery and configuration)
- Consul agent sidecars (for each application container)
- Rokkon Engine (with Consul sidecar)
- Test Module (with Consul sidecar)

This module leverages container utilities from the `testing/util` module:
- `EngineContainerResource` - Manages the Rokkon Engine container
- `TestModuleContainerResource` - Manages test module containers
- `SharedNetworkManager` - Ensures all containers share the same Docker network
- `QuarkusDockerTestSupport` - Provides Docker utilities for Quarkus tests

## Architecture

The test environment uses the Consul sidecar pattern where each application container has its own local Consul agent that connects back to the main Consul server. This allows applications to access Consul via `localhost:8500` within their container, which is a common production pattern.

```
┌─────────────────────┐     ┌─────────────────────┐
│   Consul Server     │     │   Test Runner       │
│   (consul-server)   │     │   (Your machine)    │
└──────────┬──────────┘     └──────────┬──────────┘
           │                           │
      ┌────┴────┬──────────────┬──────┴────┐
      │         │              │           │
┌─────▼────┐ ┌─▼──────────┐ ┌─▼───────┐ ┌─▼──────────┐
│  Consul  │ │   Engine   │ │ Consul  │ │Test Module │
│  Agent   │ │ Container  │ │ Agent   │ │ Container  │
│ (Engine) │ │            │ │(Module) │ │            │
└──────────┘ └────────────┘ └─────────┘ └────────────┘
```

## Running the Tests

### Prerequisites

1. Docker must be installed and running
2. Build the Docker images for engine and test-module:
   ```bash
   ./gradlew :rokkon-engine:build
   ./gradlew :modules:test-module:build
   ```

### Run Integration Tests

Integration tests are located in `src/integrationTest/java` and use `@QuarkusIntegrationTest`. 
Note that these tests do not support CDI injection and must use system properties for configuration.

From the project root:
```bash
./gradlew :testing:integration:quarkusIntTest
```

Or to run a specific test:
```bash
./gradlew :testing:integration:quarkusIntTest --tests "*EngineModuleRegistrationIT*"
```

### Test Classes

1. **ContainerSetup** - Basic setup using existing container resources from testing/util
2. **ContainerSetupWithSidecars** - Sets up the complete Docker environment with Consul sidecars
3. **ConsulSidecarSetup** - Clean implementation using custom sidecar resources
4. **SidecarEngineContainerResource** - Extension of EngineContainerResource with Consul sidecar
5. **SidecarModuleContainerResource** - Extension of ModuleContainerResource with Consul sidecar
6. **EngineModuleRegistrationIT** - Tests the full module registration flow:
   - Engine starts successfully
   - Test module starts successfully  
   - Engine registers the module on its behalf
   - Module appears in Consul
   - Engine dashboard shows the registered module

## Key Features

### Consul Sidecar Pattern
Each application container has its own Consul agent that acts as a local proxy to the Consul server. This provides:
- Better network isolation
- Local caching of configuration
- Reduced latency for Consul operations
- Production-like setup

### Module Registration Flow
The test module is configured with `MODULE_REGISTRATION_ENABLED=false`, which means it will NOT self-register. Instead:
1. The test module starts and waits
2. The engine discovers the module
3. The engine registers the module with Consul on its behalf
4. The module becomes available for pipeline processing

## Debugging

### View Container Logs
All containers log with prefixes to help identify the source:
- `[CONSUL-SERVER]` - Main Consul server
- `[CONSUL-AGENT-ENGINE]` - Consul sidecar for engine
- `[CONSUL-AGENT-MODULE]` - Consul sidecar for test module
- `[ENGINE]` - Pipeline Engine
- `[TEST-MODULE]` - Test module

### Access Services During Tests
When tests are running, you can access:
- Consul UI: http://localhost:8500 (mapped port varies)
- Engine Dashboard: http://localhost:8080 (mapped port varies)
- Test Module HTTP: http://localhost:8082 (mapped port varies)

The actual ports are logged when the containers start.

### Common Issues

1. **Containers fail to start**: Ensure Docker daemon is running and you have sufficient resources
2. **Module doesn't register**: Check that the engine can reach the test module via the Docker network
3. **Consul connection issues**: Verify the Consul agents are connecting to the server correctly

## Configuration

Test configuration can be adjusted in:
- `src/integrationTest/resources/application.yml` - Integration test-specific Quarkus configuration
- Container setup classes in `src/integrationTest/java` - Container startup parameters and environment variables

Note: Integration tests using `@QuarkusIntegrationTest` do not support CDI injection. Configuration must be accessed via system properties set by the `QuarkusTestResourceLifecycleManager`.

## Future Enhancements

- Add more modules to test complex pipeline scenarios
- Test failure scenarios (module crashes, network partitions)
- Performance testing with multiple module instances
- Test pipeline execution with actual data flow