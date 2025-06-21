# Rokkon Module Standardization Guidelines

## Module Requirements Checklist

### 1. Mutiny Stubs
- All modules must use Mutiny for reactive gRPC implementation
- Configure in `build.gradle.kts`:
  ```kotlin
  quarkus {
      buildForkOptions {
          systemProperty("quarkus.grpc.codegen.type", "mutiny")
      }
  }
  ```

### 2. Service Registration
- Implement `PipeStepProcessor` service with:
  - `processData()` - Main processing logic
  - `getServiceRegistration()` - Return module metadata
  - `registrationCheck()` - Validation endpoint
- Service must be `@GrpcService` and `@Singleton`

### 3. Protobuf Code Generation
- Extract proto files from `proto-definitions` jar at build time
- Use consistent pattern from echo module reference implementation
- No local proto file modifications

### 4. Standard Port Assignments
To avoid conflicts during testing, each module type has a designated port:

| Module Type | Default Port | Purpose |
|------------|--------------|---------|
| echo       | 9090        | Testing/validation |
| chunker    | 9091        | Document chunking |
| parser     | 9092        | Content parsing |
| embedder   | 9093        | Vector embeddings |
| opensearch-sink | 9094   | OpenSearch indexing |

### 5. Unit Testing
- Abstract test base class pattern
- Both `@QuarkusTest` and `@QuarkusIntegrationTest`
- Test coverage for all three RPC methods

### 6. Configuration
Each module must have proper `application.yml`:

```yaml
quarkus:
  application:
    name: [module-name]
  grpc:
    server:
      port: [assigned-port]
      host: 0.0.0.0
      enable-reflection-service: true
  container-image:
    build: false
    push: false
    registry: "registry.rokkon.com:8443"
    image: "rokkon/[module-name]:${quarkus.application.version}"

"%test":
  quarkus:
    grpc:
      server:
        port: 0  # Random port for tests
```

## Integration Test Configuration

For integration tests, modules should:
1. Accept `QUARKUS_GRPC_SERVER_PORT` environment variable
2. Support health checks at `/q/health/ready`
3. Implement proper gRPC health service
4. Return meaningful responses in `registrationCheck()`

## Container Requirements

Dockerfile should:
- Expose both HTTP (8080) and gRPC ports
- Support environment variable configuration
- Include health check instructions
- Run as non-root user

## Example Module Structure
```
modules/[module-name]/
├── build.gradle.kts
├── src/
│   ├── main/
│   │   ├── java/com/rokkon/[module]/
│   │   │   └── [Module]ServiceImpl.java
│   │   ├── proto/  (extracted at build time)
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/com/rokkon/[module]/
│           ├── [Module]ServiceTestBase.java
│           ├── [Module]ServiceTest.java
│           └── [Module]ServiceIT.java
└── README.md
```