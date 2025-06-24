# Module Registration QA Notes

## Expected Module Registration Flow

### Prerequisites

1. **Consul Configuration**
   - Consul must have `config/application` key populated
   - Engine looks for configuration at:
     - `/config/application` (base config)
     - `/config/${quarkus.profile}` (profile-specific)

2. **Engine Configuration**
   - HTTP Port: 8080 (internal) → 18080 (host exposed)
   - gRPC Port: 48081 (both internal and host exposed)
   - Consul connection: `consul:8500` (Docker network)

3. **Module Configuration**
   - Module gRPC port: 9090 (internal)
   - Engine connection: `engine:48081` (Docker network)

### Expected Registration Flow

1. **Module Startup**
   - Module starts embedded CLI with RegisterCommand
   - CLI attempts to connect to engine at `ENGINE_HOST:ENGINE_PORT`
   - CLI calls gRPC `registerModule()` method

2. **Engine Processing**
   - Engine gRPC service receives registration request
   - Should log: "Received module registration request"
   - Stores module info in Consul service registry
   - Returns success response with instance ID

3. **Consul Storage**
   - Module registered as Consul service with health check
   - Service name format: `module-{moduleName}`
   - Health check: gRPC health check on module port

## Deployment Scenarios

### Scenario 1: Full Docker Stack

**Docker Network**: `rokkon-network`

**Services**:
- consul (hashicorp/consul:1.21.1)
- engine (rokkon/engine:latest)
- test-module (rokkon/test-module:latest)

**Port Mappings**:
```
consul:      8500:8500 (HTTP API)
engine:      18080:8080 (HTTP Dashboard)
             48081:48081 (gRPC)
test-module: No external ports (internal only)
```

### Scenario 2: Local Consul/Engine + Docker Modules

**Nginx Configuration** (`/home/krickert/IdeaProjects/rokkon/rokkon-pristine/integration-test/nginx-proxy.conf`):
```nginx
# Consul HTTP API proxy
server {
    listen 8500;
    location / {
        proxy_pass http://host.docker.internal:8500;
    }
}

# Engine HTTP Dashboard proxy
server {
    listen 8080;
    location / {
        proxy_pass http://host.docker.internal:8080;
    }
}

# Engine gRPC proxy
server {
    listen 48081 http2;
    location / {
        grpc_pass grpc://host.docker.internal:48081;
    }
}
```

## Observed Behavior

### Engine Startup

**What I see in logs**:
```
java.lang.RuntimeException: Key 'config/application' not found in Consul
```

**Issue**: Engine fails immediately due to missing Consul configuration.

**Where I looked**:
- `docker logs rokkon-engine`
- Engine expects consul-config but it's not populated

### Module Registration Attempt

**What I see in test-module logs**:
```
Registering module with engine (attempt 1/5)...
Started gRPC server on 0.0.0.0:49095
Profile krick-local activated
```

**Issues**:
1. Module is using wrong profile (`krick-local` instead of `prod`)
2. Module gRPC port is 49095 instead of expected 9090
3. No registration success/failure messages

### Missing Components

**In engine logs**:
- No "Started gRPC server" message
- No gRPC in installed features list
- Only shows: `[cdi, config-yaml, consul-config, hibernate-validator, rest, rest-jackson, scheduler, smallrye-context-propagation, smallrye-health, smallrye-openapi, swagger-ui, vertx]`

**Where I looked**:
- `/engine/consul/src/main/java/com/rokkon/pipeline/consul/grpc/` - No gRPC services found
- `/rokkon-engine/src/main/java/com/rokkon/pipeline/engine/grpc/ModuleRegistrationServiceImpl.java` - gRPC service exists here but not in consul module

## Configuration Issues Found

1. **Profile Mismatch**
   - Engine: `QUARKUS_PROFILE=prod`
   - Test Module: Shows `Profile krick-local activated`

2. **Port Configuration**
   - Test module expects to register on engine:48081
   - Engine configuration shows gRPC should be on 48081
   - But no gRPC server starts in engine

3. **Missing gRPC Service**
   - Engine (consul module) has `quarkus-grpc` dependency
   - Engine has gRPC configuration in application.yml
   - But no `@GrpcService` classes to trigger gRPC server startup

## Root Cause Analysis

The module registration is failing because:

1. **Immediate failure**: Missing Consul configuration causes engine to crash on startup
2. **Underlying issue**: Even if Consul config existed, the gRPC registration service is not present in the consul module that's being run as the engine

