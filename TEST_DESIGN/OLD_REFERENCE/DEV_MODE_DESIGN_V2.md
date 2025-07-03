# Pipeline Dev Mode Design V2 - Hybrid Approach with Quarkus Compose Dev Services

## Executive Summary

This design leverages Quarkus's built-in Docker Compose Dev Services for infrastructure management while maintaining custom Docker Java API code for dynamic module management. This hybrid approach gives us zero-setup development with the flexibility to start/stop individual modules on demand.

## Research Findings

### Quarkus Compose Dev Services Capabilities

Based on analysis of Quarkus source code (version 3.24.2):

#### What Quarkus DOES Provide:
1. **Automatic Compose File Detection**
   - Pattern: `(^docker-compose|^compose)(-dev(-)?service).*.(yml|yaml)`
   - Examples: `docker-compose-dev-service.yml`, `compose-devservice.yml`
   - Auto-starts on `./gradlew quarkusDev`

2. **Container Lifecycle Management**
   - Starts all services via `compose up`
   - Stops all services via `compose down`
   - Reuses existing containers between restarts
   - Automatic cleanup with Ryuk

3. **Configuration Features**
   - Profile support for conditional services
   - Environment variable injection
   - Configuration mapping via labels
   - Health check support
   - Wait strategies (logs, ports, health)
   - Project name isolation

4. **Build-time Configuration** (`application.properties`):
   ```properties
   quarkus.compose.devservices.enabled=true
   quarkus.compose.devservices.files=docker-compose-dev-service.yml
   quarkus.compose.devservices.project-name=pipeline-dev
   quarkus.compose.devservices.profiles=default-modules
   quarkus.compose.devservices.start-services=true
   quarkus.compose.devservices.stop-services=false  # Keep running between restarts
   ```

#### What Quarkus DOESN'T Provide:
1. **No Dynamic Service Management**
   - Cannot start/stop individual services after initial startup
   - No runtime modification of running services
   - No individual container management APIs

2. **Limited Control**
   - All-or-nothing approach with services
   - No programmatic access to compose operations
   - No module-specific operations

### Sidecar Pattern Compatibility

Our `network_mode: "service:sidecar"` pattern is fully compatible because:
- Quarkus executes standard docker compose commands
- No special handling that would break network modes
- Labels and health checks work as expected

## Hybrid Design Approach

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Host Machine                               │
│                                                                   │
│  ┌─────────────────────────┐                                     │
│  │   Engine (Quarkus)       │                                     │
│  │   - Hot Reload           │──────connects to──────┐            │
│  │   - REST API :39001      │                       │            │
│  │   - gRPC :49001          │                       │            │
│  │   - Custom Module Mgmt   │                       │            │
│  └─────────────────────────┘                        │            │
│              │                                       ▼            │
│              │                  ┌─────────────────────────────┐  │
│              ▼                  │  Engine Consul Sidecar      │  │
│  ┌─────────────────────────┐   │  (Started by Quarkus)       │  │
│  │ PipelineDevModeInfra    │   │  localhost:8501             │  │
│  │ - Docker Java API       │   └─────────────────────────────┘  │
│  │ - Dynamic Module Mgmt   │                                     │
│  └─────────────────────────┘                                     │
│                                                                   │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │           Docker Environment (Managed by Both)              │   │
│  │                                                             │   │
│  │  ┌──────────────────────────────────────────────────┐      │   │
│  │  │        Quarkus Compose Dev Services              │      │   │
│  │  │  - Consul Server                                 │      │   │
│  │  │  - Engine Sidecar                                │      │   │
│  │  │  - Default Modules (echo, test)                  │      │   │
│  │  └──────────────────────────────────────────────────┘      │   │
│  │                                                             │   │
│  │  ┌──────────────────────────────────────────────────┐      │   │
│  │  │      Custom Docker Java API Management           │      │   │
│  │  │  - Additional Modules (parser, chunker, etc)     │      │   │
│  │  │  - Dynamic start/stop                            │      │   │
│  │  │  - Individual control                            │      │   │
│  │  └──────────────────────────────────────────────────┘      │   │
│  └───────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────────┘
```

### Division of Responsibilities

#### Quarkus Compose Dev Services Handles:
1. **Core Infrastructure**
   - Consul server
   - Engine Consul sidecar
   - Configuration seeder
   - Network creation

2. **Default Modules**
   - Echo module + sidecar
   - Test module + sidecar
   - Started via compose profiles

3. **Lifecycle**
   - Auto-start on `quarkusDev`
   - Container reuse between restarts
   - Cleanup on shutdown (optional)

#### Custom Docker Java API Handles:
1. **Dynamic Module Management**
   - Start/stop individual modules
   - Module health monitoring
   - Resource usage tracking

2. **Additional Modules**
   - Parser, chunker, embedder
   - Started on-demand
   - Individual lifecycle control

3. **Developer APIs**
   - REST endpoints for module control
   - Dev UI integration
   - Batch operations

## Implementation Strategy

### Phase 1: Quarkus Compose Integration

#### 1.1 Create `docker-compose-dev-service.yml`
```yaml
# Location: project root (where Quarkus auto-detects it)
version: '3.8'

