# Rokkon Development Guide

## Quick Start

### 1. Start the Engine (with everything)
```bash
./dev.sh
```

This single command will:
- ✅ Start Consul in Docker
- ✅ Seed Consul with initial configuration
- ✅ Build all necessary components
- ✅ Start the engine in Quarkus dev mode with live reload

**Engine Endpoints:**
- Dashboard: http://localhost:38082/
- Health: http://localhost:38082/q/health
- OpenAPI: http://localhost:38082/q/swagger-ui
- Consul UI: http://localhost:8500/ui

### 2. Start a Module (in another terminal)
```bash
./dev-module.sh echo
```

Available modules:
- `echo` - Simple echo module (port 49091)
- `chunker` - Document chunking (port 49092)
- `parser` - Document parsing (port 49093)
- `embedder` - Embedding generation (port 49094)
- `test-module` - Testing module (port 49090)

### 3. Register the Module
Once both engine and module are running, use the CLI:

```bash
cd cli/register-module
./gradlew run --args="--host localhost --port 48082 --module-name echo --module-host localhost --module-port 49091"
```

## Port Convention

**NEVER use port 8080!** All ports follow this pattern:
- HTTP ports: `3xxxx` (e.g., 38082 for engine)
- gRPC ports: `4xxxx` (e.g., 48082 for engine)

## Testing

### Run Unit Tests
```bash
# All tests
./gradlew test

# Specific module
./gradlew :modules:echo:test
```

### Run Integration Tests
```bash
./gradlew integrationTest
```

## Common Tasks

### Check Engine Health
```bash
curl http://localhost:38082/q/health
```

### View Registered Modules in Consul
1. Open http://localhost:8500/ui
2. Navigate to Services
3. Look for `module-*` entries

### Create a Cluster (via UI)
1. Open http://localhost:38082/
2. Click on "Clusters" tab
3. Click "Create Cluster"
4. Enter cluster name (lowercase, hyphens only)

### Stop Everything
1. Press `Ctrl+C` in the terminal running `dev.sh`
2. Stop Consul: `docker stop rokkon-consul-dev`

## Troubleshooting

### Engine won't start
- Check if Consul is running: `docker ps | grep consul`
- Check if ports are already in use: `lsof -i :38082`

### Module registration fails
- Ensure both engine and module are running
- Check the module's gRPC health endpoint
- Verify the host is reachable from the engine

### Tests failing
- For unit tests: Should work without any external dependencies
- For integration tests: May need Docker running

## Development Workflow

1. **Start the engine** with `./dev.sh`
2. **Make code changes** - they'll auto-reload
3. **Run tests** continuously in another terminal
4. **Add modules** as needed with `./dev-module.sh`
5. **Test the pipeline** via the UI or API

## Notes

- All configuration is in Consul - check http://localhost:8500/ui
- Logs show in the terminal where you run the dev scripts
- Use `QUARKUS_LOG_LEVEL=DEBUG` for more verbose logging
- The engine creates a "default" cluster on first UI access