# Connector Server Standalone Development Plan

## Overview
This document outlines the phased approach to create a completely independent connector-server that will eventually replace the connector functionality in rokkon-engine. The connector-server will be developed in isolation, tested independently, and only integrated with rokkon-engine after it's fully functional. **The existing rokkon-engine will remain unchanged during development.**

## Architecture Overview

### Current State (Unchanged During Development)
```
Connectors → rokkon-engine (ConnectorEngineImpl) → PipelineExecutor
                    ↓
                 Consul
```

### Development State (New Independent System)
```
Connectors → connector-server → Mock Pipeline (for testing)
     ↓            ↓         
  State DB     Consul     
```

### Final Target State (After Migration)
```
Connectors → connector-server → Pipeline Modules (direct gRPC)
     ↓            ↓         
  State DB     Consul
     
(rokkon-engine only knows connector ID from pipeline context)
```

### Project Structure

#### During Development (New Structure)
```
rokkon-engine-new/
├── connector/       # Standalone connector-server
├── pipestream/      # Mock pipeline for testing (later real pipeline)
└── commons/         # Shared components for new architecture
```

#### After Migration (Final Structure)
```
rokkon-engine-new/
├── connector/       # Connector server (Docker container)
├── pipestream/      # Migrated rokkon-engine without connector code
└── commons/         # Shared components

rokkon-engine/       # Original, unchanged until migration
```

## Key Design Principles

1. **Complete Independence**: The connector-server will be a standalone service with its own Docker container
2. **No rokkon-engine Dependencies**: Will not import or depend on any rokkon-engine code
3. **Direct Pipeline Communication**: Connectors will send documents directly to pipeline steps via gRPC
4. **Connector ID Propagation**: Pipeline context will include connector ID for tracking
5. **Zero Impact Development**: Current rokkon-engine remains untouched until migration

## Development Phases

### Phase 0: Mock Engine Development (See mock-engine-project-plan.md)
**Goal**: Create testing infrastructure for concurrent connector development
**Timeline**: 1 week
**Status**: Priority 1 - Enables parallel development

This phase is documented in detail in `mock-engine-project-plan.md`. The mock engine will:
- Support both ConnectorEngine (legacy) and PipeStepProcessor (new) interfaces
- Provide REST API for test control
- Run as a Docker container
- Enable testing of filesystem-crawler and new connectors

### Phase 1: Project Setup and Build Configuration
**Goal**: Create the new project structure completely separate from existing code

**Tasks**:
1. Create new directory structure:
   ```
   mkdir -p rokkon-engine-new/{connector,pipestream,commons}
   ```
   
2. Create new Gradle modules:
   - `rokkon-engine-new/connector/build.gradle.kts`
   - `rokkon-engine-new/pipestream/build.gradle.kts` (mock for testing)
   - `rokkon-engine-new/commons/build.gradle.kts`
   
3. Configure build files with minimal dependencies:
   - Java 21
   - Quarkus platform BOM
   - rokkon-bom import (for protobuf definitions only)
   
4. Create basic application.yml configurations
5. Update root settings.gradle.kts to include new modules
6. Verify clean compilation with `./gradlew :rokkon-engine-new:connector:build`

**Success Criteria**: New modules compile independently without affecting rokkon-engine

### Phase 2: Docker and Infrastructure Setup
**Goal**: Set up Docker configuration for standalone deployment

**Tasks**:
1. Create Dockerfile for connector-server:
   - Multi-stage build with native compilation option
   - Minimal base image
   - Health check configuration
   
2. Create docker-compose.yml for local development:
   - Connector-server container
   - PostgreSQL for state storage
   - Consul for service registration
   - Mock pipeline service
   
3. Configure container networking
4. Add environment variable configuration
5. Create .dockerignore files

**Success Criteria**: Container builds and runs with docker-compose

### Phase 3: Core Dependencies and Protobuf Setup
**Goal**: Add necessary dependencies for standalone operation

**Tasks**:
1. Add dependencies to connector-server:
   - `io.quarkus:quarkus-grpc`
   - `project(":rokkon-protobuf")` (for proto definitions only)
   - `project(":rokkon-engine-new:commons")`
   - `io.quarkus:quarkus-jdbc-postgresql`
   - `io.quarkus:quarkus-hibernate-orm-panache`
   