The gRPC service implementation exists in the `rokkon-engine` module but the Docker image is built from the `consul` module, which lacks this service.

## Configuration Seeding

### Expected Seeding Process

1. **Seed-config Tool**
   - Located at `/engine/seed-config/`
   - Should run: `java -jar seed-config.jar -e prod -f config-file.yml`
   - Connects to Consul at specified host:port
   - Writes configuration to Consul KV store

2. **Expected Consul Keys**
   - `/config/application` - Base configuration
   - `/config/prod` - Production profile overrides
   - `/config/krick-local` - Local development profile

### Observed Seeding Attempts

**Command Used**:
```bash
cd engine/seed-config && ./gradlew run --args="-e prod -c localhost:8500 -f ../consul/src/main/resources/application.yml"
```

**Result**: Command appeared to complete but engine still failed with "Key 'config/application' not found"

**Where I looked**:
- Consul UI at http://localhost:8500 - Would need to verify keys were created
- Engine logs still show missing configuration

## Docker Configuration Details

### Docker Compose Environment Variables

**Engine Service**:
```yaml
environment:
  QUARKUS_PROFILE: "prod"
  QUARKUS_CONSUL_CONFIG_AGENT_HOST_PORT: "consul:8500"
  CONSUL_HOST: "consul"
  CONSUL_PORT: 8500
  QUARKUS_HTTP_HOST: "0.0.0.0"
  QUARKUS_HTTP_PORT: 8080
  QUARKUS_GRPC_SERVER_HOST: "0.0.0.0"
  QUARKUS_GRPC_SERVER_PORT: 48081
  JAVA_OPTS: "-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
```

**Test Module Service**:
```yaml
environment:
  MODULE_HOST: "0.0.0.0"
  MODULE_PORT: 9090
  ENGINE_HOST: "engine"
  ENGINE_PORT: 48081
  CONSUL_HOST: "consul"
  CONSUL_PORT: 8500
  HEALTH_CHECK: "true"
  MAX_RETRIES: 5
  STARTUP_TIMEOUT: 60
  CHECK_INTERVAL: 5
```

### Docker Network Issues Observed

1. **Network Warning**: "a network with name rokkon-network exists but was not created for project integration-test"
2. **Container Dependencies**: Engine waits for Consul health check, test-module waits for engine health check
3. **Health Check Failure**: Engine health check fails, preventing test-module from starting

## Detailed Log Analysis

### Successful Consul Connection (from previous session)
```
2025-06-24 03:16:00,166 INFO  [com.rok.pip.con.con.ConsulConnectionManager] Initial Consul connection established to consul:8500
```

### Engine Startup Failure Pattern
```
ERROR: Failed to start application
java.lang.RuntimeException: Failed to start quarkus
Caused by: java.lang.RuntimeException: Key 'config/application' not found in Consul
```

### Test Module Registration Loop
```
Registering module with engine (attempt 1/5)...
[No further output - connection appears to fail]
```

### Missing gRPC Server Startup
**Expected log entry**: `Started gRPC server on 0.0.0.0:48081`
**Actual**: No such log entry in engine logs

## Problems Encountered

1. **Consul Configuration Bootstrap**
   - Engine requires configuration before it can start
   - Chicken-and-egg problem: Need engine to manage configs, but engine needs config to start

2. **Profile Configuration Confusion**
   - Multiple profiles defined: prod, dev, krick-local, docker-dev, local-dev
   - Test module defaults to krick-local despite Docker compose setting

3. **Port Allocation Issues**
   - Test module starts on random port (49095) instead of configured 9090
   - Suggests MODULE_PORT environment variable not being read

4. **Docker Build Context**
   - Engine Docker image built from consul module: `cd engine/consul && docker build -f src/main/docker/Dockerfile.jvm`
   - Dockerfile copies from `build/quarkus-app/` but gRPC service not included

## Verification Commands Used

```bash
# Check running containers
docker ps -a

# Check engine logs
docker logs rokkon-engine

# Check test module logs  
docker logs rokkon-test-module

# Check network
docker network ls | grep rokkon

# Check consul
curl -s http://localhost:8500/v1/kv/?keys
```

## Next Steps for QA Verification

1. Seed Consul configuration to allow engine startup
2. Verify if gRPC server starts after successful engine startup
3. Trace where the gRPC service implementation should be located
4. Verify module can connect to engine gRPC endpoint
5. Confirm registration data is stored in Consul