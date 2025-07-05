# Module Unified Server Conversion Guide

This guide documents the steps taken to convert the echo module from separate gRPC and HTTP servers to a unified server configuration. Follow these steps for other modules.

## Dev Mode vs Production

This guide covers both development and production configurations. Key differences:

**Development Mode** (`quarkusDev`):
- Modules deployed as Docker containers locally
- Dynamic port allocation (39100-39800 range)
- Auto-registration enabled by default
- Docker-based operations available at `/api/v1/dev/modules`
- Cleanup and orphan management features
- Direct module deployment from UI

**Production Mode**:
- Modules deployed via orchestration (Kubernetes, etc.)
- Ports configured by deployment manifests
- Registration handled by sidecars
- No Docker operations available
- Registry-only operations at `/api/v1/module-management`

## Recent Updates (Session Summary)

### API Separation for Dev/Prod Environments
- Created separate `ModuleDevManagementResource` for all Docker-based operations **(DEV MODE ONLY)**
- Dev-specific endpoints at `/api/v1/dev/modules` path **(DEV MODE ONLY)**:
  - Module deployment/undeployment
  - Container management (stop, scale-up, logs)
  - Orphaned module operations
  - Docker-specific cleanup
- `ModuleManagementResource` at `/api/v1/module-management` **(AVAILABLE IN BOTH DEV AND PROD)**:
  - Module registry queries (list registered modules)
  - Module health checks
  - Enable/disable operations
  - Deregistration (from registry only, no container operations)
- Frontend automatically detects dev mode and uses appropriate API paths

### Orphaned Module Management **(DEV MODE ONLY)**
- Implemented orphaned module detection and redeployment functionality
- Added "Re-deploy" button in UI for orphaned modules (containers running but not registered)
- Created comprehensive cleanup method `cleanupModuleCompletely()` that removes containers and Consul registrations
- Added REST endpoint: `DELETE /api/v1/dev/modules/{moduleName}/cleanup`

### Module Naming Clarification
- Module names should NOT have "-module" appended/removed as a convention
- "test-module" is the full module name (not "test" with "-module" added)
- Container naming follows: `{moduleName}-module-app` (e.g., "test-module-module-app")
- No naming restrictions for production modules - only dev mode has conventions

### Key Fixes
- Fixed PipelineModule enum: TEST module name changed from "test" to "test-module"
- Updated UI to use "Re-deploy" instead of "Re-register" for orphaned modules
- Redeploy functionality now properly cleans up orphaned containers before deploying fresh
- Removed port values from PipelineModule enum to eliminate confusion
- Separated dev-specific API endpoints to `/api/v1/dev/modules` path
- ModuleManagementResource now only contains production-safe operations

## Important: Understanding Module Ports

### CRITICAL: All Modules Use Port 39100 Internally
**DO NOT** use any port numbers from PipelineModule.java for internal configuration!

**Important Update**: The PipelineModule enum no longer contains port values - they have been removed to prevent confusion. All modules should use the **same internal port: 39100**.

### Internal Container Ports vs External Host Ports
- **Internal Container Port**: The port the application listens on INSIDE the container (configured in application.yml and Dockerfile)
  - **ALWAYS USE 39100** for all modules
- **External Host Port**: The port exposed on the Docker host, dynamically allocated by the deployment service

### Port Convention

**Internal Container Port (INSIDE container):**
- All modules use the same internal port: **39100**
- This is what you configure in:
  - `application.yml`: `quarkus.http.port: 39100`
  - `module-entrypoint.sh`: `MODULE_PORT=${MODULE_PORT:-39100}`
  - `Dockerfile.jvm`: `EXPOSE 39100`
- Since each module runs in its own container, there's no conflict

**External Host Ports (OUTSIDE container):**
- **DEV MODE**: ModuleDeploymentService dynamically allocates ports in the range 39100-39800
  - First available port is allocated (starting from 39100 if free)
  - Each instance of any module gets the next available port
  - This range allows for up to 700 module instances
  - Example allocation:
    - echo-module-1: 39100 (maps to internal 39100)
    - parser-module-1: 39101 (maps to internal 39100)
    - chunker-module-1: 39102 (maps to internal 39100)
    - echo-module-2: 39103 (maps to internal 39100)
- **PRODUCTION**: External ports are configured by your orchestration platform (Kubernetes, etc.)