2. Configure protobuf scanning in application.yml:
   ```yaml
   quarkus:
     generate-code:
       grpc:
         scan-for-proto: com.rokkon.pipeline:rokkon-protobuf,com.google.api.grpc:proto-google-common-protos
   ```
   
3. Add Consul client for service discovery:
   - `io.vertx:vertx-consul-client`
   - Configure for pipeline module discovery
   
4. Verify protobuf generation works

**Success Criteria**: Dependencies resolve and protobuf classes generate

### Phase 4: Mock Pipeline Service
**Goal**: Create a mock pipeline service for testing connector operations

**Tasks**:
1. In `rokkon-engine-new/pipestream`, create mock PipeStepProcessor service
2. Implement basic request/response handling
3. Add configurable delays and error scenarios
4. Create mock module registrations in Consul
5. Add logging for debugging connector interactions

**Success Criteria**: Mock pipeline service responds to gRPC calls

### Phase 5: Basic Connector Service Implementation
**Goal**: Implement the core ConnectorCoordinator service

**Tasks**:
1. Create main application class with `@QuarkusMain`
2. Implement ConnectorCoordinator gRPC service:
   - Basic structure with all RPC methods
   - Initial request validation
   - Error handling with google.rpc.Status
   
3. Add configuration management:
   - Environment variables
   - application.yml settings
   - Runtime configuration
   
4. Create health check endpoints
5. Add structured logging

**Success Criteria**: Service starts and accepts gRPC requests

### Phase 6: Direct Pipeline Communication
**Goal**: Implement direct communication from connector to pipeline modules

**Tasks**:
1. Create pipeline discovery service:
   - Query Consul for available pipeline modules
   - Cache module endpoints
   - Handle module availability changes
   
2. Implement document forwarding:
   - Create gRPC clients for PipeStepProcessor
   - Add connector ID to ProcessRequest metadata
   - Handle backpressure and flow control
   
3. Add routing logic:
   - Map connector types to initial pipeline steps
   - Support configurable routing rules
   
4. Test with mock pipeline service

**Success Criteria**: Documents flow from connector to mock pipeline

### Phase 7: State Management Implementation
**Goal**: Implement persistent state management for connector operations

**Tasks**:
1. Design database schema for:
   - Connector registrations
   - Resource states and history
   - Sync operations and checkpoints
   - Ledger entries for audit trail
   
2. Create JPA entities with Panache:
   - `ConnectorEntity`
   - `ResourceStateEntity`
   - `SyncOperationEntity`
   - `LedgerEntryEntity`
   
3. Implement repository layer
4. Add Flyway migrations
5. Implement state management in all service methods

**Success Criteria**: State persists across restarts, full CRUD operations work

### Phase 8: Connector Lifecycle and Scheduling
**Goal**: Implement full connector lifecycle management

**Tasks**:
1. Implement connector registration:
   - Validation of capabilities
   - Store in database
   - Register with Consul
   
2. Add Quartz scheduling:
   - Cron-based sync schedules
   - Ad-hoc sync triggers
   - Schedule persistence
   
3. Implement sync planning:
   - Discovery phase (what changed)
   - Execution phase (fetch and forward)
   - Checkpoint management
   
4. Add failure handling:
   - Retry logic with backoff
   - Dead letter queue
   - Circuit breaker per connector

**Success Criteria**: Scheduled syncs execute reliably

### Phase 9: Advanced Features
**Goal**: Implement sophisticated connector management features

**Tasks**:
1. Grace period implementation:
   - Track resource availability
   - Implement soft delete with grace period
   - Automatic purge after expiration
   
2. Orphan detection:
   - Compare source state with our state
   - Flag orphaned resources
   - Configurable cleanup policies
   
3. Deduplication:
   - Content-based hashing
   - Skip duplicate processing
   - Track duplicate statistics
   
4. Rate limiting and quotas:
   - Per-connector rate limits
   - Global system limits
   - Quota enforcement

**Success Criteria**: All advanced features work with test connectors

### Phase 10: Testing and Validation
**Goal**: Comprehensive testing with real-world scenarios

**Tasks**:
1. Create test harness with multiple connector types:
   - File system crawler
   - HTTP poller
   - Event stream consumer
   