services:
  # Core Infrastructure (always started)
  consul-server:
    image: hashicorp/consul:1.21
    container_name: consul-server-dev
    ports:
      - "8500:8500"
      - "8600:8600/udp"
    command: agent -server -ui -client=0.0.0.0 -bind=0.0.0.0 -bootstrap-expect=1
    labels:
      - "pipeline.service.type=infrastructure"
    healthcheck:
      test: ["CMD", "consul", "info"]
      interval: 5s
      timeout: 3s
      retries: 10

  engine-sidecar:
    image: hashicorp/consul:1.21
    container_name: engine-sidecar-dev
    depends_on:
      consul-server:
        condition: service_healthy
    ports:
      - "8501:8500"
    command: >
      agent -client=0.0.0.0 -bind=0.0.0.0 
      -retry-join=consul-server
      -advertise=${ENGINE_HOST_IP:-host.docker.internal}
    environment:
      - ENGINE_HOST_IP=${ENGINE_HOST_IP:-host.docker.internal}
    labels:
      - "pipeline.service.type=infrastructure"
      - "io.quarkus.devservices.compose.wait_for.logs=.*Synced node info.*"

  # Default Modules (profile: default-modules)
  echo-sidecar:
    profiles: ["default-modules"]
    image: hashicorp/consul:1.21
    container_name: echo-sidecar-dev
    depends_on:
      consul-server:
        condition: service_healthy
    ports:
      - "39090:39090"
      - "49090:49090"
    command: agent -client=0.0.0.0 -bind=0.0.0.0 -retry-join=consul-server

  echo-module:
    profiles: ["default-modules"]
    image: pipeline/echo:latest
    container_name: echo-module-dev
    network_mode: "service:echo-sidecar"
    environment:
      - MODULE_NAME=echo
      - ENGINE_HOST=${ENGINE_HOST_IP:-host.docker.internal}
      - ENGINE_PORT=49001
    labels:
      - "pipeline.module.name=echo"
      - "pipeline.module.type=default"

  test-sidecar:
    profiles: ["default-modules"]
    image: hashicorp/consul:1.21
    container_name: test-sidecar-dev
    depends_on:
      consul-server:
        condition: service_healthy
    ports:
      - "39095:39095"
      - "49095:49095"
    command: agent -client=0.0.0.0 -bind=0.0.0.0 -retry-join=consul-server

  test-module:
    profiles: ["default-modules"]
    image: pipeline/test-module:latest
    container_name: test-module-dev
    network_mode: "service:test-sidecar"
    environment:
      - MODULE_NAME=test
      - ENGINE_HOST=${ENGINE_HOST_IP:-host.docker.internal}
      - ENGINE_PORT=49001
    labels:
      - "pipeline.module.name=test"
      - "pipeline.module.type=default"
