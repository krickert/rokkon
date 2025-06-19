# Proposed Quarkus Project Structure

## Overview
Based on analysis of backup-refactor and requirements, this document proposes a simplified, effective project structure that preserves proven patterns while modernizing the implementation.

## Project Structure Philosophy

### Core Principles
1. **Monolithic Engine**: Single deployment artifact to eliminate multi-module brittleness
2. **Module Independence**: Individual Quarkus gRPC modules following echo pattern
3. **Configuration-Driven**: All routing and behavior controlled by configuration
4. **Proto-First**: Single source of truth for service definitions
5. **Test-First**: Comprehensive testing at all levels

### Directory Structure
```
rokkon-engine-workspace/
├── proto-definitions/           # Shared proto definitions (existing)
├── modules/                     # Individual Quarkus gRPC modules
│   ├── echo/                   # Reference implementation (existing)
│   ├── tika-parser/            # Document parsing module (to be recovered)
│   ├── embedder/               # Embedding generation module (to be recovered)
│   ├── chunker/                # Document chunking module (to be recovered)
│   └── [future-modules]/       # Additional modules as needed
├── engine/                     # Main Quarkus engine (to be created)
└── test-utilities/             # Shared testing utilities (existing)
```

## Engine Architecture (New Monolithic Engine)

### Package Structure
```
src/main/java/com/rokkon/engine/
├── core/                       # Core orchestration logic
│   ├── orchestrator/          # Pipeline orchestration
│   ├── registry/              # Module registration and lifecycle
│   └── routing/               # Message routing coordination
├── config/                    # Configuration management
│   ├── model/                 # Configuration models (from backup-refactor)
│   ├── service/               # Configuration services
│   └── validation/            # Validation rules and logic
├── grpc/                      # gRPC transport layer
│   ├── server/                # gRPC server implementations
│   ├── client/                # gRPC client management
│   └── interceptors/          # Middleware and interceptors
├── consul/                    # Consul integration
│   ├── config/                # Dynamic configuration watching
│   ├── registry/              # Service discovery
│   └── health/                # Health check integration
└── api/                       # REST API endpoints (if needed)
    ├── config/                # Configuration management API
    └── registry/              # Module registration API
```

### Core Components

#### 1. Configuration Management
Preserve successful patterns from backup-refactor:

```java
@ApplicationScoped
public class PipelineConfigService {
    
    @Inject
    ConsulConfigWatcher consulWatcher;
    
    @Inject
    Event<ConfigChangeEvent> configChangeEvent;
    
    // Async CompletionStage-based operations
    public Uni<PipelineConfig> loadPipelineConfig(String clusterId, String pipelineId) {
        // Implementation using Quarkus Consul Config
    }
    
    public Uni<Void> savePipelineConfig(String clusterId, PipelineConfig config) {
        // Multi-stage validation before save
        // Fire CDI events for changes
    }
}
```

#### 2. Module Registry
Dynamic module registration with comprehensive validation:

```java
@ApplicationScoped
public class ModuleRegistrationService {
    
    @Inject
    DynamicGrpcClientManager grpcClientManager;
    
    @Inject
    ConsulModuleRegistry consulRegistry;
    
    public Uni<ModuleRegistrationResponse> registerModule(ModuleRegistrationRequest request) {
        // 5-step validation flow from backup-refactor
        // 1. Health check
        // 2. Interface validation  
        // 3. Schema validation
        // 4. Consul registration
        // 5. Dynamic client creation
    }
}
```

#### 3. Pipeline Orchestrator
Configuration-driven message routing:

```java
@ApplicationScoped
public class PipelineOrchestrator {
    
    @Inject
    PipelineConfigService configService;
    
    @Inject
    DynamicGrpcClientManager clientManager;
    
    public Uni<ProcessResponse> processDocument(ProcessRequest request) {
        // Load pipeline configuration
        // Route to appropriate modules based on config
        // Handle retries, timeouts, error handling
    }
}
```

## Module Structure (Following Echo Pattern)

### Individual Module Layout
Each module follows the proven echo pattern:

```
modules/[module-name]/
├── build.gradle.kts            # Exact pattern from echo
├── settings.gradle.kts         # Module-level settings
├── src/
│   ├── main/
│   │   ├── java/com/rokkon/[module]/
│   │   │   └── [Module]ServiceImpl.java
│   │   ├── proto/              # Extracted from proto-definitions
│   │   └── resources/
│   │       └── application.yml
│   ├── test/
│   │   └── java/com/rokkon/[module]/
│   │       ├── [Module]ServiceTestBase.java    # Abstract base
│   │       └── [Module]ServiceTest.java        # Unit tests
│   └── integrationTest/
│       └── java/com/rokkon/[module]/
│           └── [Module]ServiceIT.java          # Integration tests
```