2. Integration tests:
   - Full sync lifecycle tests
   - Failure and recovery tests
   - Performance and load tests
   
3. Add test containers for full stack:
   - Connector server
   - PostgreSQL
   - Consul
   - Mock pipeline modules
   
4. Create chaos testing scenarios:
   - Network partitions
   - Service failures
   - Resource exhaustion

**Success Criteria**: All tests pass, system handles failures gracefully

### Phase 11: Monitoring and Production Readiness
**Goal**: Prepare for production deployment

**Tasks**:
1. Add comprehensive metrics:
   - Micrometer metrics for all operations
   - Custom business metrics
   - SLA tracking
   
2. Implement OpenTelemetry tracing:
   - Trace requests through full flow
   - Correlate with pipeline execution
   
3. Create operational tooling:
   - Admin API for connector management
   - CLI tools for debugging
   - Backup and restore procedures
   
4. Production Docker setup:
   - Optimized images
   - Security scanning
   - Resource limits

**Success Criteria**: Production-ready with full observability

### Phase 12: Migration Strategy
**Goal**: Plan and prepare for migrating from rokkon-engine

**Tasks**:
1. Document migration plan:
   - Inventory existing connectors
   - Map to new connector types
   - Data migration strategy
   
2. Create migration tools:
   - Connector configuration converter
   - Historical data migration
   - Validation tools
   
3. Prepare rokkon-engine changes:
   - Identify code to remove
   - Plan module restructuring
   - Update documentation
   
4. Create rollback plan:
   - Feature flags for gradual rollout
   - Data backup procedures
   - Emergency procedures

**Success Criteria**: Complete migration plan with tools ready

## Shared Components (rokkon-engine-new/commons)

### Minimal Shared Components:
1. **Interfaces**:
   - `ConnectorMetadata` - Interface for passing connector info in pipeline context
   
2. **Models**:
   - `ConnectorContext` - POJO for connector ID and metadata
   - Common exception classes
   
3. **Utilities**:
   - ID generation utilities
   - Timestamp utilities

### Explicitly NOT Shared:
- Any rokkon-engine code
- Pipeline execution logic
- Consul implementations
- Module routing logic
- Any existing engine services

**Note**: The commons module should be minimal and contain only what's absolutely necessary for connector-pipeline communication.

## Testing Strategy

### Development Testing (Standalone):
- Unit tests for all components
- Integration tests with mock pipeline
- Docker compose for full stack testing
- No dependency on rokkon-engine

### Validation Testing:
- Performance benchmarks
- Load testing with multiple connectors
- Chaos engineering tests
- Security scanning

### Migration Testing:
- Side-by-side comparison with rokkon-engine
- Data consistency validation
- Rollback procedures

## Key Differences from Original Plan

1. **Complete Independence**: No dependencies on rokkon-engine during development
2. **Direct Pipeline Communication**: Bypasses rokkon-engine entirely
3. **Separate Project Structure**: rokkon-engine-new for clean separation
4. **Docker-First**: Designed as containerized service from the start
5. **Minimal Commons**: Only essential shared components

## Implementation Notes

### Connector → Pipeline Flow:
```
1. Connector registers with connector-server
2. Connector sends documents to connector-server
3. Connector-server discovers pipeline modules via Consul
4. Connector-server sends directly to pipeline module (PipeStepProcessor)
5. Pipeline module has connector ID in metadata for tracking
```

### No rokkon-engine Changes:
- Current rokkon-engine remains completely unchanged
- No feature flags needed during development
- Migration happens all at once when ready

## Timeline Estimate

- Phase 0: 1 week (Mock engine - enables parallel development)
- Phase 1-3: 1 week (Setup and infrastructure)
- Phase 4-6: 2 weeks (Core implementation)
- Phase 7-9: 2 weeks (State and advanced features)
- Phase 10-11: 1 week (Testing and production prep)
- Phase 12: 1 week (Migration planning)

**Total: 8 weeks** for complete implementation (Phase 0 enables concurrent connector development)

## Next Steps

1. Review and approve revised plan
2. Create rokkon-engine-new directory structure
3. Start Phase 1 with build configuration
4. Set up CI/CD for new structure