## Development Reminders
- Read through TESTING_STRATEGY.md after every compaction /compact command happens
- DO NOT BUILD DOCKER IMAGES WITH THE DOCKER COMMAND.  Use the gradle build or else it will not work right.
- Use the application.yml instead of application.properties

## BOM Structure and Usage
- **bom:base** - Contains only Quarkus BOM, parent for all other BOMs
- **bom:cli** - For CLI applications
  - NO `quarkus-grpc` to avoid server components
  - CLI apps use `compileOnly("io.quarkus:quarkus-grpc")` for code generation only
- **bom:library** - For libraries (has `quarkus-grpc` for code generation)
- **bom:module** - For modules/services 
  - Includes `quarkus-grpc`, REST, health, metrics, container image support
  - All modules automatically get Docker build capability
- **bom:server** - For servers like engine (full server stack)

## gRPC Code Generation
- **All projects generate their own protobuf code** - no pre-generated stubs
- Proto files live in `commons:protobuf` as a proto-only JAR
- Projects scan and generate code via Quarkus gRPC plugin
- Required configuration in application.yml:
```yaml
quarkus:
  generate-code:
    grpc:
      scan-for-proto: com.rokkon.pipeline:protobuf
      scan-for-imports: com.google.protobuf:protobuf-java,com.google.api.grpc:proto-google-common-protos
```

## Port Conventions
- HTTP ports: 3XXXX (e.g., 38080, 39092)
- gRPC ports: 4XXXX (e.g., 48080, 49092)
- The XXXX portion matches between HTTP and gRPC for the same service
- Examples:
  - Engine: HTTP 38082, gRPC 48082
  - Chunker: HTTP 39092, gRPC 49092
  - Parser: HTTP 39093, gRPC 49093
  - Embedder: HTTP 39094, gRPC 49094

## Module Deployment Architecture
- Modules run with Consul sidecars (service mesh pattern)
- Modules do NOT connect directly to Consul
- Registration flow:
  1. Module starts and binds to its ports
  2. register-module CLI attempts to register with Engine
  3. If registration fails, module continues running (resilient startup)
  4. Consul sidecar handles service mesh registration separately
- This allows testing modules in isolation without full infrastructure

# important-instruction-reminders
Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.