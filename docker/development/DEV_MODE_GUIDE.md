# Quarkus Dev Mode Guide

## Overview
This guide explains how to run the Rokkon engine in Quarkus dev mode for rapid development with hot reload capabilities.

## Architecture
- **Consul + Modules**: Run in Docker containers
- **Engine**: Runs locally in Quarkus dev mode
- **Key Point**: Modules register with engine at `host.docker.internal:49000`

## Quick Start

### 1. Start Everything (Recommended)
```bash
cd docker/development
./dev-quarkus.sh
```

This will:
- Start Consul and all modules in Docker
- Start engine in Quarkus dev mode with hot reload
- Engine will be available at http://localhost:39000

### 2. Start Only Modules
```bash
./dev-quarkus.sh --modules-only
```

Then run engine manually:
```bash
cd engine/pipestream
./gradlew quarkusDev -Dquarkus.profile=dev-docker
```

### 3. Start Only Essential Modules (Faster)
```bash
./dev-quarkus.sh --minimal
```

This starts only echo and test modules (skips chunker/embedder for faster startup).

### 4. Stop Everything
```bash
./dev-quarkus.sh --stop
```

## Development Workflow

### Hot Reload
1. Make changes to engine code
2. Save the file
3. Quarkus automatically recompiles and reloads
4. Test changes immediately - no restart needed!

### Access Points
- **Engine Dashboard**: http://localhost:39000
- **Quarkus Dev UI**: http://localhost:39000/q/dev
- **Consul UI**: http://localhost:8500
- **Module Dashboard**: http://localhost:39000/api/v1/modules/dashboard

### Testing Pipeline Creation

### Quick Pipeline Creation (Dev Mode)
```bash
# 1. Check dev status
curl http://localhost:39000/api/v1/dev/status | jq

# 2. See available sample pipelines
curl http://localhost:39000/api/v1/dev/sample-pipelines | jq

# 3. Create a simple echo pipeline
curl -X POST http://localhost:39000/api/v1/dev/quick-pipeline \
  -H "Content-Type: application/json" \
  -d '{"name":"echo-test","modules":["echo"]}'

# 4. Create a multi-step pipeline
curl -X POST http://localhost:39000/api/v1/dev/quick-pipeline \
  -H "Content-Type: application/json" \
  -d '{"name":"echo-parser","modules":["echo","parser"]}'

# 5. Test the pipeline with data
curl -X POST http://localhost:39000/api/v1/dev/test-pipeline/echo-test \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello from dev mode!"}'
```

### Traditional Pipeline Creation
For more control, use the standard APIs:
```bash
# Check modules are registered
curl http://localhost:39000/api/v1/modules/dashboard | jq

# Create pipeline definition (see /api/v1/pipeline-definitions docs)
# Deploy pipeline (see /api/v1/clusters/{cluster}/pipelines docs)
```

## Troubleshooting

### Modules not registering?
1. Check Consul is running: http://localhost:8500
2. Check module logs: `docker logs echo-module-dev`
3. Ensure engine is running on port 49000

### Port conflicts?
```bash
# Check what's using ports
lsof -i :39000  # Engine HTTP
lsof -i :49000  # Engine gRPC
lsof -i :8500   # Consul
```

### Engine won't start?
1. Stop everything: `./dev-quarkus.sh --stop`
2. Clean build: `cd engine/pipestream && ./gradlew clean`
3. Try again

## Configuration

### Engine Dev Profile
The dev configuration is in `engine/pipestream/src/main/resources/application-dev-docker.yml`

### Module Configuration
Modules use `ENGINE_HOST=host.docker.internal` to connect to the host machine's engine.

## Tips

1. **Use minimal mode** for faster startup during development
2. **Check Quarkus Dev UI** at http://localhost:39000/q/dev for:
   - Configuration values
   - Bean inspection
   - REST endpoint testing
3. **Enable debug logging** by adding to application-dev-docker.yml:
   ```yaml
   quarkus:
     log:
       category:
         "com.rokkon.pipeline.engine": TRACE
   ```

## Next Steps
1. Create pipeline definitions via REST API
2. Test pipeline execution
3. Monitor data flow through the dashboard
4. Add new REST endpoints as needed