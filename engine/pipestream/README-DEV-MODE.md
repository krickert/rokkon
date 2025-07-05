# Pipeline Engine Dev Mode

## Quick Start

```bash
./gradlew :engine:pipestream:quarkusDev
```

This single command:
1. Starts a Consul server (UI at http://localhost:38500)
2. Starts a local Consul agent (on localhost:8500)
3. Seeds initial configuration
4. Starts Quarkus dev mode with live reload

## Architecture

Dev mode uses a two-container Consul setup that mirrors production:

- **Consul Server**: Runs in Docker, UI exposed on port 38500
- **Consul Agent**: Runs with host networking, provides localhost:8500
- **Engine**: Connects to localhost:8500 (same as production)

This approach ensures:
- No port conflicts with production Consul (8500)
- Same configuration approach as production
- Modules can join the same Consul cluster

## Configuration

The dev mode automatically seeds these configurations:

| Key | Value | Purpose |
|-----|-------|---------|
| `config/application/pipeline.engine.name` | pipeline-engine-dev | Engine identifier |
| `config/application/pipeline.engine.version` | 1.0.0-DEV | Version string |
| `config/dev/quarkus.http.port` | 39001 | HTTP API port |
| `config/dev/quarkus.grpc.server.port` | 49001 | gRPC service port |
| `config/dev/pipeline.engine.dev-mode` | true | Dev mode flag |

## Gradle Implementation

The implementation is in `build.gradle.kts`:

```kotlin
tasks.register("startConsulDev") {
    doLast {
        // 1. Start Consul server on port 38500
        // 2. Start Consul agent with host networking
        // 3. Seed configuration
    }
}

tasks.named<QuarkusDev>("quarkusDev") {
    dependsOn("startConsulDev")
}
```

## Troubleshooting

### Port Already in Use

If you see "Port 38500 is already in use":
```bash
# Find and stop the process using the port
lsof -i :38500
# Or stop all Consul containers
docker stop $(docker ps -q -f ancestor=hashicorp/consul:1.21)
```

### Connection Refused

If Quarkus can't connect to Consul:
1. Check that both containers started: `docker ps | grep consul`
2. Verify the agent is on host network: should show `host` in NETWORK
3. Test connection: `curl http://localhost:8500/v1/status/leader`

### Configuration Not Loading

Check Consul KV store:
```bash
# Via UI
open http://localhost:38500/ui/dc1/kv

# Via CLI
curl http://localhost:8500/v1/kv/config/application?recurse
```

## Development Workflow

1. **Start Dev Mode**: `./gradlew :engine:pipestream:quarkusDev`
2. **Make Code Changes**: Quarkus will auto-reload
3. **Add Modules**: They can register with the same Consul cluster
4. **View Services**: Check http://localhost:38500/ui for registered services

## Module Integration

Modules can join the dev Consul cluster:

```java
// Module configuration
consul.host = "host.docker.internal"  // From container
consul.port = 8500
consul.join = "consul-server"  // The dev Consul server
```

Or via Docker Compose:
```yaml
services:
  my-module:
    environment:
      - CONSUL_HOST=consul-agent
    depends_on:
      - consul-agent
  
  consul-agent:
    image: consul:1.21
    command: agent -join rokkon-consul-dev
```

## Differences from Production

| Aspect | Dev Mode | Production |
|--------|----------|------------|
| Consul UI Port | 38500 | 8500 |
| Engine HTTP Port | 39001 | 38090 |
| Engine gRPC Port | 49001 | 49000 |
| Consul Setup | Auto-started by Gradle | Pre-existing infrastructure |
| Configuration | Auto-seeded | Manually configured |

## Implementation Notes

### Why Two Containers?

1. **Server Container**: Provides the Consul cluster and UI
2. **Agent Container**: Provides localhost:8500 for the engine

This mirrors production where each service has a local Consul agent.

### Why Port 38500?

- Avoids conflicts with production Consul (8500)
- Easy to remember (38xxx range for dev services)
- UI still accessible for debugging

### Host Networking

The agent container uses host networking to bind to localhost:8500. This requires:
- Linux or macOS (Windows may need WSL2)
- No other process on port 8500

## Related Documentation

- [Dev Mode Architecture](../../DEV_MODE_ARCHITECTURE.md) - Detailed architecture
- [Module Development](../../modules/README.md) - Creating modules
- [Consul Integration](../consul/README.md) - Consul configuration details