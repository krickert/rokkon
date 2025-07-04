# Module Unified Server Conversion Guide

This guide documents the steps taken to convert the echo module from separate gRPC and HTTP servers to a unified server configuration. Follow these steps for other modules.

## Important: Understanding Module Ports

### Internal Container Ports vs External Host Ports
- **Internal Container Port**: The port the application listens on INSIDE the container (configured in application.yml and Dockerfile)
- **External Host Port**: The port exposed on the Docker host, dynamically allocated by the deployment service

### Port Convention

**Internal Container Port (INSIDE container):**
- All modules can use the same internal port: **39100**
- This is what you configure in application.yml, Dockerfile, and entrypoint scripts
- Since each module runs in its own container, there's no conflict

**External Host Ports (OUTSIDE container - dynamically allocated):**
- First instance: 39100 (maps to internal 39100)
- Second instance: 39101 (maps to internal 39100)
- Third instance: 39102 (maps to internal 39100)
- etc.

**Example Docker mapping:**
```
echo-module-1:    0.0.0.0:39100->39100/tcp  (external 39100 -> internal 39100)
echo-module-2:    0.0.0.0:39101->39100/tcp  (external 39101 -> internal 39100)
parser-module-1:  0.0.0.0:39102->39100/tcp  (external 39102 -> internal 39100)
```

**Debug Ports:**
- Internal debug port: 35100 (same for all modules)
- The '5' in debug ports signifies debug mode
- External debug ports are also dynamically allocated when needed

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

#### Configure gRPC message size limits (if needed):
```yaml
# For modules that handle large payloads (e.g., parser module)
grpc:
  server:
    use-separate-server: false
    host: 0.0.0.0
    max-inbound-message-size: 1073741824  # 1GB for large documents
    max-outbound-message-size: 1073741824  # 1GB for parsed content
```

#### Update container image naming:
```yaml
container-image:
  # OLD:
  image: "rokkon/parser-module:${quarkus.application.version}"
  group: rokkon
  
  # NEW:
  image: "pipeline/parser-module:${quarkus.application.version}"
  group: pipeline
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

#### Add new environment variables:
```bash
MODULE_PORT=${MODULE_PORT:-39100}  # Use standard internal port 39100
# With unified server, HTTP and gRPC use the same port
MODULE_HTTP_PORT=${MODULE_HTTP_PORT:-${MODULE_PORT}}
MODULE_GRPC_PORT=${MODULE_GRPC_PORT:-${MODULE_PORT}}  # Same as HTTP port
MAX_RETRIES=${MAX_RETRIES:-3}  # Reduced from 30 for faster failure detection
SHUTDOWN_ON_REGISTRATION_FAILURE=${SHUTDOWN_ON_REGISTRATION_FAILURE:-true}
AUTO_REGISTER=${AUTO_REGISTER:-true}  # Default to true for dev, set to false when using sidecars
```

#### Update registration command with better support:
```bash
# Build CLI command with all options (unified server uses same port for both)
local cli_cmd="pipeline register --module-host=${MODULE_HOST} --module-port=${MODULE_PORT} --engine-host=${ENGINE_HOST} --engine-port=${ENGINE_PORT}"

# Use registration host/port if provided (for when module is behind NAT/Docker)
if [ -n "$REGISTRATION_HOST" ]; then
  cli_cmd="$cli_cmd --registration-host=${REGISTRATION_HOST}"
fi

if [ -n "$REGISTRATION_PORT" ]; then
  cli_cmd="$cli_cmd --registration-port=${REGISTRATION_PORT}"
fi

# Log the command for debugging
echo "Executing: $cli_cmd"
```

#### Add auto-registration control:
```bash
# Check if auto-registration is enabled
if [ "$AUTO_REGISTER" = "false" ]; then
  echo "Auto-registration disabled (AUTO_REGISTER=false). Module running without registration."
  # Keep the module running in foreground
  wait $MODULE_PID
else
  # Register the module (CLI will handle health checks)
  if register_module; then
    echo "Module registered successfully!"
    wait $MODULE_PID
  else
    if [ "$SHUTDOWN_ON_REGISTRATION_FAILURE" = "true" ]; then
      echo "Registration failed. Shutting down module as SHUTDOWN_ON_REGISTRATION_FAILURE=true"
      kill $MODULE_PID
      exit 1
    else
      echo "Registration failed, but keeping module running as SHUTDOWN_ON_REGISTRATION_FAILURE=false"
      echo "Module is available for manual registration or debugging"
      wait $MODULE_PID
    fi
  fi
