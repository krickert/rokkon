# Rokkon Engine

## Overview

The Rokkon Engine is the core orchestration component of the Rokkon pipeline system. It manages module registration, pipeline execution, and serves as the central control plane for document processing workflows.

## Architecture

The engine serves multiple roles:

1. **Module Registration Hub** - Hosts the `ModuleRegistrationService` that validates and registers pipeline modules
2. **Pipeline Orchestrator** - Executes configured pipelines by routing documents through registered modules
3. **Configuration Reader** - Reads pipeline configurations from Consul (read-only)
4. **API Gateway** - Provides REST/WebSocket endpoints for frontend and CLI interactions

## Key Components

### Module Registration Service
- Implemented by `engine-registration` module, runs within this engine
- Validates modules against whitelist: `echo`, `chunker`, `parser`, `embedder`, `opensearch-sink`
- Performs health checks before registration
- Calls module's `RegistrationCheck` RPC to verify functionality

### Pipeline Execution Engine
- Discovers healthy modules via Consul service discovery
- Routes documents through pipeline steps using gRPC
- Handles failures with Dead Letter Queues (DLQ) per step
- Monitors pipeline execution status

### REST API Endpoints
- `/api/v1/modules/register` - Register a new module
- `/ping` - Simple health check endpoint
- `/api/v1/pipelines` - Pipeline CRUD operations (TODO)
- `/api/v1/modules` - List registered modules (TODO)
- `/api/v1/execute` - Execute pipeline with document (TODO)
- `/ws/status` - WebSocket for real-time status updates (TODO)

### Web Dashboard
The engine includes a web dashboard accessible at the root URL (`/`). The dashboard provides:
- Engine status monitoring
- Module registration interface
- List of registered modules

## Configuration

The engine reads configuration from:
- `application.yml` - Static configuration
- Consul KV - Dynamic pipeline configurations (read-only via quarkus-config-consul)
- Environment variables - Deployment-specific settings

## Module Communication

All module communication happens via gRPC:
- Uses Consul for service discovery
- Connects only to healthy modules
- Implements circuit breakers for resilience

## Running the Engine

### Development Mode
```bash
./gradlew quarkusDev
```

### Docker
```bash
docker run -p 8080:8080 \
  -e CONSUL_HOST=consul \
  -e CONSUL_PORT=8500 \
  rokkon/rokkon-engine:latest
```

### Environment Variables
- `CONSUL_HOST` - Consul server hostname (default: localhost)
- `CONSUL_PORT` - Consul server port (default: 8500)
- `ENGINE_PORT` - Engine HTTP port (default: 8080)
- `ENGINE_GRPC_PORT` - Engine gRPC port (default: 9099)

## Dependencies

- **engine-registration** - Module registration service implementation
- **engine-consul** - Consul writer component (handles all Consul writes)
- **engine-validators** - Configuration validation logic
- **commons-interface** - Shared interfaces and data models for pipeline configuration
- **commons-protobuf** - Protocol buffer definitions
- **commons-util** - Shared utilities

## Integration Points

### With Consul
- Reads service registry for module discovery
- Reads pipeline configurations from KV store
- Never writes directly (all writes go through engine-consul)

### With Modules
- Receives registration requests via `ModuleRegistrationService`
- Calls module RPCs: `ProcessData`, `RegistrationCheck`, `GetServiceRegistration`
- Uses gRPC health checks for monitoring

### With Frontend/CLI
- REST API for configuration and control
- WebSocket for real-time updates
- JSON-based request/response format

## Development Notes

### Adding New Module Types
1. Add module name to whitelist in registration validator
2. Update pipeline configuration schema if needed
3. Add any module-specific validation logic

### Testing
- Unit tests: `./gradlew test`
- Integration tests: `./gradlew integrationTest`
- Full ecosystem test: Use docker-compose with test-module

## Troubleshooting

### Module Registration Failures
- Check module is in whitelist
- Verify module implements all required RPCs
- Ensure module passes health check
- Check `RegistrationCheck` response

### Pipeline Execution Issues
- Verify all pipeline modules are healthy in Consul
- Check DLQ for failed messages
- Review engine logs for gRPC errors
- Ensure network connectivity between engine and modules
