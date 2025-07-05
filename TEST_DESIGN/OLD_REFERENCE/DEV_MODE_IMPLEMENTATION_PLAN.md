# Dev Mode Implementation Plan - Phased Approach

## Overview
This document outlines a phased implementation strategy for the Dev Mode design, with clear milestones, measurable outcomes, and context preservation strategies.

## Implementation Phases

### Phase 0: Prerequisites & Foundation
**Goal**: Set up Quarkiverse Docker Client and verify local registry

**Tasks**:
1. Add Quarkiverse Docker Client dependency to engine build.gradle
2. Verify local Docker registry is operational
3. Check for port conflicts (39000, 49000, 8501)
4. Backup existing application-dev.yml (if exists) and create new one
5. Document integration with existing docker-compose test environment
6. Create initial dev mode package structure
7. Verify module images are available in local registry (using :latest)

**Measurable Outcomes**:
- [ ] Quarkiverse Docker Client dependency added and verified
- [ ] Docker ping test passes
- [ ] Port availability verified
- [ ] Local registry accessible and contains required images
- [ ] Dev mode package structure created at `com.rokkon.pipeline.engine.dev`
- [ ] Old application-dev.yml backed up, new one created
- [ ] Integration points with existing test infrastructure documented

**Testing Checkpoint**:
```bash
# Verify Docker is running
docker info
# Check local registry
curl http://nas.rokkon.com:5000/v2/_catalog
# Verify images exist
docker images | grep pipeline
# Check ports are available (dev mode ports)
lsof -i :39001 || echo "Port 39001 available"
lsof -i :49001 || echo "Port 49001 available"  
lsof -i :8501 || echo "Port 8501 available"
lsof -i :8502 || echo "Port 8502 available"
```

---

### Phase 1: Foundation - Docker Client & Host IP Detection
**Goal**: Establish core infrastructure for Docker interaction

**Tasks**:
1. Implement Docker availability check in `@PostConstruct`
2. Create `PipelineModule` enum with basic module metadata
3. Implement host IP detection logic with fallbacks
4. Create basic `PipelineDevModeInfrastructure` class (no compose yet)
5. Write unit tests for host IP detection
6. Add memory usage monitoring foundation

**Measurable Outcomes**:
- [ ] Docker client successfully injected in dev profile
- [ ] Host IP detection returns valid IP on test machine
- [ ] Unit tests pass for all three IP detection methods

**Context Preservation**:
```yaml
# Add to CLAUDE.md after completion:
## Dev Mode Implementation Status
- Phase 1: Foundation ✅
  - Docker client integrated
  - Host IP detection working
  - PipelineModule enum created
```

---

### Phase 2: Basic Infrastructure Start/Stop
**Goal**: Start/stop core infrastructure without modules

**Tasks**:
1. Create enhanced docker-compose.dev.yml with engine sidecar
2. Implement `startDevInfrastructure()` for core services only
3. Add infrastructure health checks
4. Implement `isInfrastructureRunning()` detection
5. Test manual infrastructure lifecycle

**Measurable Outcomes**:
- [ ] `./gradlew quarkusDev` starts Consul + engine sidecar
- [ ] Engine connects to localhost:8501 successfully
- [ ] Infrastructure detection works correctly
- [ ] Engine can register with Consul via sidecar

**Testing Checkpoint**:
```bash
# Verify infrastructure
docker ps | grep pipeline-dev
curl http://localhost:8500/v1/catalog/services
curl http://localhost:39000/q/health
```

---

### Phase 3: Module Management Core
**Goal**: Start/stop modules programmatically

**Tasks**:
1. Implement Docker-Java based container management
2. Add `startContainer()` and `stopContainer()` methods
3. Implement `executeDockerComposeViaAPI()` for missing containers
4. Add module health checking logic
5. Test with echo module only

**Measurable Outcomes**:
- [ ] Can start echo module via Docker API
- [ ] Can stop echo module via Docker API
- [ ] Module health check reports correctly
- [ ] No ProcessBuilder usage

**Testing Script**:
```java
// Add to a test endpoint temporarily
infrastructure.startModule(PipelineModule.ECHO);
Thread.sleep(5000);
boolean running = infrastructure.isModuleRunning(PipelineModule.ECHO);
infrastructure.stopModule(PipelineModule.ECHO);
```

---

### Phase 4: REST API & Dev Endpoints
**Goal**: Expose module management via REST

**Tasks**:
1. Create `DevModuleResource` with basic endpoints
2. Implement `/q/dev/pipeline/modules` listing
3. Add start/stop endpoints
4. Add module status reporting
5. Test via curl commands

**Measurable Outcomes**:
- [ ] GET /q/dev/pipeline/modules returns correct status
- [ ] POST /q/dev/pipeline/modules/{module}/start works
- [ ] POST /q/dev/pipeline/modules/{module}/stop works
- [ ] Module status updates correctly

**API Test Suite**:
```bash
# Create test script: test-dev-api.sh
curl http://localhost:39000/q/dev/pipeline/modules
curl -X POST http://localhost:39000/q/dev/pipeline/modules/echo/start
curl http://localhost:39000/q/dev/pipeline/modules
curl -X POST http://localhost:39000/q/dev/pipeline/modules/echo/stop
```

---

### Phase 5: Auto-start Integration
**Goal**: Zero-setup experience with parallel startup

**Tasks**:
1. Implement `@Observes StartupEvent` handler
2. Add configuration properties for dev mode
3. Implement parallel default module startup with Mutiny
4. Add startup progress reporting
5. Add graceful error handling
6. Support `.env.local` for developer-specific config
7. Test full auto-start flow