fi
```

#### Update Java command:
```bash
# Only HTTP port needed with unified server
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
EXPOSE 39100  # Standard internal module port
```

#### Update comments to reflect new port and naming:
```dockerfile
# docker run -i --rm -p 39100:39100 pipeline/parser-module-jvm
# Note: In deployment, external port will be dynamically allocated
```

#### Update image naming convention:
```dockerfile
# Build command example changed from:
# docker build -f src/main/docker/Dockerfile.jvm -t quarkus/parser-module-jvm .
# To:
# docker build -f src/main/docker/Dockerfile.jvm -t pipeline/parser-module-jvm .
```

### 7. Test the Changes

Run tests to ensure everything works:
```bash
./gradlew :modules:echo:test
./gradlew :modules:echo:build
```

### 8. Update Entrypoint Script (Important!)

Ensure your `module-entrypoint.sh` script:
- Does NOT use `--consul-host` or `--consul-port` CLI parameters (these cause "Unknown options" errors)
- Uses environment variables for configuration instead
- Includes proper registration control:

```bash
# Configuration with defaults
AUTO_REGISTER=${AUTO_REGISTER:-true}  # Default to true for dev, set to false when using sidecars
SHUTDOWN_ON_REGISTRATION_FAILURE=${SHUTDOWN_ON_REGISTRATION_FAILURE:-true}
```

### 9. Configure gRPC for Large Payloads (if needed)

If your module handles large messages (like the parser), add to `application.yml`:

```yaml
quarkus:
  grpc:
    server:
      max-inbound-message-size: 1073741824  # 1GB
      max-inbound-metadata-size: 1073741824  # 1GB
      max-outbound-message-size: 1073741824  # 1GB (if module returns large data)
    clients:
      registrationClient:
        max-inbound-message-size: 1073741824  # 1GB
      # Add similar config for your module's specific clients
```

### 10. Build Docker Image

Build the Docker image using Gradle (DO NOT use docker build directly):
```bash
# Build the module and create Docker image
./gradlew :modules:echo:imageBuild

# This will:
# 1. Build the module if needed
# 2. Create a Docker image tagged as configured in application.yml
# For example: pipeline/echo:latest, pipeline/parser-module:latest

# To verify the image was created:
docker images | grep [module-name]
```

**Note**: The `imageBuild` task uses the Quarkus container-image extension. The image name comes from the `quarkus.container-image.image` property in application.yml.

## Benefits of Unified Server

1. **Single Port**: Both REST and gRPC traffic use the same port (39100 internally)
2. **Simplified Configuration**: One less port to manage and configure
3. **Better Resource Usage**: Single server instance instead of two
4. **Easier Service Mesh Integration**: Single port simplifies sidecar proxy configuration
5. **Unified Observability**: All traffic goes through the same server, making monitoring easier
6. **Consistent Internal Port**: All modules use 39100 internally, simplifying configuration

## Key Improvements from Parser Module Conversion

The parser module conversion revealed several important improvements that should be applied to all modules:

### 1. Enhanced Registration Control
- **AUTO_REGISTER**: Set to `false` when using sidecars to prevent duplicate registration
- **SHUTDOWN_ON_REGISTRATION_FAILURE**: Control whether the module should exit on registration failure (useful for debugging)
- **REGISTRATION_HOST/PORT**: Support for modules behind NAT/Docker networking

### 2. Better Error Handling
- Reduced retry attempts from 30 to 3 for faster failure detection
- Module can continue running even if registration fails (controlled by SHUTDOWN_ON_REGISTRATION_FAILURE)
- Added command logging for better debugging visibility

### 3. Large Payload Support
- Configure max message sizes for modules handling large data:
  ```yaml
  max-inbound-message-size: 1073741824  # 1GB
  max-outbound-message-size: 1073741824  # 1GB
  ```

### 4. Consistent Naming Convention
- Change from `rokkon/` to `pipeline/` prefix in container images
- Update group from `rokkon` to `pipeline`

## Notes

- Modules don't need direct Consul registration updates as they rely on the sidecar pattern
- The unified server mode is recommended by Quarkus for production deployments
- OpenTelemetry will automatically send telemetry to the configured endpoint (defaults to localhost:4317)
- In production, the OTEL endpoint will be configured via environment variable to point to the sidecar collector
- Use `AUTO_REGISTER=false` when deploying with sidecars to prevent registration conflicts

## Verification

After conversion, verify:
1. Tests pass
2. Module builds successfully
3. gRPC services are accessible on the HTTP port
4. REST endpoints continue to work
5. Metrics are exposed at `/q/metrics`
6. OpenAPI/Swagger UI is accessible at `/q/swagger-ui`