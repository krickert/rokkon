# Quarkus Dev Mode - Context and Progress Document

## Quick Context for New Sessions

### The Goal
Create a zero-setup development environment for the Pipeline system where `./gradlew quarkusDev` automatically starts:
- Consul (service discovery)
- Consul configuration with seeding
- Engine consul sidecar
- Two default modules: echo and test-module

### Key Discovery
Quarkus has built-in Docker Compose support that auto-detects `docker-compose-dev-service.yml` files and manages container lifecycle. We're leveraging this instead of writing custom Docker management code.

### Current Approach
1. Use Quarkus's compose dev services for infrastructure
2. Keep it simple - no custom Docker management code initially
3. Add complexity only as needed

## Implementation Phases

### Phase 1: Basic Infrastructure (CURRENT PHASE)
**Goal**: Get Quarkus to auto-start our Docker infrastructure

**Tasks**:
- [x] Create docker-compose-dev-service.yml in project root
- [x] Configure dev ports in application-dev.yml (39001/49001)
- [x] Manual test of compose file works
- [ ] Test auto-detection with `./gradlew quarkusDev`
- [x] Verify consul accessible on both ports (8500, 8501)
- [ ] Get modules starting and registering

**Success Criteria**: 
- Running quarkusDev starts everything automatically
- No manual docker commands needed
- Modules are accessible and registered in Consul

### Phase 2: Configuration Tuning
**Goal**: Optimize the dev experience

**Tasks**:
- [ ] Add Quarkus compose configuration to application.properties if needed
- [ ] Ensure containers persist between restarts
- [ ] Configure logging appropriately
- [ ] Add health check optimizations
- [ ] Document any ENGINE_HOST_IP issues/solutions

### Phase 3: Dynamic Module Management (FUTURE)
**Goal**: Add ability to start/stop additional modules on demand

**Tasks**:
- [ ] Design REST API for module control
- [ ] Implement start/stop for non-default modules (parser, chunker, embedder)
- [ ] Add module status endpoints
- [ ] Create simple CLI commands

### Phase 4: Dev UI Integration (FUTURE)
**Goal**: Visual module management in Quarkus Dev UI

**Tasks**:
- [ ] Create Quarkus Dev UI extension
- [ ] Add module status cards
- [ ] Implement start/stop buttons
- [ ] Show real-time health status

## Current Status Summary

### Completed:
- ✅ Research on Quarkus compose dev services
- ✅ Created clean design document (`TEST_DESIGN/QUARKUS_DEV_MODE_DESIGN.md`)
- ✅ Docker client dependency added (build.gradle.kts)
- ✅ Dev ports configured (application-dev.yml)
- ✅ Architectural plan for Dev UI extensions (`ARCHITECTURE/Quarkus_Dev_UI_Extensions.md`)
- ✅ Architectural plan for Main Frontend (`ARCHITECTURE/Main_Frontend_Architecture.md`)

### In Progress:
- 🔄 Creating `docker-compose-dev-service.yml`
- 🔄 Testing Quarkus auto-detection
- 🔄 Implementing Dev UI extensions and backend endpoints for dev operations

### Blocked/Waiting:
- None currently

## Key Files and Locations

### Design Documents:
- `/TEST_DESIGN/QUARKUS_DEV_MODE_DESIGN.md` - Current design
- `/TEST_DESIGN/IMPLEMENTATION_STATUS.md` - Detailed status
- `/TEST_DESIGN/CONTEXT_AND_PROGRESS.md` - This file

### Implementation Files:
- `docker-compose-dev-service.yml` - Project root (Quarkus auto-detects here)
- `engine/pipestream/src/main/resources/application-dev.yml` - Dev configuration
- `engine/pipestream/build.gradle.kts` - Has Docker client dependency

### Old Reference (archived):
- `/TEST_DESIGN/OLD_REFERENCE/` - Previous design iterations

## Important Technical Details

### Quarkus Compose File Detection:
- Pattern: `(^docker-compose|^compose)(-dev(-)?service).*.(yml|yaml)`
- Location: Project root
- Auto-starts when: `quarkus.compose.devservices.enabled=true` (default)

### Port Configuration:
- Production: 39000 (HTTP), 49000 (gRPC)
- Development: 39001 (HTTP), 49001 (gRPC)
- Consul: 8500 (main), 8501 (engine sidecar)

### Important Naming Note:
- We're transitioning from "rokkon" to "pipeline" in the codebase
- Docker images still use "rokkon" prefix (e.g., `rokkon/echo-module:latest`)
- New code should use "pipeline" naming
- Container names use "pipeline" prefix to avoid conflicts

### Network Configuration:
- Modules use `network_mode: "service:sidecar-name"`
- Engine connects to consul via localhost:8501
- Modules connect to engine via ENGINE_HOST_IP

## Common Commands

```bash
# Start dev mode
./gradlew :engine:pipestream:quarkusDev

# Check running containers
docker ps | grep dev

# View Consul services
curl http://localhost:8500/v1/catalog/services

# Clean up everything
docker compose -p pipeline-dev down -v

# Check module health
curl http://localhost:39001/api/v1/modules
```

## Known Issues and Solutions

### Issue: ENGINE_HOST_IP detection
- Solution: Use `host.docker.internal` for Docker Desktop
- Fallback: Set ENGINE_HOST_IP environment variable

### Issue: Containers not starting
- Check: Docker is running
- Check: Ports are available (especially 8500, 8501, 39001, 49001)
- Check: Images exist locally (`docker images | grep pipeline`)

## Next Session Instructions

When starting a new session:
1. Read this document first
2. Check current phase status
3. Run `docker ps | grep dev` to see current state
4. Continue with uncompleted tasks in current phase

## Recovery Commands

If context is lost:
```bash
# See this document
cat TEST_DESIGN/CONTEXT_AND_PROGRESS.md

# Check implementation status
cat TEST_DESIGN/IMPLEMENTATION_STATUS.md

# View current design
cat TEST_DESIGN/QUARKUS_DEV_MODE_DESIGN.md

# Check what's running
docker ps | grep dev
```