```

#### 1.2 Configure Quarkus (`application.properties`)
```properties
# Dev profile compose configuration
%dev.quarkus.compose.devservices.enabled=true
%dev.quarkus.compose.devservices.files=docker-compose-dev-service.yml
%dev.quarkus.compose.devservices.project-name=pipeline-dev
%dev.quarkus.compose.devservices.profiles=default-modules
%dev.quarkus.compose.devservices.start-services=true
%dev.quarkus.compose.devservices.stop-services=false
%dev.quarkus.compose.devservices.remove-volumes=false
%dev.quarkus.compose.devservices.follow-container-logs=false

# Dev mode ports (different from production)
%dev.quarkus.http.port=39001
%dev.quarkus.grpc.server.port=49001

# Consul configuration for dev
%dev.quarkus.consul.agent.host=localhost
%dev.quarkus.consul.agent.port=8501
```

### Phase 2: Custom Module Management

#### 2.1 Module Definitions
```java
package com.rokkon.pipeline.engine.dev;

public enum PipelineModule {
    // Default modules (managed by Quarkus compose)
    ECHO("echo", "pipeline/echo:latest", "Simple echo module", "1G", true),
    TEST("test", "pipeline/test-module:latest", "Test module", "1G", true),
    
    // On-demand modules (managed by custom code)
    PARSER("parser", "pipeline/parser:latest", "Document parser", "1G", false),
    CHUNKER("chunker", "pipeline/chunker:latest", "Text chunking", "4G", false),
    EMBEDDER("embedder", "pipeline/embedder:latest", "ML embedding", "8G", false);
    
    private final String name;
    private final String image;
    private final String description;
    private final String memory;
    private final boolean defaultModule;
    
    // constructor and getters...
}
```

#### 2.2 Enhanced Infrastructure Manager
```java
package com.rokkon.pipeline.engine.dev;

@ApplicationScoped
@IfBuildProfile("dev")
public class PipelineDevModeInfrastructure {
    
    @Inject
    DockerClient dockerClient;
    
    @ConfigProperty(name = "quarkus.compose.devservices.project-name")
    String projectName;
    
    // Only manages on-demand modules, not default ones
    public void startModule(PipelineModule module) {
        if (module.isDefaultModule()) {
            throw new IllegalArgumentException(
                "Module " + module.getName() + " is managed by Quarkus Compose");
        }
        
        // Implementation for dynamic module start
        startModuleWithSidecar(module);
    }
    
    public void stopModule(PipelineModule module) {
        if (module.isDefaultModule()) {
            // For default modules, we can only deregister from Consul
            deregisterFromConsul(module);
            return;
        }
        
        // Implementation for dynamic module stop
        stopModuleContainers(module);
    }
    
    public List<ModuleStatus> getModuleStatus() {
        List<ModuleStatus> statuses = new ArrayList<>();
        
        // Check all modules using Docker API
        for (PipelineModule module : PipelineModule.values()) {
            boolean running = isModuleRunning(module);
            boolean healthy = running ? isModuleHealthy(module) : false;
            statuses.add(new ModuleStatus(module, running, healthy));
        }
        
        return statuses;
    }
    
    private void startModuleWithSidecar(PipelineModule module) {
        // 1. Create sidecar container
        String sidecarName = module.getName() + "-sidecar-dev";
        createConsulSidecar(sidecarName, module);
        
        // 2. Create module container with network_mode
        String moduleName = module.getName() + "-module-dev";
        createModuleContainer(moduleName, module, sidecarName);
        
        // 3. Wait for health
        waitForModuleHealth(module);
    }
}
```

#### 2.3 Separate Compose File for On-Demand Modules
```yaml
# docker-compose-ondemand-modules.yml
# Used by custom code, not Quarkus
version: '3.8'

