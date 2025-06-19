# Chunker Module

## Overview
The Chunker module is a text segmentation service for the Rokkon Engine pipeline. It splits large documents into smaller, semantically meaningful chunks optimized for embedding and vector search operations.

## Architecture

### Service Implementation
- **gRPC Service**: Implements `PipeStepProcessor` interface
- **Port**: 49092
- **Main Class**: `ChunkerServiceImpl`

### Core Functionality
1. **Text Segmentation**
   - Splits documents into configurable chunk sizes
   - Maintains semantic boundaries (sentences, paragraphs)
   - Supports overlapping chunks for context preservation
   - Handles multiple chunking strategies

2. **Chunking Strategies**
   - **Fixed Size**: Chunks of consistent character/token count
   - **Sentence-based**: Respects sentence boundaries
   - **Paragraph-based**: Maintains paragraph integrity
   - **Semantic**: AI-driven chunking based on topic changes

3. **Metadata Preservation**
   - Each chunk maintains reference to parent document
   - Chunk position and ordering information
   - Overlap tracking for deduplication

## Container Deployment

### Docker Structure
```
chunker-container/
├── chunker-service (this module)
├── rokkon-cli (registration tool)
└── docker-entrypoint.sh
```

### Registration Flow
1. Container starts chunker service on port 49092
2. CLI tool connects to localhost:49092
3. Calls `GetServiceRegistration()` to get module info
4. Registers with Rokkon Engine's ModuleRegistrationService
5. Engine validates via `RegistrationCheck()` with test document
6. Engine registers service in Consul with gRPC health checks

### Example docker-entrypoint.sh
```bash
#!/bin/bash
# Start the chunker service
java -jar /app/chunker-service.jar &

# Wait for service to be ready
while ! grpc_health_probe -addr=localhost:49092 2>/dev/null; do
  echo "Waiting for chunker service..."
  sleep 1
done

# Register with engine
rokkon-cli register \
  --module-port=49092 \
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
    name: chunker
  grpc:
    server:
      port: 49092
      host: 0.0.0.0
      enable-reflection-service: true
      max-inbound-message-size: 1073741824  # 1GB
  
# Chunker-specific configuration
chunker:
  default-chunk-size: 1000
  default-overlap: 200
  strategy: "sentence"  # fixed, sentence, paragraph, semantic
  max-chunks-per-document: 1000
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
grpcurl -plaintext localhost:49092 list
grpcurl -plaintext localhost:49092 com.rokkon.PipeStepProcessor/GetServiceRegistration
```

## Integration with Pipeline

### Input Format
Receives `ProcessRequest` with:
- Parsed text in `Document.extractedText`
- Document metadata from parser
- Chunking configuration (optional)

### Output Format
Returns `ProcessResponse` with:
- Multiple `PipeDoc` objects (one per chunk)
- Each chunk contains:
  - Chunk text content
  - Parent document reference
  - Chunk position/index
  - Overlap information

### Example Usage
```java
// From parser output
Document parsedDoc = Document.newBuilder()
    .setId("doc-123")
    .setExtractedText("Long document text...")
    .build();

ProcessRequest request = ProcessRequest.newBuilder()
    .setDocument(parsedDoc)
    .setPipelineId("pipeline-1")
    .build();

// Chunker processes and returns multiple chunks
ProcessResponse response = chunkerService.processData(request).await();
// Response contains multiple PipeDoc entries
for (PipeDoc chunk : response.getPipedocsList()) {
    String chunkText = chunk.getTextContent();
    int chunkIndex = chunk.getChunkIndex();
    // Process each chunk...
}
```

## Chunking Strategies

### Fixed Size Chunking
- Splits text at fixed character/token intervals
- Fast and predictable
- May break mid-sentence

### Sentence-Based Chunking
- Respects sentence boundaries
- Better semantic coherence
- Variable chunk sizes

### Paragraph-Based Chunking
- Maintains paragraph structure
- Good for structured documents
- May create very large/small chunks

### Semantic Chunking
- Uses NLP to detect topic changes
- Best semantic coherence
- Higher computational cost

## Health Checks

The module exposes standard gRPC health check endpoints:
- Overall service health: `/grpc.health.v1.Health/Check`
- Watch health changes: `/grpc.health.v1.Health/Watch`

Consul monitors these endpoints every 10 seconds to maintain service availability.

## Troubleshooting

### Common Issues

1. **Memory Issues with Large Documents**
   - Implement streaming for very large texts
   - Increase heap allocation
   - Set maximum document size limits

2. **Poor Chunk Quality**
   - Adjust chunk size and overlap
   - Try different chunking strategies
   - Consider document-specific tuning

3. **Performance Bottlenecks**
   - Profile chunking algorithms
   - Implement caching for repeated documents
   - Consider parallel processing

### Debug Logging
```yaml
quarkus:
  log:
    category:
      "com.rokkon.chunker":
        level: DEBUG
```

## Dependencies
- Quarkus gRPC
- Apache Commons Text (for text processing)
- Rokkon Protobuf definitions
- Rokkon Commons utilities

## Performance Considerations

1. **Chunking Strategy Impact**
   - Fixed size: Fastest, lowest quality
   - Sentence-based: Moderate speed, good quality
   - Semantic: Slowest, best quality

2. **Memory Usage**
   - Scales with document size
   - Overlap increases memory usage
   - Consider streaming for large documents

3. **Optimization Tips**
   - Cache sentence detection results
   - Pre-compile regex patterns
   - Use efficient string builders
   - Implement parallel chunking for independent sections