# Module Creation Guide for Pipeline System

This guide provides comprehensive instructions for creating new Java-based modules in the Pipeline system. All modules use a unified server configuration where HTTP and gRPC share the same port.

## Table of Contents
1. [Module Architecture Overview](#module-architecture-overview)
2. [Module Structure](#module-structure)
3. [Step-by-Step Module Creation](#step-by-step-module-creation)
4. [Development vs Production](#development-vs-production)
5. [Testing Guidelines](#testing-guidelines)
6. [Best Practices](#best-practices)
7. [Troubleshooting](#troubleshooting)

## Module Architecture Overview

### Core Principles
- **Unified Server**: All modules use a single port (39100) for both HTTP and gRPC traffic
- **Container-First**: Modules are designed to run in Docker containers
- **Service Discovery**: Modules register themselves with Consul for discovery
- **Observability**: Built-in support for OpenTelemetry and Prometheus metrics
- **Fan-in/Fan-out**: Modules can have multiple inputs and outputs in pipeline configurations

### Port Configuration
- **Internal Port**: All modules use port 39100 inside their containers
- **External Port**: 
  - Dev mode: Dynamically allocated (39100-39800 range)
  - Production: Configured by orchestration platform

## Module Structure

```
modules/
└── your-module/
    ├── build.gradle.kts
    ├── src/
    │   ├── main/
    │   │   ├── java/
    │   │   │   └── com/rokkon/yourmodule/
    │   │   │       ├── YourModuleApplication.java
    │   │   │       ├── YourModuleServiceImpl.java
    │   │   │       ├── config/
    │   │   │       ├── health/
    │   │   │       └── model/
    │   │   ├── resources/
    │   │   │   ├── application.yml
    │   │   │   └── META-INF/
    │   │   ├── bash/
    │   │   │   └── module-entrypoint.sh
    │   │   └── docker/
    │   │       ├── Dockerfile.jvm
    │   │       └── Dockerfile.dev
    │   └── test/
    │       └── java/
    └── README.md
```

## Step-by-Step Module Creation

### 1. Create Module Directory Structure

```bash
mkdir -p modules/your-module/src/main/{java,resources,bash,docker}
mkdir -p modules/your-module/src/test/java
```

### 2. Create build.gradle.kts

```kotlin
plugins {
    java
    id("io.quarkus")
}

dependencies {
    // Core dependencies
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation(libs.quarkus.grpc)
    implementation(libs.quarkus.rest)
    implementation(libs.quarkus.rest.jackson)
    implementation(libs.quarkus.opentelemetry)
    implementation(libs.quarkus.micrometer)
    implementation(libs.quarkus.micrometer.registry.prometheus)
    implementation(libs.quarkus.container.image.docker)
    
    // Pipeline core dependencies
    implementation(project(":proto"))
    implementation(project(":lib:shared-lib"))
    implementation(project(":lib:registration-lib"))
    implementation(project(":lib:module-types"))
    
    // Module-specific dependencies
    // Add your specific dependencies here
    
    // Test dependencies
    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.quarkus.junit5.mockito)
    testImplementation(libs.rest.assured)
    testImplementation(libs.assertj.core)
}

// Configure Quarkus
quarkus {
    // Ensure proper resource handling
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
```

### 3. Create application.yml

```yaml
# Application Configuration
quarkus:
  application:
    name: your-module
    version: ${quarkus.application.version:latest}
  
  # HTTP Configuration (unified server)
  http:
    port: 39100  # Standard internal module port
    host: 0.0.0.0
    cors:
      ~: true
      origins: /.*/
  
  # gRPC Configuration (unified server)
  grpc:
    server:
      use-separate-server: false  # CRITICAL: Use unified server
      host: 0.0.0.0
      # Configure message sizes if handling large payloads
      max-inbound-message-size: 10485760  # 10MB default
      max-outbound-message-size: 10485760  # 10MB default
    
    # Client configuration for engine registration
    clients:
      registrationClient:
        host: ${ENGINE_HOST:localhost}
        port: ${ENGINE_PORT:39001}
        max-inbound-message-size: 10485760
  
  # Container Image Configuration
  container-image:
    build: true
    push: false
    image: "pipeline/your-module:${quarkus.application.version}"
    group: pipeline
    registry: "docker.io"
    # Add labels for container management
    labels:
      "pipeline.module": "true"
      "pipeline.module.name": "your-module"
      "pipeline.module.type": "processor"  # or generator/aggregator/etc
  
  # Console and logging
  console:
    color: true
  log:
    level: INFO
    category:
      "com.rokkon":
        level: DEBUG
      "io.grpc":
        level: WARN

# Module Configuration
module:
  name: ${quarkus.application.name}
  version: ${quarkus.application.version}
  # Add module-specific configuration here
  config:
    # Example configurations
    buffer-size: 1024
    timeout-seconds: 30

# OpenTelemetry Configuration
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
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}
      protocol: ${OTEL_EXPORTER_OTLP_PROTOCOL:grpc}
  resource:
    attributes:
      service.name: your-module
      service.namespace: pipeline
      deployment.environment: ${quarkus.profile:prod}

# Micrometer Metrics
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

# Health Check Configuration
health:
  enabled: true
  live-path: /q/health/live
  ready-path: /q/health/ready

# Test Profile Configuration
"%test":
  quarkus:
    http:
      port: 0  # Random port for tests
    grpc:
      server:
        port: 0  # Random port for tests
    # Disable OpenTelemetry in tests
    otel:
      enabled: false
      sdk:
        disabled: true
  module:
    # Test-specific configurations
    name: your-module-test

# Dev Profile Configuration
"%dev":
  quarkus:
    log:
      level: DEBUG
      console:
        format: "%d{HH:mm:ss} %-5p [%c{2.}] (%t) %s%e%n"
    # Dev mode container settings
    container-image:
      push: false
      build: true
```

### 4. Create Module Service Implementation

```java
package com.rokkon.yourmodule;

import com.rokkon.pipeline.grpc.*;
import com.rokkon.pipeline.module.types.BaseModuleService;
import io.grpc.stub.StreamObserver;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@GrpcService
@ApplicationScoped
public class YourModuleServiceImpl extends BaseModuleService implements YourModuleService {
    
    private static final Logger LOG = Logger.getLogger(YourModuleServiceImpl.class);
    
    @ConfigProperty(name = "quarkus.application.name")
    String moduleName;
    
    @ConfigProperty(name = "quarkus.application.version")
    String moduleVersion;
    
    @ConfigProperty(name = "module.config.buffer-size", defaultValue = "1024")
    int bufferSize;
    
    @Override
    public void process(DataPacket request, StreamObserver<DataPacket> responseObserver) {
        try {
            LOG.infof("Processing data packet: %s", request.getId());
            
            // Extract metadata
            Map<String, String> metadata = request.getMetadataMap();
            String pipelineId = metadata.get("pipeline_id");
            String sourceModule = metadata.get("source_module");
            
            // Process the data
            ProcessedData processedData = processData(request);
            
            // Build response packet
            DataPacket response = DataPacket.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setTimestamp(Instant.now().toEpochMilli())
                .setModuleName(moduleName)
                .setDataType(DataType.PROCESSED)  // Or appropriate type
                .setData(processedData.toByteString())
                .putAllMetadata(metadata)
                .putMetadata("processor", moduleName)
                .putMetadata("processing_time_ms", String.valueOf(processedData.getProcessingTime()))
                .build();
            
            // Send response
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            LOG.infof("Successfully processed packet %s", request.getId());
            
        } catch (Exception e) {
            LOG.errorf(e, "Error processing packet %s", request.getId());
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription("Processing failed: " + e.getMessage())
                    .asException()
            );
        }
    }
    
    @Override
    public Uni<ServiceRegistration> getServiceRegistration() {
        return Uni.createFrom().item(() -> {
            return ServiceRegistration.newBuilder()
                .setModuleName(moduleName)
                .setModuleId(moduleName + "-" + UUID.randomUUID().toString().substring(0, 8))
                .setVersion(moduleVersion)
                .setHost(System.getenv("HOSTNAME"))
                .setPort(39100)  // Standard internal port
                .setServiceType(ServiceType.GRPC)
                .setStatus(ServiceStatus.RUNNING)
                .putCapabilities("process", "true")
                .putCapabilities("data_types", "text,json")  // Module capabilities
                .putMetadata("module_type", "processor")
                .putMetadata("buffer_size", String.valueOf(bufferSize))
                .build();
        });
    }
    
    // Module-specific processing logic
    private ProcessedData processData(DataPacket packet) {
        long startTime = System.currentTimeMillis();
        
        // Your processing logic here
        byte[] inputData = packet.getData().toByteArray();
        
        // Example: Transform the data
        String processedContent = new String(inputData).toUpperCase();
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        return ProcessedData.newBuilder()
            .setContent(processedContent)
            .setProcessingTime(processingTime)
            .build();
    }
}
```

### 5. Create Health Check

```java
package com.rokkon.yourmodule.health;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@Readiness
@ApplicationScoped
public class YourModuleHealthCheck implements HealthCheck {
    
    @Inject
    YourModuleServiceImpl service;
    
    @Override
    public HealthCheckResponse call() {
        // Check if your module is ready
        boolean isReady = checkModuleReadiness();
        
        return HealthCheckResponse.builder()
            .name("your-module-readiness")
            .status(isReady)
            .withData("module", "your-module")
            .withData("ready", isReady)
            .build();
    }
    
    private boolean checkModuleReadiness() {
        // Add your readiness checks here
        // Examples:
        // - Check if required resources are loaded
        // - Check if connections are established
        // - Check if initialization is complete
        return true;
    }
}
```

### 6. Create module-entrypoint.sh

```bash
#!/bin/bash
set -e

# Module Entrypoint Script
echo "Starting Your Module..."

# Configuration with unified server defaults
MODULE_NAME=${MODULE_NAME:-your-module}
MODULE_HOST=${MODULE_HOST:-0.0.0.0}
MODULE_PORT=${MODULE_PORT:-39100}  # Standard internal module port

# With unified server, HTTP and gRPC use the same port
MODULE_HTTP_PORT=${MODULE_HTTP_PORT:-${MODULE_PORT}}
MODULE_GRPC_PORT=${MODULE_GRPC_PORT:-${MODULE_PORT}}

# Engine configuration
ENGINE_HOST=${ENGINE_HOST:-engine}
ENGINE_PORT=${ENGINE_PORT:-39001}

# Registration configuration
REGISTRATION_HOST=${REGISTRATION_HOST:-}
REGISTRATION_PORT=${REGISTRATION_PORT:-}
AUTO_REGISTER=${AUTO_REGISTER:-true}  # Default to true for dev, set to false when using sidecars
SHUTDOWN_ON_REGISTRATION_FAILURE=${SHUTDOWN_ON_REGISTRATION_FAILURE:-true}
MAX_RETRIES=${MAX_RETRIES:-3}

# Debug configuration
DEBUG_PORT=${DEBUG_PORT:-35100}  # Standard debug port with '5' prefix
DEBUG_ENABLED=${DEBUG_ENABLED:-false}

# Export for application use
export MODULE_NAME MODULE_HOST MODULE_PORT
export ENGINE_HOST ENGINE_PORT

# Start module
echo "Module Configuration:"
echo "  Name: $MODULE_NAME"
echo "  Host: $MODULE_HOST"
echo "  Port: $MODULE_PORT (HTTP and gRPC)"
echo "  Engine: $ENGINE_HOST:$ENGINE_PORT"
echo "  Auto-register: $AUTO_REGISTER"
echo "  Debug: $DEBUG_ENABLED (port: $DEBUG_PORT)"

# Configure Java options
JAVA_OPTS="${JAVA_OPTS} -Dquarkus.http.host=${MODULE_HOST} -Dquarkus.http.port=${MODULE_PORT}"

# Add debug options if enabled
if [ "$DEBUG_ENABLED" = "true" ]; then
    JAVA_OPTS="${JAVA_OPTS} -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${DEBUG_PORT}"
fi

# Start the module
echo "Starting module with: java ${JAVA_OPTS} ${JAVA_OPTS_APPEND} -jar /deployments/quarkus-run.jar"
java ${JAVA_OPTS} ${JAVA_OPTS_APPEND} -jar /deployments/quarkus-run.jar &
MODULE_PID=$!

# Function to check if module is healthy
check_health() {
    curl -sf http://localhost:${MODULE_PORT}/q/health/ready > /dev/null 2>&1
}

# Wait for module to be ready
echo "Waiting for module to be ready..."
for i in $(seq 1 30); do
    if check_health; then
        echo "Module is ready!"
        break
    fi
    echo "Waiting for module to start... ($i/30)"
    sleep 2
done

# Function to register module
register_module() {
    echo "Attempting to register module with engine..."
    
    # Build CLI command with all options
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
    
    # Try registration with retries
    for i in $(seq 1 $MAX_RETRIES); do
        if eval $cli_cmd; then
            echo "Registration successful!"
            return 0
        fi
        echo "Registration attempt $i failed, retrying..."
        sleep 5
    done
    
    echo "Registration failed after $MAX_RETRIES attempts"
    return 1
}

# Check if auto-registration is enabled
if [ "$AUTO_REGISTER" = "false" ]; then
    echo "Auto-registration disabled (AUTO_REGISTER=false). Module running without registration."
    # Keep the module running in foreground
    wait $MODULE_PID
else
    # Register the module
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

### 7. Create Dockerfile.jvm

```dockerfile
####
# This Dockerfile is used in order to build a container that runs the Quarkus application in JVM mode
#
# Build the image with:
# ./gradlew :modules:your-module:imageBuild
#
# Then run the container using:
# docker run -i --rm -p 39100:39100 pipeline/your-module:latest
#
###
FROM registry.access.redhat.com/ubi8/openjdk-17:1.20

ENV LANGUAGE='en_US:en'

# Install pipeline CLI (adjust version as needed)
COPY --from=pipeline/cli:latest /usr/local/bin/pipeline /usr/local/bin/pipeline

# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --chown=185 build/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 build/quarkus-app/*.jar /deployments/
COPY --chown=185 build/quarkus-app/app/ /deployments/app/
COPY --chown=185 build/quarkus-app/quarkus/ /deployments/quarkus/

# Copy entrypoint script
COPY --chown=185 src/main/bash/module-entrypoint.sh /deployments/
RUN chmod +x /deployments/module-entrypoint.sh

# Standard module port
EXPOSE 39100

# Debug port
EXPOSE 35100

USER 185
ENV AB_JOLOKIA_OFF=""
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

# Use our entrypoint script
ENTRYPOINT ["/deployments/module-entrypoint.sh"]
```

### 8. Create REST Endpoints (Optional)

```java
package com.rokkon.yourmodule.api;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;

@Path("/api/v1/your-module")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class YourModuleResource {
    
    @Inject
    YourModuleServiceImpl service;
    
    @GET
    @Path("/status")
    public Response getStatus() {
        return Response.ok(Map.of(
            "module", "your-module",
            "status", "running",
            "version", service.getModuleVersion()
        )).build();
    }
    
    @POST
    @Path("/configure")
    public Response configure(Map<String, Object> config) {
        // Handle runtime configuration updates
        service.updateConfiguration(config);
        return Response.ok(Map.of("status", "configured")).build();
    }
}
```

### 9. Add Module to PipelineModule Enum (Dev Mode)

For development mode, add your module to the PipelineModule enum:

```java
// In engine/pipestream/src/main/java/com/rokkon/pipeline/engine/dev/PipelineModule.java
public enum PipelineModule {
    // ... existing modules ...
    YOUR_MODULE("your-module", "pipeline/your-module:latest", "Description of your module", "1G"),
    // ... rest of enum
}
```

## Development vs Production

### Development Mode
- Modules deployed as local Docker containers
- Dynamic port allocation (39100-39800)
- Auto-registration enabled by default
- Module management UI available
- Hot reload support with Quarkus dev mode

### Production Mode
- Modules deployed via orchestration (Kubernetes)
- Port configuration managed by platform
- Registration handled by sidecars
- No direct Docker operations
- Centralized logging and monitoring

### Environment Variables

#### Common Variables
- `MODULE_NAME`: Module identifier
- `MODULE_PORT`: Internal port (always 39100)
- `ENGINE_HOST`: Pipeline engine host
- `ENGINE_PORT`: Pipeline engine port
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OpenTelemetry endpoint

#### Dev Mode Specific
- `AUTO_REGISTER`: Set to true (default)
- `REGISTRATION_HOST`: External host for registration
- `REGISTRATION_PORT`: External port for registration

#### Production Specific
- `AUTO_REGISTER`: Set to false (sidecar handles registration)
- `CONSUL_HOST`: Consul agent host
- `CONSUL_PORT`: Consul agent port

## Testing Guidelines

### 1. Unit Tests

```java
package com.rokkon.yourmodule;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class YourModuleServiceTest {
    
    @Test
    void testDataProcessing() {
        // Test your processing logic
    }
    
    @Test
    void testServiceRegistration() {
        // Test registration info
    }
}
```

### 2. Integration Tests

```java
package com.rokkon.yourmodule;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
class YourModuleIntegrationTest {
    
    @Test
    void testHealthEndpoint() {
        RestAssured.given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200);
    }
    
    @Test
    void testGrpcService() {
        // Test gRPC endpoints
    }
}
```

### 3. Running Tests

```bash
# Unit tests
./gradlew :modules:your-module:test

# Integration tests
./gradlew :modules:your-module:quarkusIntTest

# All tests
./gradlew :modules:your-module:check
```

## Best Practices

### 1. Module Design
- Keep modules focused on a single responsibility
- Design for horizontal scaling
- Handle backpressure appropriately
- Implement proper error handling and recovery

### 2. Configuration
- Use environment variables for runtime configuration
- Provide sensible defaults
- Document all configuration options
- Validate configuration on startup

### 3. Observability
- Use structured logging with appropriate levels
- Export metrics for key operations
- Include trace context in gRPC calls
- Add custom health checks for dependencies

### 4. Resource Management
- Set appropriate memory limits
- Configure thread pools based on workload
- Implement graceful shutdown
- Clean up resources properly

### 5. Error Handling
- Return appropriate gRPC status codes
- Include helpful error messages
- Don't leak internal details
- Log errors with context

### 6. Performance
- Profile your module under load
- Optimize hot paths
- Use appropriate data structures
- Consider caching where beneficial

## Troubleshooting

### Module Won't Start
1. Check logs: `docker logs your-module-module-app`
2. Verify port 39100 is exposed
3. Check health endpoint: `curl http://localhost:39100/q/health/ready`
4. Ensure all dependencies are included

### Registration Fails
1. Verify module name matches everywhere
2. Check engine connectivity
3. Ensure health checks pass
4. Look for registration errors in logs

### gRPC Errors
1. Check message size limits for large payloads
2. Verify service is properly annotated with @GrpcService
3. Ensure proto definitions match
4. Check client/server compatibility

### Memory Issues
1. Increase container memory limits
2. Profile for memory leaks
3. Tune JVM heap settings
4. Check for resource cleanup

### Performance Problems
1. Enable debug logging temporarily
2. Check metric endpoints
3. Profile CPU usage
4. Look for blocking operations

## Module Types

### Processor Module
- Transforms input data
- One input, one output
- Stateless operation

### Generator Module
- Creates new data
- No input, one output
- May be triggered or scheduled

### Aggregator Module
- Combines multiple inputs
- Multiple inputs, one output
- May maintain state

### Splitter Module
- Divides input into parts
- One input, multiple outputs
- Routing logic

### Filter Module
- Selectively passes data
- One input, one output
- Conditional logic

## Next Steps

1. Create your module following this guide
2. Add comprehensive tests
3. Document module-specific features
4. Submit for code review
5. Add to module registry
6. Create example pipelines

Remember: All modules use port 39100 internally, follow the naming conventions, and should be designed for containerized deployment.