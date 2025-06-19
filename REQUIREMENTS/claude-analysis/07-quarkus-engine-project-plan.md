# Quarkus Engine Project Plan

## Overview
Comprehensive project plan for implementing the Quarkus-based Rokkon Engine, incorporating lessons learned from the Micronaut engine analysis and successful patterns from backup-refactor.

## Project Philosophy

### Core Principles
1. **Simplicity First**: Start simple, add complexity incrementally
2. **Proven Patterns**: Use successful patterns from echo module and backup-refactor
3. **Avoid Over-Engineering**: Learn from Micronaut mistakes
4. **Developer Experience**: Easy setup, clear errors, simple debugging
5. **Incremental Value**: Each phase delivers working functionality

### Anti-Patterns to Avoid (From Micronaut Analysis)
- ❌ Dynamic everything at runtime
- ❌ Circular dependencies requiring Provider hacks
- ❌ Complex event-driven systems before basic functionality
- ❌ Over-abstraction with too many interfaces
- ❌ Configuration explosion with 200+ properties

### Success Patterns to Preserve
- ✅ PipeStepProcessor gRPC interface design
- ✅ 5-step module registration flow (simplified)
- ✅ Immutable record-based configuration models
- ✅ Consistent error response structure
- ✅ Comprehensive testing with real services

## Project Structure

### Repository Organization
```
rokkon-engine-workspace/
├── proto-definitions/           # Shared protobuf definitions (existing)
├── modules/                     # Individual Quarkus gRPC modules
│   ├── echo/                   # Reference implementation (existing)
│   ├── tika-parser/            # Document parsing (to be recovered)
│   ├── embedder/               # Embedding generation (to be recovered)
│   ├── chunker/                # Document chunking (to be recovered)
│   └── [future-modules]/       # Additional modules as needed
├── engine/                     # Main Quarkus engine (to be created)
│   ├── src/main/java/com/rokkon/engine/
│   │   ├── core/               # Core orchestration
│   │   ├── config/             # Configuration management
│   │   ├── grpc/               # gRPC client management
│   │   ├── consul/             # Consul integration
│   │   └── api/                # REST APIs (optional)
│   └── src/test/               # Comprehensive engine tests
├── test-utilities/             # Shared testing utilities (existing)
└── REQUIREMENTS/               # Documentation and requirements
```

## Implementation Phases

### Phase 1: Simple Foundation (Weeks 1-2)
**Goal**: Basic working engine with static configuration

#### 1.1 Project Setup
- [x] Analyze requirements and existing code
- [ ] Create engine project using Quarkus CLI
- [ ] Setup basic build configuration
- [ ] Establish testing infrastructure

#### 1.2 Core gRPC Server
```java
@GrpcService
public class EngineService implements PipeStepProcessor {
    
    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        // Simple echo-like implementation initially
        return Uni.createFrom().item(
            ProcessResponse.newBuilder()
                .setSuccess(true)
                .addProcessorLogs("Engine received document: " + request.getDocument().getId())
                .setOutputDoc(request.getDocument())
                .build()
        );
    }
}
```

#### 1.3 Basic Configuration
```yaml
# application.yml - Start simple
quarkus:
  application:
    name: rokkon-engine
  grpc:
    server:
      port: 9090
      host: 0.0.0.0
      enable-reflection-service: true
  log:
    level: INFO
    category:
      "com.rokkon":
        level: DEBUG
```

#### 1.4 Static Pipeline Configuration
```java
// Start with static configuration loaded from files
@ApplicationScoped
public class StaticPipelineConfigService {
    
    public PipelineConfig loadPipelineConfig(String pipelineId) {
        // Load from src/main/resources/pipelines/
        return parseConfigFromFile(pipelineId + ".json");
    }
}
```

#### Phase 1 Success Criteria
- ✅ Engine starts in < 5 seconds
- ✅ Basic gRPC server responds to requests
- ✅ Simple pipeline configuration loading
- ✅ Unit and integration tests pass
- ✅ Health checks working

### Phase 2: Core Pipeline Orchestration (Weeks 3-4)
**Goal**: Basic pipeline execution with static module communication

