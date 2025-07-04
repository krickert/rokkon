# Module Unified Server Conversion Guide

This guide documents the steps taken to convert the echo module from separate gRPC and HTTP servers to a unified server configuration. Follow these steps for other modules.

## Port Convention

- Module ports: 391xx (e.g., echo: 39100, chunker: 39101, parser: 39102, etc.)
- Debug ports: 351xx (e.g., echo: 35100, chunker: 35101, parser: 35102, etc.)
- The '5' in debug ports signifies debug mode

## Conversion Steps

### 1. Update application.yml

#### Change gRPC server configuration:
```yaml
# OLD: Separate servers
grpc:
  server:
    port: 49090
    host: 0.0.0.0
    
# NEW: Unified server  
grpc:
  server:
    use-separate-server: false  # Key change - tells Quarkus to use unified server
    host: 0.0.0.0
```

#### Update gRPC client configuration:
```yaml
# OLD: Using separate gRPC port
clients:
  echoService:
    host: localhost
    port: 49090  # OLD gRPC port
    
# NEW: Using unified HTTP port
clients:
  echoService:
    host: localhost
    port: 39090  # Use HTTP port
```

### 2. Add OpenTelemetry Configuration

Add to application.yml:
```yaml
# OpenTelemetry configuration
otel:
  enabled: true
  traces:
    enabled: true
  metrics:
    enabled: true
  logs:
    enabled: true
  exporter:
    otlp:
      # In production, this will be set via environment variable to the sidecar collector
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}
      protocol: ${OTEL_EXPORTER_OTLP_PROTOCOL:grpc}
  resource:
    attributes:
      service.name: echo-module  # Change to your module name
      service.namespace: pipeline
      deployment.environment: ${quarkus.profile:prod}

# Micrometer metrics configuration
micrometer:
  enabled: true
  export:
    prometheus:
      enabled: true
  binder:
    jvm: true
    system: true
    grpc:
      server: true
      client: true
```

### 3. Disable OpenTelemetry for Tests

In the `%test` profile section:
```yaml
"%test":
  quarkus:
    # ... existing config ...
    # Disable OpenTelemetry in tests
    otel:
      enabled: false
      sdk:
        disabled: true
```

### 4. Update Module Name in Service Implementation

Check your service implementation (e.g., EchoServiceImpl.java) and ensure the module name matches the application name:
```java
// In getServiceRegistration method
.setModuleName("echo-module")  // Should match quarkus.application.name
```

### 5. Update Entrypoint Script

Update `src/main/bash/module-entrypoint.sh`:

#### Change default port:
```bash
MODULE_PORT=${MODULE_PORT:-39090}  # Update to your module's unified port
```

#### Update registration command:
```bash
# OLD:
local cli_cmd="pipeline register --module-host=${MODULE_HOST} --module-port=${MODULE_GRPC_PORT}..."

# NEW:
local cli_cmd="pipeline register --module-host=${MODULE_HOST} --module-port=${MODULE_PORT}..."
```

#### Update Java command:
```bash
# OLD: Setting both HTTP and gRPC ports
java ${JAVA_OPTS} ${JAVA_OPTS_APPEND} -Dquarkus.http.port=${MODULE_HTTP_PORT} -Dquarkus.grpc.server.port=${MODULE_GRPC_PORT} -jar /deployments/quarkus-run.jar &

# NEW: Only HTTP port needed with unified server
java ${JAVA_OPTS} ${JAVA_OPTS_APPEND} -Dquarkus.http.port=${MODULE_PORT} -jar /deployments/quarkus-run.jar &
```

### 6. Update Dockerfile

Update `src/main/docker/Dockerfile.jvm`:

#### Change EXPOSE directives:
```dockerfile
# OLD:
EXPOSE 8080
EXPOSE 9090

# NEW:
EXPOSE 39090  # Your module's unified port
```

#### Update comments to reflect new port:
```dockerfile
# docker run -i --rm -p 39090:39090 quarkus/echo-module-jvm
```

### 7. Test the Changes

Run tests to ensure everything works:
```bash
./gradlew :modules:echo:test
./gradlew :modules:echo:build
```

## Benefits of Unified Server

1. **Single Port**: Both REST and gRPC traffic use the same port (e.g., 39090)
2. **Simplified Configuration**: One less port to manage and configure
3. **Better Resource Usage**: Single server instance instead of two
4. **Easier Service Mesh Integration**: Single port simplifies sidecar proxy configuration
5. **Unified Observability**: All traffic goes through the same server, making monitoring easier

## Notes

- Modules don't need direct Consul registration updates as they rely on the sidecar pattern
- The unified server mode is recommended by Quarkus for production deployments
- OpenTelemetry will automatically send telemetry to the configured endpoint (defaults to localhost:4317)
- In production, the OTEL endpoint will be configured via environment variable to point to the sidecar collector

## Verification

After conversion, verify:
1. Tests pass
2. Module builds successfully
3. gRPC services are accessible on the HTTP port
4. REST endpoints continue to work
5. Metrics are exposed at `/q/metrics`
6. OpenAPI/Swagger UI is accessible at `/q/swagger-ui`