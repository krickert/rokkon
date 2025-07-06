# Parser Module

## Overview
The Parser Module is a document processing service that extracts text and metadata from various document formats. It uses Apache Tika to parse documents and extract their content, making them available for further processing in the Rokkon pipeline.

## Features
- Extracts text content from various document formats
- Extracts metadata from documents
- Configurable parsing options
- Support for custom parser configurations
- Comprehensive error handling
- Automatic title extraction
- Metadata enrichment

## Supported Document Types
The Parser Module supports a wide range of document formats, including:
- PDF documents
- Microsoft Office documents (Word, Excel, PowerPoint)
- OpenDocument formats
- HTML and XML
- Plain text
- Images with OCR (when configured)
- And many more formats supported by Apache Tika

## How It Works
The Parser Module:
1. Receives documents through gRPC
2. Extracts text and metadata using Apache Tika
3. Processes and cleans up the extracted content
4. Adds metadata to the document
5. Returns the parsed document with extracted content and metadata

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
docker run -i --rm -p 49095:49095 
  -e ENGINE_HOST=engine 
  -e CONSUL_HOST=consul 
  pipeline/parser-module:latest

# Development mode (uses host networking)
docker run -i --rm --network=host rokkon/parser-module:dev
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

### Parser Configuration Options
The parser accepts the following configuration options in the `custom_json_config`:

- `maxContentLength`: Maximum content length to extract (default: -1, unlimited)
- `extractMetadata`: Whether to extract metadata (default: true)
- `enableTitleExtraction`: Whether to enable title extraction (default: true)
- `disableEmfParser`: Whether to disable the EMF parser (default: false)
- `enableGeoTopicParser`: Whether to enable the GeoTopic parser (default: false)
- `logParsingErrors`: Whether to log parsing errors (default: false)
- `fallbackToFilename`: Whether to fallback to filename for content type detection (default: true)

## Integration
The Parser Module integrates with the Rokkon pipeline through:

1. **gRPC Service**: Implements the `PipeStepProcessor` service defined in the Rokkon protobuf
2. **Registration**: Automatically registers with the Rokkon Engine on startup
3. **Health Checks**: Provides health status through the standard gRPC health check protocol

## Development
The module is built with Quarkus and uses:
- Apache Tika for document parsing
- Mutiny for reactive programming
- Micrometer and OpenTelemetry for observability

To contribute to the module, follow the standard Rokkon development workflow.