#### 2.1 Pipeline Orchestrator
```java
@ApplicationScoped
public class PipelineOrchestrator {
    
    @Inject
    StaticPipelineConfigService configService;
    
    @Inject
    StaticModuleClientManager clientManager;
    
    public Uni<ProcessResponse> executeP0ipeline(ProcessRequest request) {
        // Load pipeline configuration
        PipelineConfig config = configService.loadPipelineConfig(request.getPipelineName());
        
        // Execute steps sequentially (start simple)
        return executeSteps(request, config.steps());
    }
    
    private Uni<ProcessResponse> executeSteps(ProcessRequest request, List<PipelineStepConfig> steps) {
        // Simple sequential execution
        return steps.stream()
            .map(step -> executeStep(request, step))
            .reduce(Uni.createFrom().item(request), this::chainSteps);
    }
}
```

#### 2.2 Static Module Client Management
```java
@ApplicationScoped  
public class StaticModuleClientManager {
    
    // Start with hard-coded module endpoints
    @GrpcClient("tika-parser")
    PipeStepProcessor tikaParserClient;
    
    @GrpcClient("chunker")
    PipeStepProcessor chunkerClient;
    
    @GrpcClient("embedder")
    PipeStepProcessor embedderClient;
    
    public PipeStepProcessor getModuleClient(String moduleName) {
        return switch (moduleName) {
            case "tika-parser" -> tikaParserClient;
            case "chunker" -> chunkerClient;
            case "embedder" -> embedderClient;
            default -> throw new IllegalArgumentException("Unknown module: " + moduleName);
        };
    }
}
```

#### 2.3 Configuration Models
```java
// Preserve successful patterns from backup-refactor
public record PipelineConfig(
    String pipelineId,
    String pipelineName,
    List<PipelineStepConfig> steps,
    boolean enabled
);

public record PipelineStepConfig(
    String stepId,
    String stepName,
    String processorName,
    Map<String, Object> processorConfig,
    Duration timeout,
    boolean optional
);
```

#### Phase 2 Success Criteria
- ✅ Pipeline configuration loading from files
- ✅ Sequential step execution working
- ✅ Static module communication functional
- ✅ Error handling for module failures
- ✅ End-to-end pipeline testing

### Phase 3: Module Recovery and Integration (Weeks 5-8)
**Goal**: Recover damaged modules following echo pattern

#### 3.1 Recover tika-parser Module
Following exact echo pattern:
```bash
cd modules
mkdir tika-parser
cd tika-parser
quarkus create app com.rokkon.pipeline:tika-parser \
  --java=21 \
  --gradle-kotlin-dsl \
  --extension=grpc,config-yaml,container-image-docker
```

#### 3.2 TikaParser Service Implementation
```java
@GrpcService
@Singleton
public class TikaParserServiceImpl implements PipeStepProcessor {
    
    private static final Logger LOG = Logger.getLogger(TikaParserServiceImpl.class);
    
    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        LOG.debugf("TikaParser service received document: %s", 
                 request.hasDocument() ? request.getDocument().getId() : "no document");
        
        try {
            // Migrate business logic from backup-hosed-modules/tika-parser-damaged/
            Document processedDoc = parseDocument(request.getDocument());
            
            ProcessResponse.Builder responseBuilder = ProcessResponse.newBuilder()
                    .setSuccess(true)
                    .addProcessorLogs("TikaParser service successfully processed document")
                    .setOutputDoc(processedDoc);
            
            return Uni.createFrom().item(responseBuilder.build());
            
        } catch (Exception e) {
            LOG.error("TikaParser processing failed", e);
            
            ProcessResponse.Builder errorResponse = ProcessResponse.newBuilder()
                    .setSuccess(false)
                    .addProcessorLogs("TikaParser processing failed: " + e.getMessage());
            
            return Uni.createFrom().item(errorResponse.build());
        }
    }
    
    private Document parseDocument(Document input) {
        // Migrate DocumentParser.java logic from backup
        // Preserve core business logic
        // Modernize integration patterns
    }
}
```

#### 3.3 Module Testing Pattern
```java
// Abstract test base (exactly like echo pattern)
public abstract class TikaParserServiceTestBase {
    
    protected abstract PipeStepProcessor getTikaParserService();
    
    @Test
    void testProcessDataWithValidDocument() {
        Document testDoc = createTestDocument();
        ProcessRequest request = ProcessRequest.newBuilder()
            .setDocument(testDoc)
            .build();
        
        ProcessResponse response = getTikaParserService()
            .processData(request)
            .await()
            .atMost(Duration.ofSeconds(10));
        
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getProcessorLogsList())
            .anyMatch(log -> log.contains("successfully processed"));
    }
}

// Unit test
@QuarkusTest
class TikaParserServiceTest extends TikaParserServiceTestBase {
    @GrpcClient
    PipeStepProcessor pipeStepProcessor;
    
    @Override
    protected PipeStepProcessor getTikaParserService() {
        return pipeStepProcessor;
    }
}

// Integration test
@QuarkusIntegrationTest
class TikaParserServiceIT extends TikaParserServiceTestBase {
    // External gRPC client setup
}
```

