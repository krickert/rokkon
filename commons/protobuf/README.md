# Rokkon Protocol Buffers

## Overview
This project contains all Protocol Buffer definitions for the Rokkon Engine ecosystem. It serves as the single source of truth for all gRPC service definitions, message types, and data structures used across the platform.

## Purpose

This library exists to:
- Centralize all protobuf definitions in one location
- Ensure consistent data structures across all services
- Simplify protobuf version management
- Enable code generation for multiple languages

## Proto Files Structure

```
src/main/proto/
├── common/
│   ├── document.proto         # Core document types
│   ├── pipeline.proto         # Pipeline configuration messages
│   └── metadata.proto         # Common metadata structures
├── services/
│   ├── pipe_step_processor.proto    # Module processing service
│   ├── module_registration.proto    # Module registration service
│   └── engine_service.proto         # Engine orchestration service
└── streaming/
    ├── pipe_stream.proto      # Stream processing messages
    └── transport.proto        # Transport configuration
```

## Key Protobuf Definitions

### Core Services

1. **PipeStepProcessor** - The interface all modules implement:
   - `ProcessData()` - Main processing method
   - `GetServiceRegistration()` - Returns module metadata
   - `RegistrationCheck()` - Validation endpoint

2. **ModuleRegistrationService** - Engine's registration service:
   - `RegisterModule()` - Registers a module with the engine
   - `UnregisterModule()` - Removes a module
   - `GetRegisteredModules()` - Lists all modules

### Core Messages

1. **Document** - Raw document representation
2. **PipeDoc** - Processed document chunk with embeddings
3. **ProcessRequest/Response** - Module communication
4. **PipelineConfig** - Pipeline configuration
5. **ServiceRegistrationData** - Module registration info

## Usage

### For Quarkus Modules

Add dependency in `build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.rokkon.pipeline:rokkon-protobuf:1.0.0-SNAPSHOT")
}
```

Configure proto scanning in `application.yml`:
```yaml
quarkus:
  generate-code:
    grpc:
      scan-for-proto: "com.rokkon.pipeline:rokkon-protobuf"
```

### For Other Languages

The proto files can be used to generate code for:
- Python (for data science modules)
- Go (for high-performance modules)
- Node.js (for web integrations)
- Any language with protobuf support

## Building

```bash
# Build the project
./gradlew build

# Publish to Maven Local for development
./gradlew publishToMavenLocal

# Generate descriptor set (for other tools)
./gradlew generateProto
```

## Versioning

### Proto Compatibility Rules

1. **Never change field numbers** in existing messages
2. **Never change field types** once deployed
3. **Add new fields** with unique field numbers
4. **Mark deprecated fields** but don't remove them
5. **Use reserved** for deleted field numbers

### Example of Safe Evolution

```proto
message Document {
  string id = 1;
  bytes content = 2;
  string source = 3;
  
  // Safe to add new fields
  string content_type = 4;
  
  // Mark old fields deprecated
  // string old_field = 5 [deprecated = true];
  
  // Reserve deleted field numbers
  reserved 6, 7;
  reserved "deleted_field_name";
}
```

## Development Guidelines

### Adding New Protos

1. Place in appropriate directory (common/, services/, streaming/)
2. Use clear, descriptive message names
3. Include comprehensive comments
4. Follow naming conventions:
   - Services: PascalCase ending with "Service"
   - Messages: PascalCase
   - Fields: snake_case
   - Enums: SCREAMING_SNAKE_CASE

### Example Proto Structure

```proto
syntax = "proto3";

package com.rokkon;

import "google/protobuf/timestamp.proto";
import "common/metadata.proto";

// Service documentation
service MyService {
  // RPC documentation
  rpc MyMethod(MyRequest) returns (MyResponse);
}

// Message documentation
message MyRequest {
  // Field documentation
  string id = 1;
  repeated string tags = 2;
  map<string, string> metadata = 3;
}
```

## Testing Proto Changes

Before committing:
1. Build all dependent modules
2. Run integration tests
3. Verify backward compatibility
4. Update documentation if needed

## Common Issues

### Import Resolution
- Use full paths from proto root
- Don't use relative imports

### Java Package Conflicts
- Set unique java_package in each proto
- Use java_outer_classname when needed

### Large Messages
- Consider streaming for large data
- Use bytes for binary data, not string

## Related Documentation

- [Protocol Buffers Documentation](https://protobuf.dev/)
- [gRPC Documentation](https://grpc.io/)
- [Quarkus gRPC Guide](https://quarkus.io/guides/grpc-getting-started)