### Example Configuration
✅ **CORRECT** (all modules use 39100 internally):
```yaml
# parser/application.yml
quarkus:
  http:
    port: 39100  # Standard internal module port

# test-module/application.yml  
quarkus:
  http:
    port: 39100  # Standard internal module port

# chunker/application.yml
quarkus:
  http:
    port: 39100  # Standard internal module port
```

❌ **INCORRECT** (using different internal ports):
```yaml
# chunker/application.yml
quarkus:
  http:
    port: 39103  # WRONG! All modules must use 39100 internally
```

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

**Note**: Most modules already have the correct `getServiceRegistration` method implemented. For example, the chunker module already had this method properly implemented, so no changes were needed. Always check first before adding a duplicate method!

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
# Auto-registration behavior differs between dev and production
# DEV MODE: AUTO_REGISTER defaults to true for automatic registration
# PRODUCTION: Set AUTO_REGISTER=false when using sidecars for registration

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

**DEV MODE**: Images are built and deployed locally  
**PRODUCTION**: Images should be pushed to your container registry and deployed via your orchestration platform

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

## Chunker Module Specific Notes

### Special Considerations for Chunker Module
1. **Memory Requirements**: The chunker module requires 4GB of memory due to NLP processing
2. **Port Assignment**: Internal port should be 39100 (like all modules), external port will be dynamically allocated
3. **Module Name**: The module name in PipelineModule.java is "chunker" (not "chunker-module")
4. **NLP Dependencies**: May need larger message sizes if processing large documents with NLP models
5. **Docker Image**: Should be named `pipeline/chunker:latest` following the new convention

### Files to Update for Chunker
1. `/modules/chunker/src/main/resources/application.yml` - Main configuration changes
2. `/modules/chunker/src/main/bash/module-entrypoint.sh` - Startup script updates
3. `/modules/chunker/src/main/docker/Dockerfile.jvm` - Port exposure changes
4. `/modules/chunker/src/main/java/com/rokkon/chunker/ChunkerServiceImpl.java` - Check module name in getServiceRegistration()

### Common Issues to Avoid
1. **Module Names**: Ensure consistency between:
   - PipelineModule enum entry ("chunker")
   - Service registration in ChunkerServiceImpl
   - application.yml (`quarkus.application.name`)
   - Docker container labels
2. **Port Confusion**: Remember internal port (39100) vs external port (dynamically allocated)
3. **Image Names**: Change from `rokkon/chunker` to `pipeline/chunker`
4. **Build Process**: Always use `./gradlew :modules:chunker:imageBuild` instead of direct docker build

## Verification

After conversion, verify:
1. Tests pass: `./gradlew :modules:chunker:test`
2. Module builds successfully: `./gradlew :modules:chunker:build`
3. Docker image created: `./gradlew :modules:chunker:imageBuild`
4. gRPC services are accessible on the HTTP port
5. REST endpoints continue to work
6. Metrics are exposed at `/q/metrics`
7. OpenAPI/Swagger UI is accessible at `/q/swagger-ui`
8. Module can be deployed and registered in dev mode

## Troubleshooting

### Module Shows as Orphaned After Deployment **(DEV MODE)**
If a module shows as orphaned (container running but not registered):
1. Check the registrar sidecar logs: `docker logs [module-name]-registrar`
2. Verify the module is healthy: `docker logs [module-name]-module-app`
3. Ensure `AUTO_REGISTER=true` is set (default in dev mode)
4. Check that the module name in PipelineModule.java matches the service registration

### Registration Fails
Common causes:
1. Module name mismatch between PipelineModule enum and service implementation (DEV MODE)
2. Health check endpoint not accessible
3. Engine not reachable from the module container
4. Port configuration issues (ensure internal port is 39100)
5. In production: Check sidecar configuration and network policies

### Port Already in Use **(DEV MODE)**
If deployment fails with "port already in use":
1. Check for orphaned containers: `docker ps -a | grep module`
2. Use the cleanup endpoint: `DELETE /api/v1/dev/modules/{moduleName}/cleanup`
3. The deployment service will automatically find the next available port

### Module Not Appearing in UI
**DEV MODE**:
1. Ensure the module is added to the PipelineModule enum
2. Restart the engine in dev mode to pick up the new module
3. Check browser console for any API errors

**PRODUCTION**:
1. Verify module is registered in the registry
2. Check module health status
3. Verify network connectivity between UI and registry