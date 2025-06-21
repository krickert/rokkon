# Rokkon Engine Development Guide

## Module Registration Architecture

### Overview
Modules in Rokkon Engine are "dumb" gRPC services that don't know about Consul or the engine. Each module container includes a CLI tool that handles registration with the engine on the module's behalf.

### Registration Flow
1. **Module container starts up**
   - Module exposes gRPC service implementing `PipeStepProcessor` interface
   - Implements three key RPCs:
     - `ProcessData()` - Main processing logic
     - `GetServiceRegistration()` - Returns module name and optional schema
     - `RegistrationCheck()` - Validation endpoint (similar to ProcessData but for testing)
   - Module does NOT connect to Consul or engine directly

2. **CLI tool runs inside the module container**
   - Called as part of container startup or via CI/CD after deployment
   - Connects to localhost to reach the module's gRPC service
   - Calls `GetServiceRegistration()` to get module's basic info
   - will also act as a full cli for developers

3. **CLI registers module with engine**
   - Connects to engine's `ModuleRegistrationService` (implemented in engine-registration, runs in rokkon-engine)
   - Provides full registration details including:
     - Module name and implementation ID
     - Host/port information
     - Health check configuration (gRPC only) TODO: delete other protocols but keep the placeholder
   - Engine validates the module:
     - Checks against whitelist (echo, chunker, parser, embedder, opensearch-sink)
     - Performs health check
     - Calls `RegistrationCheck()` to verify module functionality

4. **Engine writes to Consul**
   - Only the engine-consul component can write to Consul
   - Registers module as a gRPC service
   - Configures gRPC health checks
   - Stores module metadata for pipeline configuration
   - ALL calls to write to consul are validated and validate the entire config validation. 
   - There is an entire validation framework under engine-validators that help with this

5. **Consul monitors health**
   - Performs regular gRPC health checks using standard protocol
   - Marks service as healthy/unhealthy
   - Engine queries Consul for healthy services during pipeline execution

### Key Design Principles
- **Modules are stateless**: No Consul dependencies, just gRPC services
- **In-container registration**: CLI tool runs inside module container
- **Engine owns Consul writes**: Only engine-consul project writes to Consul
- **Health monitoring**: Consul handles gRPC health checks
- **Validation before registration**: RegistrationCheck ensures module works

### Module Validation with RegistrationCheck

The `RegistrationCheck` RPC is a new addition to the `PipeStepProcessor` interface:

```proto
service PipeStepProcessor {
  // Main processing logic
  rpc ProcessData(ProcessRequest) returns (ProcessResponse);
  
  // Returns basic module info for CLI
  rpc GetServiceRegistration(google.protobuf.Empty) returns (ServiceRegistrationData);
  
  // Validation endpoint - tests module functionality
  rpc RegistrationCheck(ProcessRequest) returns (ProcessResponse);
}
```

The `RegistrationCheck`:
- Receives a test `ProcessRequest` from the engine
- Should execute a lightweight version of the module's logic
- Returns success/failure to indicate if the module is functioning
- Helps prevent broken modules from being registered
- forces module author to think about how a dummy document should not end up on a prod system

### CLI Tool Architecture

The CLI tool is a simple utility that:
- Ships with each module container (language-agnostic)
- Runs after module startup (via entrypoint script or CI/CD webhook)
- Connects to the module on localhost
- Bridges the gap between the module and engine

Example container entrypoint:
```bash
#!/bin/bash
# Start the module
./my-module-service &

# Wait for module to be ready
sleep 5

# Register with engine
rokkon-cli register \
  --module-port=9090 \
  --engine-host=$ENGINE_HOST \
  --engine-port=$ENGINE_PORT
```

[Rest of the existing content remains unchanged]

## Development Best Practices

### Project Development Workflow
- **Compilation and Validation**
   - make sure everything compiles before moving onto the next task
   - double check for correctness and see what other projects may use any classes