# Docker Compose Infrastructure with Consul Sidecar Pattern

## Summary
This PR implements a complete Docker Compose infrastructure with Consul Connect sidecar pattern for all Rokkon modules. All 5 modules (echo, test-module, parser, chunker, embedder) are now successfully deployed and registered with the engine.

## Key Achievements
- ✅ Renamed `rokkon` command to `pipeline` across entire codebase
- ✅ Deployed all modules with proper memory allocation
- ✅ Implemented Consul Connect sidecar pattern for service mesh
- ✅ Fixed module registration and health check issues
- ✅ Created comprehensive Docker Compose configurations

## Detailed Changes

### 1. Command Renaming (`rokkon` → `pipeline`)

#### Modified Files:
- `modules/echo/src/main/bash/module-entrypoint.sh`
- `modules/test-module/src/main/bash/module-entrypoint.sh`
- `modules/parser/src/main/bash/module-entrypoint.sh`
- `modules/chunker/src/main/bash/module-entrypoint.sh`
- `modules/embedder/src/main/bash/module-entrypoint.sh`

All entrypoint scripts now use `pipeline register` instead of `rokkon register`.

### 2. Docker Build Configuration Updates

#### CLI JAR Renaming:
- `modules/echo/src/main/docker/Dockerfile.jvm`
- `modules/test-module/src/main/docker/Dockerfile.jvm`
- `modules/parser/src/main/docker/Dockerfile.jvm`
- `modules/chunker/src/main/docker/Dockerfile.jvm`
- `modules/embedder/src/main/docker/Dockerfile.jvm`

All Dockerfiles now:
- Copy `pipeline-cli.jar` instead of `register-module-cli.jar`
- Create `/deployments/pipeline` wrapper script
- Add pipeline to PATH for easy execution

#### Build Script Updates:
- `modules/chunker/build.gradle.kts` - Added copyDockerAssets task to rename CLI JAR
- `modules/embedder/build.gradle.kts` - Added copyDockerAssets task to rename CLI JAR

### 3. Module Deployments

#### Chunker Module:
- **Ports**: HTTP 39092, gRPC 49092
- **Memory**: 4GB limit, 2GB reservation
- **Image**: `pipeline/chunker:latest`
- **Fixed**: Health check implementation in `ChunkerServiceImpl.java`
- **Files Modified**:
  - `modules/chunker/src/main/java/com/rokkon/pipeline/modules/chunker/service/ChunkerServiceImpl.java`
  - `modules/chunker/src/main/resources/application.yml`
  - `docker/compose/docker-compose.chunker-module.yml` (new)

#### Embedder Module:
- **Ports**: HTTP 39094, gRPC 49094 (fixed conflict with parser)
- **Memory**: 8GB limit, 4GB reservation
- **Image**: `pipeline/embedder:latest`
- **Special**: Extended startup delay (20s) for ML model loading
- **Files Modified**:
  - `modules/embedder/src/main/resources/application.yml`
  - `docker/compose/docker-compose.embedder-module.yml` (new)

### 4. Docker Compose Infrastructure

#### New Files Created:
- `docker/compose/docker-compose.yml` - Base infrastructure (Consul + Engine)
- `docker/compose/docker-compose.echo-module.yml` - Echo module deployment
- `docker/compose/docker-compose.test-module.yml` - Test module deployment
- `docker/compose/docker-compose.parser-module.yml` - Parser module deployment
- `docker/compose/docker-compose.chunker-module.yml` - Chunker module deployment
- `docker/compose/docker-compose.embedder-module.yml` - Embedder module deployment
- `docker/compose/docker-compose.all-modules.yml` - Full stack deployment

#### Updated Files:
- `docker/compose/.env` - Added CHUNKER and EMBEDDER configuration
- `docker-compose-helper.sh` - Added support for new modules

### 5. Memory Configuration

All modules now have proper memory allocation:

| Module | Memory Limit | Memory Reservation | JVM Options |
|--------|-------------|--------------------|-------------|
| Echo | 1GB | 512MB | `-Xmx1g -Xms512m` |
| Test | 1GB | 512MB | `-Xmx1g -Xms512m` |
| Parser | 1GB | 512MB | `-Xmx1g -Xms512m` |
| Chunker | 4GB | 2GB | `-Xmx4g -Xms1g` |
| Embedder | 8GB | 4GB | `-Xmx8g -Xms2g` |

### 6. Service Registration Improvements

#### Fixed Issues:
- Module registration now uses correct gRPC ports
- Health check fields properly set in ServiceRegistrationResponse
- Fixed host resolution (0.0.0.0 → proper container hostname)
- Increased retry attempts for slow-starting modules

#### Pattern Implemented:
1. Module starts with HTTP and gRPC ports
2. Consul sidecar provides network namespace
3. Module registers with engine via gRPC
4. Health checks validate module readiness
5. Service discovery through Consul

### 7. Port Allocation

Consistent port numbering scheme:
- Engine: HTTP 39000, gRPC 49000
- Echo: HTTP 39090, gRPC 49090
- Test: HTTP 39095, gRPC 49095
- Parser: HTTP 39093, gRPC 49093
- Chunker: HTTP 39092, gRPC 49092
- Embedder: HTTP 39094, gRPC 49094

### 8. Consul Sidecar Pattern

Each module follows the same pattern:
```yaml
consul-agent-[module]:
  # Consul agent providing network namespace
  ports:
    - "HTTP_PORT:HTTP_PORT"
    - "GRPC_PORT:GRPC_PORT"

[module]:
  # Application using sidecar's network
  network_mode: "service:consul-agent-[module]"
```

## Testing Performed

- [x] All modules build successfully with `./gradlew build`
- [x] Docker images created for all modules
- [x] `docker-compose -f docker-compose.all-modules.yml up` starts entire stack
- [x] All modules register successfully with engine
- [x] Health checks pass for all modules
- [x] Dashboard at http://localhost:39000 shows all modules correctly
- [x] Module dashboard at http://localhost:39000/api/v1/modules/dashboard shows proper categorization

## Verification Steps

1. Clean build and Docker image creation:
   ```bash
   ./gradlew clean build
   ./gradlew dockerBuild
   ```

2. Start full stack:
   ```bash
   cd docker/compose
   docker-compose -f docker-compose.all-modules.yml up
   ```

3. Verify all modules registered:
   ```bash
   curl http://localhost:39000/api/v1/modules/dashboard | jq
   ```

## Next Steps

- [ ] Create generic module registration pattern for Java and non-Java modules
- [ ] Implement reusable REST component based on getServiceRegistration()
- [ ] Setup quarkusDev configuration for development
- [ ] Test live pipeline execution with deployed modules
- [ ] Create predefined test pipelines for TESTING mode

## Breaking Changes

- The `rokkon` CLI command is now `pipeline` - all scripts and documentation need updates

## Notes

- All modules use JVM uber-jar deployment for consistency
- Consul sidecars handle service mesh connectivity
- Memory allocations optimized for module workloads (NLP/ML for chunker/embedder)
- Dashboard categorization bug fixed - modules no longer appear in base_services