#### 3.4 Repeat for Other Modules
- Recover embedder module following same pattern
- Recover chunker module following same pattern
- Ensure each module has 100% test pass rate

#### Phase 3 Success Criteria
- ✅ All modules recovered and working
- ✅ 100% unit and integration test pass rate
- ✅ Business logic preserved from backup modules
- ✅ Engine communicates with all modules
- ✅ End-to-end pipeline execution working

### Phase 4: Consul Integration (Weeks 9-10)
**Goal**: Dynamic service discovery and configuration

#### 4.1 Consul Configuration
```properties
# Enable Quarkus Consul integration
quarkus.consul-config.enabled=true
quarkus.consul-config.agent.host-port=${CONSUL_HOST:localhost}:${CONSUL_PORT:8500}
quarkus.consul-config.properties-value-keys=rokkon-clusters/${CLUSTER_NAME:default}/config
```

#### 4.2 Dynamic Configuration Service
```java
@ApplicationScoped
public class ConsulPipelineConfigService {
    
    @Inject
    @RestClient
    ConsulClient consulClient;
    
    public Uni<PipelineConfig> loadPipelineConfig(String pipelineId) {
        String consulKey = String.format("rokkon-clusters/%s/pipelines/%s", 
                                        getClusterName(), pipelineId);
        
        return consulClient.getKVValue(consulKey)
            .onItem().transform(this::parsePipelineConfig);
    }
    
    public Uni<Void> savePipelineConfig(PipelineConfig config) {
        // Validate configuration
        // Save to Consul
        // Fire configuration change event
    }
}
```

#### 4.3 Service Discovery
```java
@ApplicationScoped
public class ConsulModuleDiscovery {
    
    @Inject
    ConsulClient consulClient;
    
    public Uni<List<ServiceInstance>> discoverModules(String moduleName) {
        return consulClient.getHealthyServiceInstances(moduleName)
            .onItem().transform(this::convertToServiceInstances);
    }
}
```

#### 4.4 Dynamic Module Client Manager
```java
@ApplicationScoped
public class DynamicModuleClientManager {
    
    @Inject
    ConsulModuleDiscovery discovery;
    
    private final Map<String, PipeStepProcessor> clientCache = new ConcurrentHashMap<>();
    
    public Uni<PipeStepProcessor> getModuleClient(String moduleName) {
        // Check cache first
        PipeStepProcessor cached = clientCache.get(moduleName);
        if (cached != null) {
            return Uni.createFrom().item(cached);
        }
        
        // Discover and create client
        return discovery.discoverModules(moduleName)
            .onItem().transform(instances -> createGrpcClient(instances.get(0)))
            .onItem().invoke(client -> clientCache.put(moduleName, client));
    }
}
```

#### Phase 4 Success Criteria
- ✅ Consul integration working
- ✅ Dynamic service discovery functional
- ✅ Configuration loading from Consul
- ✅ Module registration and discovery
- ✅ Pipeline execution with dynamic modules

### Phase 5: Enhanced Features (Weeks 11-12)
**Goal**: Production-ready features

#### 5.1 Module Registration Service
```java
@ApplicationScoped
public class ModuleRegistrationService {
    
    // Simplified 5-step registration (avoid Micronaut complexity)
    public Uni<ModuleRegistrationResponse> registerModule(ModuleRegistrationRequest request) {
        return performHealthCheck(request)
            .flatMap(health -> validateInterface(request))
            .flatMap(validation -> registerInConsul(request))
            .flatMap(registration -> createGrpcClient(request))
            .onItem().transform(client -> ModuleRegistrationResponse.success())
            .onFailure().recoverWithItem(this::createErrorResponse);
    }
}
```

#### 5.2 Configuration Management API
```java
@Path("/api/config")
@ApplicationScoped
public class ConfigurationManagementResource {
    
    @GET
    @Path("/pipelines")
    public Uni<List<PipelineConfig>> listPipelines() {
        // List all pipeline configurations
    }
    
    @POST
    @Path("/pipelines")
    public Uni<Response> createPipeline(PipelineConfig config) {
        // Create new pipeline configuration
        // Validate before saving
    }
    
    @PUT
    @Path("/pipelines/{pipelineId}")
    public Uni<Response> updatePipeline(@PathParam("pipelineId") String pipelineId, 
                                       PipelineConfig config) {
        // Update existing pipeline
        // Fire configuration change events
    }
}
```

