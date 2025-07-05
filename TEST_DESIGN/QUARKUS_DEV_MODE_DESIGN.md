# Quarkus Dev Mode Design - Zero Setup Development

## Goal
When a developer runs `./gradlew quarkusDev`, everything needed for pipeline development starts automatically:
1. Consul server for service discovery
2. Consul configuration seeding
3. Engine consul sidecar
4. Echo and test-module (our default modules)

## Core Principle: Use What Quarkus Provides

### What Quarkus Dev Services Gives Us:
- Automatic detection of `docker-compose-dev-service.yml` files
- Automatic startup when running `quarkusDev`
- Container reuse between restarts
- Proper cleanup on shutdown
- Configuration mapping via labels

### What We Need to Build:
- Dynamic module start/stop (future phase)
- Dev UI integration (future phase)

## Implementation Plan

### Step 1: Create docker-compose-dev-service.yml

Location: Project root (where Quarkus will auto-detect it)

```yaml
version: '3.8'

services:
  # Consul Server
  consul-server:
    image: hashicorp/consul:1.21
    container_name: consul-server-dev
    ports:
      - "8500:8500"
      - "8600:8600/udp"
    command: agent -server -ui -client=0.0.0.0 -bind=0.0.0.0 -bootstrap-expect=1 -dev
    healthcheck:
      test: ["CMD", "consul", "info"]
      interval: 5s
      timeout: 3s
      retries: 10

  # Configuration Seeder (runs once)
  consul-seeder:
    build:
      context: ./cli/seed-engine-consul-config
      dockerfile: Dockerfile
    depends_on:
      consul-server:
        condition: service_healthy
    command: [
      "java", "-jar", "build/quarkus-app/quarkus-run.jar",
      "-h", "consul-server", "-p", "8500",
      "--key", "config/application",
      "--import", "seed-data.json",
      "--force"
    ]
    volumes:
      - ./cli/seed-engine-consul-config/seed-data.json:/app/seed-data.json:ro

  # Engine Consul Sidecar
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
      ENGINE_HOST_IP: ${ENGINE_HOST_IP:-host.docker.internal}

  # Echo Module Sidecar
  echo-sidecar:
    image: hashicorp/consul:1.21
    container_name: echo-sidecar-dev
    depends_on:
      consul-server:
        condition: service_healthy
    ports:
      - "39090:39090"
      - "49090:49090"
    command: agent -client=0.0.0.0 -bind=0.0.0.0 -retry-join=consul-server

  # Echo Module
  echo-module:
    image: pipeline/echo:latest
    container_name: echo-module-dev
    network_mode: "service:echo-sidecar"
    depends_on:
      echo-sidecar:
        condition: service_started
    environment:
      MODULE_NAME: echo
      MODULE_HOST: echo-sidecar
      MODULE_HTTP_PORT: 39090
      MODULE_GRPC_PORT: 49090
      ENGINE_HOST: ${ENGINE_HOST_IP:-host.docker.internal}
      ENGINE_PORT: 49001  # Dev port
      QUARKUS_PROFILE: dev

  # Test Module Sidecar
  test-sidecar:
    image: hashicorp/consul:1.21
    container_name: test-sidecar-dev
    depends_on:
      consul-server:
        condition: service_healthy
    ports:
      - "39095:39095"
      - "49095:49095"
    command: agent -client=0.0.0.0 -bind=0.0.0.0 -retry-join=consul-server

  # Test Module
  test-module:
    image: pipeline/test-module:latest
    container_name: test-module-dev
    network_mode: "service:test-sidecar"
    depends_on:
      test-sidecar:
        condition: service_started
    environment:
      MODULE_NAME: test
      MODULE_HOST: test-sidecar
      MODULE_HTTP_PORT: 39095
      MODULE_GRPC_PORT: 49095
      ENGINE_HOST: ${ENGINE_HOST_IP:-host.docker.internal}
      ENGINE_PORT: 49001  # Dev port
      QUARKUS_PROFILE: dev
```

### Step 2: Configure application-dev.yml

```yaml
# engine/pipestream/src/main/resources/application-dev.yml
quarkus:
  # Dev mode ports (different from production)
  http:
    port: 39001
  grpc:
    server:
      port: 49001
  
  # Connect to engine sidecar (not main consul)
  consul-config:
    agent:
      host: localhost
      port: 8501
    enabled: true
    fail-on-missing-key: false
    properties-value-keys:
      - config/application
      - config/dev
```

### Step 3: Test It!

```bash
# Clean any existing containers
docker compose -p pipeline-dev down -v

# Run quarkus dev
./gradlew :engine:pipestream:quarkusDev
```

Expected Result:
1. Quarkus detects docker-compose-dev-service.yml
2. Starts all containers automatically
3. Engine connects to consul via localhost:8501
4. Echo and test modules register themselves
5. Everything just works!

## What We're NOT Doing (Yet)

1. **No custom Docker management code** - Let Quarkus handle it
2. **No dynamic module start/stop** - That's phase 2
3. **No Dev UI** - That's phase 3
4. **No complex implementation** - Keep it simple

## Key Ports and Access Points

When dev mode is running, you can access:

- **Frontend/UI**: http://localhost:39001 (vs production 39000)
- **Consul UI**: http://localhost:8500
- **Engine gRPC**: localhost:49001 (vs production 49000)
- **Engine Consul Sidecar**: localhost:8501 (engine connects here)

## How It Works

1. **Consul Server** starts on port 8500 (main instance)
2. **Engine Sidecar** starts on port 8501 (engine's dedicated Consul agent)
3. **Seeder** seeds configuration to the engine sidecar (not main Consul)
4. **Engine** connects to localhost:8501 for Consul config
5. **Modules** register with the engine via gRPC on port 49001

## Configuration Notes

- The seeder runs once and exits (this is normal)
- Configuration is seeded to the engine sidecar to keep it isolated
- Dev mode uses different ports to avoid conflicts with production
- No user-specific configuration needed - works OOTB

## Success Criteria

- [ ] Running `./gradlew quarkusDev` starts all containers
- [ ] Engine connects to consul at localhost:8501
- [ ] Frontend accessible at http://localhost:39001
- [ ] Echo module is accessible and registered
- [ ] Test module is accessible and registered
- [ ] No manual docker commands needed
- [ ] Containers persist between restarts (faster dev cycle)

## Notes

- Using `host.docker.internal` for ENGINE_HOST_IP should work on all Docker Desktop installations
- Dev ports (39001/49001) are different from production (39000/49000) to avoid conflicts
- Consul seeder runs once and exits (normal behavior)
- All containers use `-dev` suffix to avoid conflicts with production