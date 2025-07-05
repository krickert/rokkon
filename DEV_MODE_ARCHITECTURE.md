# Dev Mode Architecture

## Overview

The Rokkon Pipeline Engine dev mode provides a zero-configuration development environment that mirrors production architecture while being simple to start with `./gradlew :engine:pipestream:quarkusDev`.

## Working Implementation

After extensive testing, we have a working dev mode implementation that solves the chicken-and-egg problem where Quarkus needs Consul configuration during build time, but Consul needs to be started first.

### Key Components

1. **Two-Container Consul Setup**:
   - Consul Server: Runs in bridge network, stores configuration
   - Consul Agent: Runs with port binding to 8501, acts as engine sidecar

2. **Port Strategy**:
   - **8501**: Consul agent port (mapped from container's 8500)
   - **39001**: Engine HTTP port in dev mode
   - **49001**: Engine gRPC port in dev mode

3. **Critical Configuration**:
   - `.env` file controls ports and host settings
   - `ENGINE_HOST=host.docker.internal` allows Consul health checks to reach the host
   - `QUARKUS_PROFILE=dev` overrides any environment settings

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Host Machine                               │
│                                                                     │
│  ┌─────────────────┐         ┌──────────────────┐                   │
│  │ Consul Server   │◄────────│ Consul Agent     │                   │
│  │ (Container)     │  join   │ (Container)      │                   │
│  │ Internal 8500   │         │ 0.0.0.0:8501:8500│                   │
│  └─────────────────┘         └──────────────────┘                   │
│           ▲                           ▲                             │
│           │                           │ localhost:8501              │
│           │                           │                             │
│           │                    ┌──────────────┐                     │
│           │                    │   Quarkus    │                     │
│           │                    │   Engine     │                     │
│           │                    │  Port 39001  │                     │
│           └────────────────────┤  Port 49001  │                     │
│                  Bridge Network└──────────────┘                     │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## Implementation in build.gradle.kts

```kotlin
tasks.register("startConsulDev") {
    doLast {
        // Check port availability
        try {
            ServerSocket(8501).close()
        } catch (e: Exception) {
            throw GradleException("Port 8501 is already in use!")
        }

        // Create bridge network
        val network = Network.newNetwork()

        // 1. Start Consul server
        val consulServer = ConsulContainer(DockerImageName.parse("hashicorp/consul:1.21"))
            .withCommand("agent", "-server", "-ui", "-client=0.0.0.0", 
                        "-bind=0.0.0.0", "-bootstrap-expect=1", "-dev")
            .withNetwork(network)
            .withNetworkAliases("consul-server")
            .withEnv("CONSUL_BIND_INTERFACE", "eth0")
        
        consulServer.start()

        // 2. Seed configuration
        Thread.sleep(2000)
        consulServer.execInContainer("consul", "kv", "put", "config/application", applicationConfig)
        consulServer.execInContainer("consul", "kv", "put", "config/dev", devConfig)
        consulServer.execInContainer("consul", "kv", "put", "config/krick-local", devConfig)

        // 3. Start Consul agent (sidecar)
        val consulAgent = GenericContainer(DockerImageName.parse("hashicorp/consul:1.21"))
            .withCommand("agent", "-node=engine-sidecar-dev", "-client=0.0.0.0",
                        "-bind=0.0.0.0", "-retry-join=consul-server")
            .withNetwork(network)
            .withNetworkAliases("consul-agent-engine")
            .withEnv("CONSUL_BIND_INTERFACE", "eth0")
            .withExposedPorts(8500)
            .withExtraHost("host.docker.internal", "host-gateway")  // Critical!
            
        consulAgent.setPortBindings(listOf("8501:8500"))
        consulAgent.start()
    }
}

tasks.named<QuarkusDev>("quarkusDev") {
    dependsOn("startConsulDev")
}
```

## Critical Configuration Files

### .env
```properties
CONSUL_HOST=localhost
CONSUL_PORT=8501
ENGINE_HOST=host.docker.internal
QUARKUS_PROFILE=dev
QUARKUS_HTTP_HOST=0.0.0.0
QUARKUS_HTTP_PORT=39001
```

### application.properties
```properties
# Dev profile Consul configuration
%dev.quarkus.consul-config.enabled=true
%dev.quarkus.consul-config.agent.host-port=localhost:8501
%dev.quarkus.consul-config.properties-value-keys=config/application,config/dev
%dev.quarkus.consul-config.fail-on-missing-key=false
```

## Why This Works

1. **Bridge Networking**: Both Consul containers share a Docker network, allowing them to communicate
2. **Port Mapping**: Agent maps container port 8500 to host port 8501, avoiding conflicts
3. **Extra Host**: `withExtraHost("host.docker.internal", "host-gateway")` allows the agent to reach back to the host for health checks
4. **Proper Registration**: `ENGINE_HOST=host.docker.internal` ensures services register with an address Docker can reach

## Common Issues and Solutions

### Issue: "Connection refused: localhost/127.0.0.1:8500"
**Cause**: Quarkus trying to connect to default port 8500 instead of 8501
**Solution**: Ensure .env has `CONSUL_PORT=8501` and dev profile is active

### Issue: "Key 'config/krick-local' not found"
**Cause**: Profile-specific configuration key missing
**Solution**: Either seed the key or set `QUARKUS_PROFILE=dev`

### Issue: Health checks failing
**Cause**: Consul can't reach the engine from inside Docker
**Solution**: Set `ENGINE_HOST=host.docker.internal` in .env

### Issue: Port 8080 exposed instead of 39001
**Cause**: .env overrides the dev profile settings
**Solution**: Set `QUARKUS_HTTP_PORT=39001` in .env

## Running Dev Mode

```bash
# Clean up any existing containers
docker stop $(docker ps -q --filter ancestor=hashicorp/consul:1.21) && \
docker rm $(docker ps -aq --filter ancestor=hashicorp/consul:1.21)

# Start dev mode
QUARKUS_PROFILE=dev ./gradlew :engine:pipestream:quarkusDev
```

## Benefits

1. **Zero Manual Setup**: No need to start Consul manually
2. **Production-like**: Uses the same sidecar pattern as production
3. **Isolated**: Dedicated Consul instance for development
4. **Auto-cleanup**: Consul properly deregisters services
5. **Debuggable**: Full Consul UI available at http://localhost:8501

## Future Enhancements

1. **Module Auto-start**: Automatically start configured modules in dev mode
2. **Data Persistence**: Option to persist Consul data between restarts
3. **Windows Support**: Alternative networking solution for Windows
4. **Multi-instance**: Support multiple dev instances on different ports