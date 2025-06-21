# Embedder Module

## Overview
The Embedder module is a vector embedding service for the Rokkon Engine pipeline. It converts text chunks into high-dimensional vector representations using state-of-the-art embedding models, enabling semantic search and similarity matching.

## Architecture

### Service Implementation
- **gRPC Service**: Implements `PipeStepProcessor` interface
- **Port**: 49093
- **Main Class**: `EmbedderServiceImpl`

### Core Functionality
1. **Text Embedding**
   - Converts text chunks to dense vector representations
   - Supports multiple embedding models (OpenAI, Sentence-Transformers, etc.)
   - Handles batch processing for efficiency
   - Configurable embedding dimensions

2. **Model Support**
   - **OpenAI Embeddings**: Ada, Curie, Davinci models
   - **Sentence-Transformers**: All-MiniLM, All-MPNet variants
   - **Custom Models**: Support for ONNX/TorchScript models
   - **Model caching**: Reduces latency for repeated embeddings

3. **Vector Operations**
   - Normalization for cosine similarity
   - Dimension reduction options
   - Vector quantization for storage optimization

## Container Deployment

### Docker Structure
```
embedder-container/
├── embedder-service (this module)
├── rokkon-cli (registration tool)
└── docker-entrypoint.sh
```

### Registration Flow
1. Container starts embedder service on port 49093
2. CLI tool connects to localhost:49093
3. Calls `GetServiceRegistration()` to get module info
4. Registers with Rokkon Engine's ModuleRegistrationService
5. Engine validates via `RegistrationCheck()` with test text
6. Engine registers service in Consul with gRPC health checks

### Example docker-entrypoint.sh
```bash
#!/bin/bash
# Start the embedder service
java -jar /app/embedder-service.jar &

# Wait for service to be ready
while ! grpc_health_probe -addr=localhost:49093 2>/dev/null; do
  echo "Waiting for embedder service..."
  sleep 1
done

# Register with engine
rokkon-cli register \
  --module-port=49093 \
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
    name: embedder
  grpc:
    server:
      port: 49093
      host: 0.0.0.0
      enable-reflection-service: true
      max-inbound-message-size: 1073741824  # 1GB

# Embedder-specific configuration
embedder:
  model:
    provider: "sentence-transformers"  # openai, sentence-transformers, custom
    name: "all-MiniLM-L6-v2"
    dimensions: 384
    batch-size: 32
    cache-enabled: true
    cache-size: 10000
  
  # OpenAI specific (if using OpenAI provider)
  openai:
    api-key: ${OPENAI_API_KEY}
    model: "text-embedding-ada-002"
    
  # Local model settings
  local:
    model-path: "/models/embedder"
    device: "cpu"  # cpu, cuda, mps
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
grpcurl -plaintext localhost:49093 list
grpcurl -plaintext localhost:49093 com.rokkon.PipeStepProcessor/GetServiceRegistration
```

## Integration with Pipeline

### Input Format
Receives `ProcessRequest` with:
- Text chunks in `PipeDoc.textContent`
- Chunk metadata (position, parent document)
- Embedding configuration (optional)

### Output Format
Returns `ProcessResponse` with:
- Updated `PipeDoc` objects with embeddings
- Each chunk includes:
  - Original text content
  - Vector embedding in `embedding` field
  - Embedding metadata (model, dimensions)

### Example Usage
```java
// From chunker output
PipeDoc chunk = PipeDoc.newBuilder()
    .setPipedocId("chunk-123-001")
    .setTextContent("This is a text chunk to embed...")
    .setChunkIndex(0)
    .build();

ProcessRequest request = ProcessRequest.newBuilder()
    .addPipedocs(chunk)
    .setPipelineId("pipeline-1")
    .build();

// Embedder processes and returns embeddings
ProcessResponse response = embedderService.processData(request).await();
PipeDoc embeddedChunk = response.getPipedocs(0);
// Embedding is now available in embeddedChunk.getEmbedding()
```

## Embedding Models

### Sentence-Transformers (Default)
- **all-MiniLM-L6-v2**: 384 dimensions, fast, good quality
- **all-MiniLM-L12-v2**: 384 dimensions, better quality
- **all-MPNet-base-v2**: 768 dimensions, best quality

### OpenAI Embeddings
- **text-embedding-ada-002**: 1536 dimensions, excellent quality
- **text-embedding-3-small**: 512 dimensions, cost-effective
- **text-embedding-3-large**: 3072 dimensions, highest quality

### Custom Models
- Support for ONNX exported models
- TorchScript models
- Custom tokenizers and preprocessing

## Health Checks

The module exposes standard gRPC health check endpoints:
- Overall service health: `/grpc.health.v1.Health/Check`
- Watch health changes: `/grpc.health.v1.Health/Watch`

Consul monitors these endpoints every 10 seconds to maintain service availability.

## Troubleshooting

### Common Issues

1. **Model Loading Failures**
   - Verify model path and permissions
   - Check available memory for model loading
   - Ensure model format compatibility

2. **Out of Memory**
   - Reduce batch size
   - Use smaller embedding models
   - Enable model quantization

3. **Slow Performance**
   - Enable GPU acceleration if available
   - Increase batch size for throughput
   - Use caching for repeated texts

### Debug Logging
```yaml
quarkus:
  log:
    category:
      "com.rokkon.embedder":
        level: DEBUG
      "ai.djl":  # If using DJL
        level: DEBUG
```

## Dependencies
- Quarkus gRPC
- Deep Java Library (DJL) or Sentence-Transformers Java
- Rokkon Protobuf definitions
- Rokkon Commons utilities
- Optional: CUDA runtime for GPU acceleration

## Performance Considerations

1. **Model Selection**
   - Smaller models: Lower latency, less memory
   - Larger models: Better quality, higher resource usage
   - Consider quality vs. performance tradeoffs

2. **Batching Strategy**
   - Larger batches: Better GPU utilization
   - Smaller batches: Lower latency
   - Dynamic batching for optimal throughput

3. **Caching**
   - Cache embeddings for repeated texts
   - Use content-based cache keys
   - Monitor cache hit rates

4. **Resource Optimization**
   - GPU acceleration dramatically improves throughput
   - Model quantization reduces memory usage
   - Consider model distillation for production