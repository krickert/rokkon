# Parser Module

## Overview
The Parser module is a document parsing service for the Rokkon Engine pipeline. It extracts structured content from raw documents using Apache Tika, converting various file formats into standardized text and metadata for downstream processing.

## Architecture

### Service Implementation
- **gRPC Service**: Implements `PipeStepProcessor` interface
- **Port**: 49091
- **Main Class**: `ParserServiceImpl`

### Core Functionality
1. **Document Parsing**
   - Extracts text content from various document formats (PDF, DOCX, TXT, HTML, etc.)
   - Preserves document metadata (author, creation date, etc.)
   - Handles encoding detection automatically
   - Supports over 1000 file formats via Apache Tika

2. **Metadata Extraction**
   - Document properties (title, author, keywords)
   - Creation and modification timestamps
   - Content type detection
   - Language detection

3. **Error Handling**
   - Graceful fallback for unsupported formats
   - Detailed error logging
   - Maintains pipeline flow even on parsing failures

## Container Deployment

### Docker Structure
```
parser-container/
├── parser-service (this module)
├── rokkon-cli (registration tool)
└── docker-entrypoint.sh
```

### Registration Flow
1. Container starts parser service on port 49091
2. CLI tool connects to localhost:49091
3. Calls `GetServiceRegistration()` to get module info
4. Registers with Rokkon Engine's ModuleRegistrationService
5. Engine validates via `RegistrationCheck()` with test document
6. Engine registers service in Consul with gRPC health checks

### Example docker-entrypoint.sh
```bash
#!/bin/bash
# Start the parser service
java -jar /app/parser-service.jar &

# Wait for service to be ready
while ! grpc_health_probe -addr=localhost:49091 2>/dev/null; do
  echo "Waiting for parser service..."
  sleep 1
done

# Register with engine
rokkon-cli register \
  --module-port=49091 \
  --engine-host=${ENGINE_HOST} \
  --engine-port=${ENGINE_PORT}

# Keep container running
wait
```

## Configuration

### application.yml
```yaml
quarkus:
  application:
    name: parser
  grpc:
    server:
      port: 49091
      host: 0.0.0.0
      enable-reflection-service: true
      max-inbound-message-size: 1073741824  # 1GB
  tika:
    # Tika configuration
    parse-timeout: 300000  # 5 minutes
    detect-timeout: 60000  # 1 minute
```

## Development

### Building
```bash
# Standard build
./gradlew build

# Build with tests
./gradlew build test

# Build Docker image
./gradlew build -Dquarkus.container-image.build=true
```

### Testing
```bash
# Run unit tests
./gradlew test

# Run integration tests
./gradlew quarkusIntTest

# Run in dev mode with live reload
./gradlew quarkusDev
```

### Local Development
```bash
# Start in dev mode
./gradlew quarkusDev

# Test with grpcurl
grpcurl -plaintext localhost:49091 list
grpcurl -plaintext localhost:49091 com.rokkon.PipeStepProcessor/GetServiceRegistration
```

## Integration with Pipeline

### Input Format
Receives `ProcessRequest` with:
- Raw document bytes in `Document.content`
- Document metadata (source, timestamp, etc.)
- Pipeline context

### Output Format
Returns `ProcessResponse` with:
- Parsed text in `Document.extractedText`
- Updated metadata including detected properties
- Processing logs for debugging

### Example Usage
```java
// From previous pipeline step
Document rawDoc = Document.newBuilder()
    .setId("doc-123")
    .setContent(ByteString.copyFrom(pdfBytes))
    .setSource("uploads/report.pdf")
    .build();

ProcessRequest request = ProcessRequest.newBuilder()
    .setDocument(rawDoc)
    .setPipelineId("pipeline-1")
    .build();

// Parser processes and returns
ProcessResponse response = parserService.processData(request).await();
Document parsedDoc = response.getOutputDoc();
String extractedText = parsedDoc.getExtractedText();
```

## Health Checks

The module exposes standard gRPC health check endpoints:
- Overall service health: `/grpc.health.v1.Health/Check`
- Watch health changes: `/grpc.health.v1.Health/Watch`

Consul monitors these endpoints every 10 seconds to maintain service availability.

## Troubleshooting

### Common Issues

1. **Out of Memory on Large Documents**
   - Increase heap size: `-Xmx2g`
   - Configure Tika streaming for large files
   - Implement document size limits

2. **Parsing Timeouts**
   - Adjust `tika.parse-timeout` in configuration
   - Check for corrupted input files
   - Monitor CPU usage during parsing

3. **Unsupported Format**
   - Check Tika logs for format detection
   - Verify file isn't corrupted
   - Consider custom parser implementation

### Debug Logging
```yaml
quarkus:
  log:
    category:
      "com.rokkon.parser":
        level: DEBUG
      "org.apache.tika":
        level: DEBUG
```

## Dependencies
- Quarkus gRPC
- Apache Tika
- Rokkon Protobuf definitions
- Rokkon Commons utilities

## Performance Considerations

1. **Memory Usage**
   - Tika can be memory intensive for large documents
   - Configure appropriate heap sizes
   - Use streaming for documents over 100MB

2. **CPU Usage**
   - OCR operations are CPU intensive
   - Complex formats (PDF) require more processing
   - Consider horizontal scaling for high throughput

3. **Optimization Tips**
   - Pre-filter by content type if possible
   - Implement caching for repeated documents
   - Use Tika's SAX parser for better streaming