services:
  # Parser Module
  parser-sidecar:
    image: hashicorp/consul:1.21
    container_name: parser-sidecar-dev
    external_links:
      - consul-server-dev:consul-server
    command: agent -client=0.0.0.0 -bind=0.0.0.0 -retry-join=consul-server
    ports:
      - "39093:39093"
      - "49093:49093"

  parser-module:
    image: pipeline/parser:latest
    container_name: parser-module-dev
    network_mode: "service:parser-sidecar"
    environment:
      - MODULE_NAME=parser
      - ENGINE_HOST=${ENGINE_HOST_IP:-host.docker.internal}
      - ENGINE_PORT=49001

  # Similar definitions for chunker and embedder...
```

### Phase 3: REST API and Dev UI

#### 3.1 Module Management API
```java
@Path("/q/dev/pipeline")
@IfBuildProfile("dev")
public class DevModuleResource {
    
    @Inject
    PipelineDevModeInfrastructure infrastructure;
    
    @GET
    @Path("/modules")
    public Response listModules() {
        return Response.ok(infrastructure.getModuleStatus()).build();
    }
    
    @POST
    @Path("/modules/{name}/start")
    public Response startModule(@PathParam("name") String name) {
        try {
            PipelineModule module = PipelineModule.fromName(name);
            if (module.isDefaultModule()) {
                return Response.status(400)
                    .entity("Default modules are managed by Quarkus")
                    .build();
            }
            infrastructure.startModule(module);
            return Response.ok().build();
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }
}
```

## Implementation Phases

### Phase 0: Foundation (Same as original)
- Add Docker client dependency (dev-only)
- Verify Docker and ports
- Create package structure

### Phase 1: Quarkus Compose Integration
- Create `docker-compose-dev-service.yml`
- Configure Quarkus properties
- Test auto-start functionality
- Verify default modules work

### Phase 2: Custom Module Management
- Implement Docker Java API code
- Create on-demand module definitions
- Add start/stop logic
- Test with parser module

### Phase 3: REST API
- Create Dev endpoints
- Add module status
- Implement batch operations
- Add error handling

### Phase 4: Dev UI Integration
- Extend Quarkus Dev UI
- Add module cards
- Real-time status updates
- Start/stop controls

### Phase 5: Testing & Polish
- Integration tests
- Error scenarios
- Documentation
- Performance optimization

## Key Benefits of Hybrid Approach

1. **Zero Configuration**: Quarkus handles compose file detection and startup
2. **Flexibility**: Can still start/stop modules dynamically
3. **Reliability**: Leverages proven Quarkus infrastructure
4. **Maintainability**: Less custom code to maintain
5. **Compatibility**: Works with existing module structure

## Configuration Summary

### Required Files:
1. `docker-compose-dev-service.yml` - Core infrastructure + default modules
2. `docker-compose-ondemand-modules.yml` - Additional modules (reference only)
3. `application.properties` - Quarkus compose configuration
4. `application-dev.yml` - Dev profile settings

### Key Configuration Properties:
```properties
# Quarkus Compose Dev Services
%dev.quarkus.compose.devservices.enabled=true
%dev.quarkus.compose.devservices.project-name=pipeline-dev
%dev.quarkus.compose.devservices.profiles=default-modules

# Dev Mode Ports
%dev.quarkus.http.port=39001
%dev.quarkus.grpc.server.port=49001

# Consul Connection
%dev.quarkus.consul.agent.port=8501

# Custom Dev Mode
%dev.pipeline.dev.ondemand-modules=PARSER,CHUNKER,EMBEDDER
```

## Migration Notes

### From Original Design:
1. Keep all custom Docker Java API code
2. Move core infrastructure to Quarkus compose
3. Split modules into default vs on-demand
4. Update REST API to respect module types
5. Adjust Dev UI to show module categories

### Testing Strategy:
1. Test Quarkus compose startup alone
2. Add custom module management
3. Verify both systems work together
4. Test edge cases (restarts, failures)

## Conclusion

This hybrid approach leverages Quarkus's strengths for infrastructure management while preserving our requirement for dynamic module control. It reduces complexity, improves reliability, and maintains all the flexibility we need for an effective development environment.