**Measurable Outcomes**:
- [ ] `./gradlew quarkusDev` starts everything automatically
- [ ] Default modules (echo, test) start correctly
- [ ] Clear error messages if Docker not running
- [ ] Can disable auto-start via config

**Validation**:
```bash
# Clean start test
docker compose -p pipeline-dev down -v
./gradlew clean quarkusDev
# Should see all infrastructure + 2 modules running
```

---

### Phase 6: Dev UI Integration (Priority Feature)
**Goal**: Primary developer interface with real-time updates

**Tasks**:
1. Create Quarkus Dev UI extension
2. Add module status card with real-time updates (design for reuse in production)
3. Implement start/stop buttons with progress indication
4. Add infrastructure health and memory usage display
5. Implement SSE endpoint for live status streaming
6. Add module log streaming capability
7. Create shared components for future dashboard migration
8. Test UI interactions and responsiveness

**Measurable Outcomes**:
- [ ] Dev UI shows module cards
- [ ] Can start/stop modules via UI
- [ ] Health status updates in real-time
- [ ] Components designed for reuse in production dashboard
- [ ] Errors displayed clearly

**Production Dashboard Migration Notes**:
- Design components with production use in mind
- Keep authentication/authorization hooks separate
- Document which features should migrate to production
- Create shared component library structure

---

### Phase 7: Pipeline Testing Integration
**Goal**: Create and test pipelines easily

**Tasks**:
1. Add sample pipeline creation endpoints
2. Create pipeline test data generators
3. Add pipeline execution helpers
4. Document pipeline testing workflow
5. Create example test scenarios

**Measurable Outcomes**:
- [ ] Can create test pipeline via API
- [ ] Can send test data through pipeline
- [ ] Results visible in logs/UI
- [ ] Documentation complete

---

## Design Decisions & Constraints

### Error Handling Strategy
- **Module Start Failures**: Retry with backoff, don't fail entire dev mode
- **Engine Crashes**: Containers keep running (by design for faster restart)
- **Compose File Validation**: Must be valid YAML, validated in Phase 2

### Image Versioning
- All modules use `:latest` tag in dev mode
- Images come from same project, so version consistency assumed
- Future phase may add image cleanup for outdated versions

### Data Persistence
- Consul data persists between sessions
- Future enhancement: Add `clear-consul` command
- Dangling containers cleaned only if from current dev session

### Port Management
- Port conflicts checked in Phase 0
- Dev mode uses different ports to avoid conflicts with running systems:
  - Engine (dev mode): 39001 (HTTP), 49001 (gRPC) - different from production
  - Engine Consul sidecar: 8501 (avoiding main Consul on 8500)
  - Consul dev server: 8502 (if needed, avoiding 8500)
  - Module ports: Same as defined in compose file (isolated by project name)
- Docker Compose project name: `pipeline-dev` (different from `compose`)

## Context Preservation Strategy

### 1. Update Tracking Document
Create `DEV_MODE_PROGRESS.md`:
```markdown
# Dev Mode Implementation Progress

## Current Phase: [X]
## Completed Phases: [List]
## Next Steps: [Specific tasks]

## Recent Changes:
- [Date]: [What was done]
- [Date]: [What was done]

## Known Issues:
- [Issue description and workaround]

## Test Commands:
[Keep updating with working test commands]
```

### 2. Update CLAUDE.md After Each Phase
```markdown
## Dev Mode Implementation
- Current Status: Phase X of 7
- Working Features: [List what works]
- Known Limitations: [List what doesn't work yet]
- Next: [Next phase goals]
```

### 3. Incremental Git Commits
```bash
# Commit pattern for each phase
git add -A
git commit -m "feat(dev-mode): Phase X - [Description]

- [Specific change 1]
- [Specific change 2]
- Measurable outcome: [What now works]

Next: [What's next]"
```

### 4. Test Suite Evolution
Each phase adds its own tests:
```
src/test/java/.../dev/
├── Phase1HostIPDetectionTest.java
├── Phase2InfrastructureTest.java
├── Phase3ModuleManagementTest.java
├── Phase4RestApiTest.java
└── ...
```

### 5. Regular Checkpoints
After each phase:
1. Run all tests
2. Update documentation
3. Commit changes
4. Tag the commit: `git tag dev-mode-phase-X`
5. Quick smoke test of all previous phases

## Rollback Strategy

If a phase fails:
```bash
# Rollback to last good phase
git checkout dev-mode-phase-[X-1]

# Or selectively revert
git revert [commit-hash]
```

## Success Metrics

### Phase Completion Criteria:
- All measurable outcomes checked ✅
- Tests passing
- Documentation updated
- No regression in previous phases

### Overall Success:
- Dev mode starts in < 30 seconds
- Module operations complete in < 5 seconds
- Zero manual setup required
- Works on Linux, macOS, Windows with Docker Desktop

## Communication During Implementation

### Daily Updates Pattern:
```
"Completed Phase X:
- ✅ [Outcome 1]
- ✅ [Outcome 2]
- ⚠️ [Issue found]: [Workaround]

Starting Phase X+1:
- First task: [Specific task]
- Expected completion: [Estimate]"
```

### Context Loss Recovery:
If context is lost, the implementer should:
1. Check `DEV_MODE_PROGRESS.md`
2. Run `git log --oneline --grep="dev-mode"`
3. Check current phase tests
4. Continue from last incomplete task

This phased approach ensures we can make steady progress without losing work or context during compactions.