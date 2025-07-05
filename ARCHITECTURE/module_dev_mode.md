# Module Dev Mode Architecture

## Overview

The module dev mode deployment system allows developers to dynamically deploy and test pipeline modules in a development environment. It mimics production deployment patterns using Docker containers with sidecars for service discovery and registration.

## Key Components

### 1. Module Management REST API

Located at `/api/v1/module-management/*`, provides endpoints for:
- `GET /available` - List available modules from the PipelineModule enum
- `GET /deployed` - List currently deployed modules
- `POST /{moduleName}/deploy` - Deploy a module with sidecars
- `DELETE /{moduleName}` - Stop and remove a module
- `GET /{moduleName}/status` - Get module deployment status

### 2. Module Deployment Service

The `ModuleDeploymentService` orchestrates the deployment of modules with their sidecars:

```java
// Each module deployment creates:
1. A dedicated Docker network
2. A Consul agent sidecar (for service discovery)
3. The module container
4. A registration sidecar (to register with engine)
```

### 3. Module Architecture

Each deployed module consists of three containers:

```
┌─────────────────────────────────────────┐
│   Module Network (echo-network)         │
├─────────────────────────────────────────┤
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  Consul Agent Sidecar           │   │
│  │  (consul-agent-echo)            │   │
│  │  - Exposes port 39100           │   │
│  │  - Connects to Consul server    │   │
│  └─────────────────────────────────┘   │
│            ↑                            │
│            │ Shared Network Namespace   │
│            ↓                            │
│  ┌─────────────────────────────────┐   │
│  │  Module Container               │   │
│  │  (echo-module-app)              │   │
│  │  - Runs on port 39100           │   │
│  │  - AUTO_REGISTER=false          │   │
│  └─────────────────────────────────┘   │
│            ↑                            │
│            │ Shared Network Namespace   │
│            ↓                            │
│  ┌─────────────────────────────────┐   │
│  │  Registration Sidecar           │   │
│  │  (echo-registrar)               │   │
│  │  - Waits 15 seconds             │   │
│  │  - Registers module with engine │   │
│  └─────────────────────────────────┘   │
│                                         │
└─────────────────────────────────────────┘
```

## Deployment Process

### 1. Network Creation
Each module gets its own Docker network to isolate it from other modules:
```java
docker network create echo-network
```

### 2. Consul Agent Sidecar
The Consul agent provides service discovery and exposes the module's port:
```java
docker run -d --name consul-agent-echo \
  --network echo-network \
  -p 39100:39100 \
  hashicorp/consul:1.21 agent \
  -node=echo-sidecar \
  -retry-join=<host-ip>:38500
```

### 3. Module Container
The module shares the Consul agent's network namespace:
```java
docker run -d --name echo-module-app \
  --network container:consul-agent-echo \
  pipeline/echo-module:latest
```

Environment variables:
- `MODULE_NAME=echo-module`
- `MODULE_PORT=39100`
- `ENGINE_HOST=<host-ip>`
- `ENGINE_PORT=39001`
- `AUTO_REGISTER=false`
- `REGISTRATION_HOST=<host-ip>`
- `REGISTRATION_PORT=39100`

### 4. Registration Sidecar
Handles module registration after startup:
```bash
#!/bin/sh
echo 'Waiting for module to be ready...'
sleep 15

echo 'Attempting to register module echo with engine...'
java -jar /deployments/pipeline-cli.jar register \
  --module-host=localhost \
  --module-port=39100 \
  --engine-host=<host-ip> \
  --engine-port=39001 \
  --registration-host=<host-ip> \
  --registration-port=39100

echo 'Registration sidecar complete. Sleeping...'
sleep infinity
```

## Port Conventions

Modules use the unified server pattern (single port for HTTP and gRPC):
- Echo: 39100
- Test: 39101
- Parser: 39102
- Chunker: 39103
- Embedder: 39104

## Key Design Decisions

### 1. Shared Network Namespace
The module and Consul agent share the same network namespace using Docker's `--network container:` option. This allows:
- The module to listen on localhost
- The Consul agent to expose the module's port to the host
- All containers to communicate via localhost

### 2. Disabled Auto-Registration
Modules have `AUTO_REGISTER=false` to prevent the entrypoint script from attempting registration. This is necessary because:
- In dev mode, the engine runs on the host (not in Docker)
- Direct registration from the module would fail due to network isolation
- The registration sidecar handles this after the module is ready

### 3. Registration Sidecar Pattern
A separate container handles registration because:
- It can wait for the module to be fully started
- It provides better error handling and retry logic
- It mimics production patterns where registration is often handled by init containers

### 4. Host IP Detection
The `HostIPDetector` service determines the correct IP for Docker containers to reach the host:
- Uses `host.docker.internal` on Docker Desktop
- Falls back to bridge network gateway on Linux

## Observability

### OpenTelemetry
Modules send telemetry directly to the LGTM stack's OTEL collector:
- Endpoint configured via `OTEL_EXPORTER_OTLP_ENDPOINT`
- Typically points to the Quarkus DevServices LGTM instance

### Health Checks
Modules expose health endpoints:
- HTTP: `http://localhost:39100/q/health`
- gRPC: `grpc.health.v1.Health/Check`

## Comparison with Production

This dev mode setup closely mimics production deployment patterns, particularly in its use of sidecars for service discovery. The core `pipeline-cli` registration mechanism remains consistent, with the invocation method adapting to the deployment environment.

| Aspect | Dev Mode | Production |
|--------|----------|------------|
| Network | Dedicated Docker network | Kubernetes namespace |
| Service Discovery | Consul sidecar | Consul/K8s service |
| Registration | Registration sidecar | Init container |
| Port Exposure | Docker port binding | K8s service/ingress |
| Observability | Direct to LGTM | Via OTEL collector |

## Module Lifecycle

### Deployment
1. Create network and sidecars
2. Start module container
3. Wait for health checks
4. Register with engine

### Shutdown
1. Stop all containers
2. Remove network
3. Deregister from engine (automatic via Consul)

## Configuration

Key configuration properties:
```yaml
# Consul server (dev mode)
consul.server.port: 38500

# Module defaults
pipeline.dev.auto-start: true
pipeline.dev.compose-project-name: pipeline-dev

# OTEL endpoint (from DevServices)
quarkus.otel.exporter.otlp.endpoint: http://localhost:32794
```

## Troubleshooting

### Module Not Accessible
- Check if Consul agent exposed the port: `docker port consul-agent-echo`
- Verify module is healthy: `docker logs echo-module-app`

### Registration Failed
- Check registration sidecar logs: `docker logs echo-registrar`
- Verify engine is reachable from container
- Ensure registration host/port are correct

### Port Conflicts
- Each module must use a unique port
- Check for existing containers: `docker ps`

## Future Enhancements

1. **Kubernetes Mode**: Deploy to local K8s cluster
2. **Multi-Module Pipelines**: Deploy connected module chains
3. **Hot Reload**: Update module without full redeploy
4. **Resource Monitoring**: Track CPU/memory usage
5. **Distributed Tracing**: Visualize request flow through modules