### Module Implementation Pattern
```java
@GrpcService
@Singleton
public class TikaParserServiceImpl implements PipeStepProcessor {
    
    private static final Logger LOG = Logger.getLogger(TikaParserServiceImpl.class);
    
    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        // Module-specific business logic
        // Preserve logic from backup modules
        // Follow reactive patterns
    }
    
    @Override
    public Uni<ServiceRegistrationData> getServiceRegistration(Empty request) {
        // Module metadata and capabilities
    }
}
```

## Testing Strategy

### Three-Level Testing Approach

#### 1. Module Testing
Each module has both unit and integration tests:

```java
// Abstract base for shared test logic
public abstract class TikaParserServiceTestBase {
    protected abstract PipeStepProcessor getTikaParserService();
    
    @Test
    void testDocumentParsing() {
        // Common test logic
    }
}

// Unit test with @QuarkusTest
@QuarkusTest
class TikaParserServiceTest extends TikaParserServiceTestBase {
    @GrpcClient
    PipeStepProcessor pipeStepProcessor;
    
    @Override
    protected PipeStepProcessor getTikaParserService() {
        return pipeStepProcessor;
    }
}

// Integration test with @QuarkusIntegrationTest
@QuarkusIntegrationTest
class TikaParserServiceIT extends TikaParserServiceTestBase {
    private ManagedChannel channel;
    private PipeStepProcessor pipeStepProcessor;
    
    // External gRPC client setup
}
```

#### 2. Engine Testing
Comprehensive engine testing with real services:

```java
@QuarkusTest
@TestProfile(EngineIntegrationTestProfile.class)
class PipelineOrchestrationTest {
    
    // Test with Quarkus dev services for Consul
    // Test configuration loading and watching
    // Test module registration and discovery
    // Test pipeline execution flows
}
```

#### 3. End-to-End Testing
Complete system testing:

```java
@QuarkusTest
@TestProfile(E2ETestProfile.class)
class EndToEndPipelineTest {
    
    // Test complete document processing flows
    // Test with real modules running in containers
    // Test failure scenarios and recovery
    // Test configuration changes during processing
}
```

### Test Infrastructure

#### Quarkus Dev Services Integration
```properties
# Automatic service containers for testing
quarkus.devservices.enabled=true
quarkus.consul-config.devservices.enabled=true
%test.quarkus.grpc.server.port=0  # Random ports
```

#### Test Profiles
```java
public class EngineIntegrationTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "engine.test-mode", "true",
            "consul.test-data-setup", "true",
            "quarkus.log.category.\"com.rokkon\".level", "DEBUG"
        );
    }
}
```

## Configuration Management

### Consul KV Structure
Preserve proven hierarchical structure:

```
/rokkon-clusters/<cluster>/
├── config.json                # PipelineClusterConfig
├── pipelines/                  # Individual pipeline configs
│   ├── pipeline-1.json
│   └── pipeline-2.json
├── modules/registry/           # Module configurations
│   ├── tika-parser.json
│   ├── embedder.json
│   └── chunker.json
└── status/services/            # Runtime status
    ├── engine-status.json
    └── module-status.json
```

### Dynamic Configuration
```java
@ApplicationScoped
public class ConsulConfigWatcher {
    
    @ConfigProperty(name = "consul.kv.watch.paths")
    List<String> watchPaths;
    
    @Inject
    Event<ConfigChangeEvent> configChangeEvent;
    
    @Startup
    void startWatching() {
        // Use Quarkus Consul Config extension
        // Watch for configuration changes
        // Fire CDI events on changes
    }
}
```

## Migration Strategy

### Phase 1: Engine Foundation
1. Create main engine project using Quarkus CLI
2. Migrate configuration models from backup-refactor
3. Implement basic Consul integration using Quarkus extensions
4. Create module registration framework

### Phase 2: Module Recovery
1. Recover tika-parser following echo pattern
2. Recover embedder following echo pattern  
3. Recover chunker following echo pattern
4. Test each module individually before integration

### Phase 3: Integration
1. Implement pipeline orchestration logic
2. Test engine-to-module communication
3. Test configuration-driven routing
4. Validate end-to-end processing flows

### Phase 4: Enhancement
1. Add Kafka transport layer (if needed)
2. Implement monitoring and metrics
3. Add operational tooling
4. Performance optimization

## Success Criteria

### Technical
- All modules have 100% passing unit and integration tests
- Engine startup time < 5 seconds in JVM mode
- Configuration changes propagate within 30 seconds
- Zero-downtime module registration/deregistration

### Operational
- No more 2-3 hour debugging sessions
- Clear error messages and debugging information
- Reliable test execution with < 1% flaky test rate
- Easy new module integration (< 10 minutes)

## Key Benefits of This Structure

1. **Proven Patterns**: Preserves successful patterns from backup-refactor
2. **Quarkus Modernization**: Leverages Quarkus extensions for reliability
3. **Simplified Testing**: Clear testing strategy with comprehensive coverage
4. **Module Independence**: Each module is independently testable and deployable
5. **Configuration-Driven**: Flexible pipeline configuration without code changes
6. **Operational Reliability**: Focus on debugging and monitoring capabilities