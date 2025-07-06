# Chunker Module

## Overview
The Chunker Module is a specialized text processing service that breaks documents into smaller, overlapping chunks for further processing in the Rokkon pipeline. It's designed to prepare text for semantic analysis, embedding generation, and other NLP tasks by creating optimally sized text segments.

## Features
- Configurable chunk size and overlap settings
- Automatic metadata extraction for each chunk
- Support for preserving URLs and special content during chunking
- Comprehensive chunk analysis including:
  - Word and sentence counts
  - Text complexity metrics
  - Structural analysis (headings, lists, etc.)
  - Position tracking within the original document

## How It Works
The Chunker Module:
1. Receives documents through gRPC
2. Extracts text from the specified source field
3. Divides the text into chunks based on configuration
4. Analyzes each chunk and adds metadata
5. Returns the document with added semantic results containing the chunks

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
  pipeline/chunker-module:latest

# Development mode (uses host networking)
    docker run -i --rm --network=host pipeline/chunker-module:dev
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

### Processing Options
The chunker accepts the following configuration options in the `custom_json_config`:

- `source_field`: The field in the document to chunk (required)
- `chunk_size`: Maximum size of each chunk in characters (default: 1000)
- `overlap_size`: Number of characters to overlap between chunks (default: 200)
- `preserve_urls`: Whether to preserve URLs during chunking (default: true)
- `chunk_config_id`: Identifier for the chunking configuration (default: "default")
- `result_set_name_template`: Template for naming the result set (default: "%s_%s")
- `log_prefix`: Prefix for log messages (default: "[Chunker] ")

## Integration
The Chunker Module integrates with the Rokkon pipeline through:

1. **gRPC Service**: Implements the `PipeStepProcessor` service defined in the Rokkon protobuf
2. **Registration**: Automatically registers with the Rokkon Engine on startup
3. **Health Checks**: Provides health status through the standard gRPC health check protocol

## Development
The module is built with Quarkus and uses:
- OpenNLP for text analysis
- Mutiny for reactive programming
- Micrometer and OpenTelemetry for observability

To contribute to the module, follow the standard Rokkon development workflow.