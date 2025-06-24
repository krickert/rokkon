# Echo Module

## Overview
The Echo Module is a simple utility service that echoes back documents with added metadata. It serves as a basic example of a Rokkon pipeline module and can be used for testing, debugging, and as a reference implementation.

## Features
- Echoes back documents without modifying their content
- Adds metadata to documents for tracking and debugging
- Provides REST endpoints for direct interaction
- Implements the standard Rokkon gRPC service interface

## How It Works
The Echo Module:
1. Receives documents through gRPC
2. Adds metadata to the document's custom_data field
3. Returns the document with the added metadata

## Deployment

### Prerequisites
- JDK 21 or later
- Docker (for containerized deployment)
- Access to Rokkon Engine and Consul services

### Building the Module
The module can be built using the provided `docker-build.sh` script:

```bash
# Build in production mode
./docker-build.sh

# Build in development mode
./docker-build.sh dev
```

### Running the Module
After building, you can run the module using Docker:

```bash
# Production mode
docker run -i --rm -p 49095:49095 \
  -e ENGINE_HOST=engine \
  -e CONSUL_HOST=consul \
  rokkon/echo-module:latest

# Development mode (uses host networking)
docker run -i --rm --network=host rokkon/echo-module:dev
```

### Testing the Module
Use the provided `test-docker.sh` script to test the module:

```bash
# Test in development mode
./test-docker.sh

# Test in production mode
./test-docker.sh prod
```

## Configuration

### Environment Variables
- `MODULE_HOST`: Host address for the module (default: 0.0.0.0)
- `MODULE_PORT`: Port for the module (default: 9090)
- `ENGINE_HOST`: Host address for the Rokkon Engine (default: localhost)
- `ENGINE_PORT`: Port for the Rokkon Engine (default: 8081)
- `CONSUL_HOST`: Host address for Consul (default: empty)
- `CONSUL_PORT`: Port for Consul (default: -1)
- `HEALTH_CHECK`: Whether to perform health checks (default: true)
- `MAX_RETRIES`: Maximum number of registration retries (default: 3)

### Metadata Added
The Echo Module adds the following metadata to documents:

- `processed_by_echo`: Always set to "echo-module"
- `echo_timestamp`: ISO-8601 timestamp of when the document was processed
- `echo_module_version`: Version of the echo module
- `echo_stream_id`: Stream ID from the request metadata (if available)
- `echo_step_name`: Pipeline step name from the request metadata (if available)

## Integration
The Echo Module integrates with the Rokkon pipeline through:

1. **gRPC Service**: Implements the `PipeStepProcessor` service defined in the Rokkon protobuf
2. **Registration**: Automatically registers with the Rokkon Engine on startup
3. **Health Checks**: Provides health status through the standard gRPC health check protocol

## REST API
The module also provides REST endpoints for direct interaction:

- `GET /echo/info`: Returns information about the module
- `POST /echo/document`: Echoes back the posted document with added metadata
- `GET /debug/health`: Returns the health status of the module

## Development
The module is built with Quarkus and uses:
- Mutiny for reactive programming
- Micrometer and OpenTelemetry for observability

To contribute to the module, follow the standard Rokkon development workflow.