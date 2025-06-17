# Pipeline Transport Configuration Requirements

## Overview
This document captures the detailed requirements and design decisions for pipeline transport configuration, including Kafka, gRPC, and internal transport mechanisms.

## Kafka Transport Configuration

### Topic Naming Convention
- **Pattern**: `{pipeline-name}.{step-name}.input`
- **DLQ Pattern**: `{pipeline-name}.{step-name}.input.dlq`
- **Design Decision**: DLQ topics are ALWAYS derived from the main topic by appending `.dlq`
- **Rationale**: Following the Seinfeld "good luck with all that" pattern - if users want custom DLQ topics, they can add them to the whitelist and maintain them themselves

### Validation Rules
1. **No dots in custom topics** - dots are reserved as delimiters in our naming convention
2. **Topic name constraints**:
   - Must match pattern: `^[a-zA-Z0-9._-]+$`
   - Maximum length: 249 characters
   - Cannot be `.` or `..`
3. **Consumer group naming**: `{pipeline-name}.consumer-group`

### Partitioning Strategy
- **Default partition key**: `pipedocId` (NOT `streamId`)
- **Rationale**: Ensures CRUD operations are not clashing out-of-order
- **Compaction**: Kafka topics will have compaction enabled so only the most recent ID is retained
- **Custom partitioning**: Currently not supported to avoid confusing data scientists

### Producer Configuration

#### Explicitly Modeled Fields
1. **compressionType** 
   - Default: `snappy`
   - Preferred: `snappy` or `lz4`
   - Rationale: Kafka is limited to 8MB in AWS, compression gives us ~20MB effective size
   
2. **batchSize**
   - Default: 16384 (16KB)
   - Configurable per transport
   
3. **lingerMs**
   - Default: 10ms
   - Optimized for low latency
   
4. **acks**
   - App-controlled at engine level
   - Not configurable per transport

#### Producer Instance ID
- **Generation**: `{hostname}-{consul-lease-id}-{uuid}`
- **Tied to**: Consul lease name for debugging/monitoring
- **Dynamic**: Generated at runtime, not configured
- **Optional prefix**: Can be configured if needed without overcomplicating

### Per-Step Topic Strategy
- Each step gets its own topic for independent operations:
  - Rewind capability
  - Pause/resume
  - Purge operations
- Consumer groups are per-pipeline for load balancing

## gRPC Transport Configuration

### Service Discovery
- **Resolution**: 100% through Consul service registry
- **Host/Port**: Resolved dynamically via Consul service name
- **TLS Settings**: Configured at global/instance level, not per-transport
- **Configuration**: Plain config synced through Consul

### Configuration Model
```java
public record GrpcTransportConfig(
    String serviceName,              // Consul service name
    Map<String, String> grpcClientProperties  // timeout, retry policies, etc.
)
```

## Internal Transport

### Use Cases
- Pre/post mappings (automatically injected into every pipeline)
- Lightweight in-memory operations
- No transport configuration needed

### Pre/Post Mapping
- **Automatic injection**: Added to every pipeline step
- **Configuration**: Through `MappingConfig`
- **Type**: INTERNAL transport type
- **Protobuf Mapper**: Already implemented for performance

## Entity Operations

### Location in Model
- **Added to**: PipeStream in protobuf definition
- **Operations**: CREATE, UPDATE, DELETE
- **Not in**: Document proto or ProcessRequest
- **Rationale**: Operations are stream-level, not document-level

## Fan-in/Fan-out Patterns

### Fan-out (One-to-Many)
- Single step outputs to multiple targets
- Each output can have different transport types
- Example: Parser → (Chunker via Kafka, Metadata-extractor via gRPC)

### Fan-in (Many-to-One)
- Multiple steps write to same Kafka topic
- Share same consumer group for load balancing
- Validation prevents loops (both inter- and intra-pipeline)

### Loop Detection
- **When**: On every write to Consul
- **How**: Build directed graph and check for cycles
- **Includes**: Both inter-pipeline and intra-pipeline loops

## Schema Registry Integration

### Supported Registries
- **JSON Schema**: V7 validation (already implemented)
- **Future**: Interface for Glue/Apicurio
- **Per-module**: Schema is global and set to the module

### Schema Evolution
- Modules shouldn't change schemas often
- New schema = register as new module if needed
- Minimize boilerplate for schema management

### Configuration Topics vs Pipeline Topics
- **Pipeline topics**: Can be auto-created (configurable)
- **Custom topics**: Require whitelisting and manual creation
- **Advanced use cases**:
  - Dedicated topics for external systems (Kinesis, etc.)
  - Audit topics (not pipeline steps)
  - Cross-region data sync

## Kafka Slot Management (Future)

### Lease Management
- Producer instance IDs tied to Consul leases
- Prevents resource conflicts
- Enables clean handoff during scaling

### Consumer Coordination
- Consul-based partition claiming
- Prevent excessive Kafka consumers
- Dynamic scaling support

## Configuration Examples

### Standard Pipeline Step with Kafka
```json
{
  "stepName": "document-parser",
  "outputs": {
    "default": {
      "transportType": "KAFKA",
      "kafkaTransport": {
        "topic": "document-processing.parser.input",
        "partitionKeyField": "pipedocId",
        "compressionType": "snappy",
        "batchSize": 32768,
        "lingerMs": 20
      }
    }
  }
}
```

### Fan-out Configuration
```json
{
  "stepName": "parser",
  "outputs": {
    "content": {
      "transportType": "KAFKA",
      "kafkaTransport": {
        "topic": "pipeline.parser-to-chunker.input"
      }
    },
    "metadata": {
      "transportType": "GRPC",
      "grpcTransport": {
        "serviceName": "metadata-extractor-service"
      }
    }
  }
}
```

## Implementation Notes

1. **Immutability**: All configuration records are immutable
2. **Validation**: Multi-stage validation pipeline
3. **Defaults**: Smart defaults for all optional fields
4. **Future-proof**: Properties maps for extensibility

## Open Questions Resolved

1. **Q: Partition strategy flexibility?**
   - A: Keep it simple with pipedocId only, avoid confusion

2. **Q: Multiple schema versions?**
   - A: Register as new module if needed, keep it simple

3. **Q: Kafka message size limits?**
   - A: Use compression now, consider S3/Mongo for large payloads later

4. **Q: Topic auto-creation?**
   - A: Configurable option, whitelist for custom topics