#### 5.3 Health and Monitoring
```java
@ApplicationScoped
public class EngineHealthCheck {
    
    @Readiness
    public HealthCheckResponse checkEngine() {
        // Check if engine is ready to process requests
        // Verify Consul connectivity
        // Check module availability
    }
    
    @Liveness  
    public HealthCheckResponse checkLiveness() {
        // Basic liveness check
    }
}
```

#### Phase 5 Success Criteria
- ✅ Module registration API working
- ✅ Configuration management API functional
- ✅ Health checks integrated
- ✅ Basic metrics collection
- ✅ Ready for production deployment

### Phase 6: Kafka Integration (Optional - Weeks 13-14)
**Goal**: Add Kafka transport if needed

#### 6.1 Simple Kafka Integration
```java
@ApplicationScoped
public class KafkaMessageProcessor {
    
    @Inject
    PipelineOrchestrator orchestrator;
    
    @Incoming("pipeline-requests")
    @Outgoing("pipeline-responses")
    public Uni<ProcessResponse> processMessage(ProcessRequest request) {
        // Route to pipeline orchestrator
        return orchestrator.executeP0ipeline(request);
    }
}
```

#### 6.2 Kafka Configuration
```properties
# Simple Kafka configuration (avoid Micronaut complexity)
kafka.bootstrap.servers=${KAFKA_BROKERS:localhost:9092}

mp.messaging.incoming.pipeline-requests.connector=smallrye-kafka
mp.messaging.incoming.pipeline-requests.topic=pipeline-requests
mp.messaging.incoming.pipeline-requests.group.id=rokkon-engine

mp.messaging.outgoing.pipeline-responses.connector=smallrye-kafka
mp.messaging.outgoing.pipeline-responses.topic=pipeline-responses
```

## Risk Mitigation

### High Risk Areas
1. **Module Recovery**: Business logic migration from damaged modules
2. **Consul Integration**: Configuration complexity
3. **gRPC Client Management**: Connection pooling and failover

### Mitigation Strategies
1. **Incremental Development**: Each phase delivers working functionality
2. **Comprehensive Testing**: Real service integration testing
3. **Simple First**: Avoid over-engineering from start
4. **Pattern Following**: Strict adherence to echo module pattern

## Success Metrics

### Technical Metrics
- **Startup Time**: < 5 seconds (vs 30+ seconds in Micronaut)
- **Test Reliability**: < 1% flaky test rate
- **Module Recovery**: 100% business logic preservation
- **Configuration Propagation**: < 30 seconds

### Developer Experience Metrics
- **Setup Time**: Working environment in < 10 minutes
- **Debug Time**: Issue identification < 5 minutes
- **New Module Creation**: < 10 minutes following pattern

### Operational Metrics
- **Pipeline Execution**: Reliable end-to-end processing
- **Error Handling**: Clear error messages and recovery
- **Health Monitoring**: Comprehensive health checks
- **Configuration Management**: Easy pipeline configuration

## Timeline Summary

- **Weeks 1-2**: Simple Foundation
- **Weeks 3-4**: Core Pipeline Orchestration  
- **Weeks 5-8**: Module Recovery and Integration
- **Weeks 9-10**: Consul Integration
- **Weeks 11-12**: Enhanced Features
- **Weeks 13-14**: Kafka Integration (Optional)

## Key Deliverables

### Phase 1 Deliverables
- [ ] Working Quarkus engine with basic gRPC server
- [ ] Static configuration loading
- [ ] Basic health checks
- [ ] Comprehensive test setup

### Phase 2 Deliverables
- [ ] Pipeline orchestration working
- [ ] Static module communication
- [ ] Error handling framework
- [ ] End-to-end testing

### Phase 3 Deliverables
- [ ] All modules recovered (tika-parser, embedder, chunker)
- [ ] 100% test pass rate for all modules
- [ ] Business logic preserved and enhanced
- [ ] Engine-to-module integration working

### Phase 4 Deliverables
- [ ] Consul integration functional
- [ ] Dynamic service discovery working
- [ ] Configuration management via Consul
- [ ] Module registration system

### Phase 5 Deliverables
- [ ] Production-ready engine
- [ ] Configuration management API
- [ ] Comprehensive monitoring
- [ ] Deployment documentation

This project plan provides a clear path from the current damaged state to a working, production-ready Quarkus engine while avoiding the over-engineering pitfalls that plagued the Micronaut implementation.