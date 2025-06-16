# Configuration Management Requirements

## Overview
Define configuration management strategy that supports operational effectiveness, dynamic updates, and operator-friendly pipeline configuration through front-end interfaces.

## Configuration Architecture

### 1. Configuration Hierarchy
The system maintains a hierarchical configuration structure designed for operational simplicity and front-end management:

```
PipelineClusterConfig (Root)
├── cluster-metadata
├── global-settings  
├── PipelineGraphConfig (Container)
│   ├── graph-metadata
│   ├── PipelineConfig[] (Individual Pipelines)
│   │   ├── pipeline-metadata
│   │   ├── source-configuration
│   │   ├── PipelineStepConfig[] (Processing Steps)
│   │   │   ├── module-reference
│   │   │   ├── step-configuration
│   │   │   └── routing-rules
│   │   └── sink-configuration
│   └── connector-mappings
└── module-registry
    ├── available-modules
    ├── module-configurations
    └── health-monitoring
```

### 2. Consul Storage Structure
Maintain existing Consul KV structure for operational continuity:

```
/yappy-clusters/<cluster-name>/
├── config.json                    # PipelineClusterConfig
├── pipelines/                     # Individual pipeline configs
│   ├── document-processing.json   # Example pipeline
│   ├── real-time-analysis.json    # Example pipeline
│   └── batch-processing.json      # Example pipeline
├── connector-mappings/            # Source routing configs
│   ├── s3-bucket-mapping.json
│   ├── api-endpoint-mapping.json
│   └── file-watcher-mapping.json
├── modules/registry/              # Module configurations
│   ├── chunker/
│   │   ├── config.json           # Module configuration
│   │   ├── health-check.json     # Health monitoring
│   │   └── instances.json        # Active instances
│   ├── embedder/
│   └── opensearch-sink/
└── status/services/               # Runtime status
    ├── engine-status.json         # Engine health
    ├── module-health.json         # Module health aggregation
    └── pipeline-metrics.json      # Pipeline performance
```

## Dynamic Configuration Requirements

### 1. Real-Time Updates
**Consul KV Watching**: Configuration changes propagate without restart
```java
@ApplicationScoped
public class DynamicConfigurationManager {
    
    @ConfigProperty(name = "consul.kv.watch.interval")
    Duration watchInterval;
    
    public void watchConfigurationChanges() {
        // Watch for changes in Consul KV
        // Trigger configuration reload
        // Validate new configuration
        // Apply changes with fallback
    }
}
```

**Configuration Validation**: All configuration changes validated before application
```java
@ApplicationScoped
public class ConfigurationValidator {
    
    public ValidationResult validatePipelineConfig(PipelineConfig config) {
        // Validate module references exist
        // Check routing logic consistency
        // Verify resource requirements
        // Validate data flow integrity
    }
}
```

### 2. Compare-And-Set Operations
**Conflict Prevention**: Prevent race conditions during configuration updates
```java
@ApplicationScoped
public class ConsulConfigurationStore {
    
    public boolean updateConfiguration(String key, Object newConfig, long expectedVersion) {
        // Implement CAS operations
        // Prevent concurrent modification conflicts
        // Provide optimistic locking
    }
}
```

### 3. Configuration Versioning
**Version Tracking**: Track configuration changes for rollback capability
```java
public class ConfigurationVersion {
    private String configurationId;
    private long version;
    private Instant timestamp;
    private String author; // For audit trail
    private String changeDescription;
    
    // Support for configuration rollback
    // Audit trail for operational compliance
}
```

## Operator-Friendly Configuration

### 1. Front-End Interface Integration
**API Layer**: RESTful API for front-end configuration management
```java
@RestController
@Path("/api/v1/configuration")
public class ConfigurationAPI {
    
    @GET
    @Path("/pipelines")
    public List<PipelineConfig> getAllPipelines() {
        // Return pipeline configurations for UI
    }
    
    @POST
    @Path("/pipelines")
    public Response createPipeline(PipelineConfig config) {
        // Validate and create new pipeline
        // Update Consul configuration
        // Trigger engine reconfiguration
    }
    
    @PUT
    @Path("/pipelines/{id}")
    public Response updatePipeline(@PathParam("id") String id, PipelineConfig config) {
        // Update existing pipeline with CAS
        // Validate configuration changes
        // Apply updates dynamically
    }
}
```

### 2. Configuration Templates
**Pipeline Templates**: Pre-configured templates for common use cases
```json
{
  "template-id": "document-processing-basic",
  "template-name": "Basic Document Processing",
  "description": "Simple document chunking and embedding pipeline",
  "template-config": {
    "steps": [
      {
        "module": "pdf-extractor",
        "config": { "extract-images": false }
      },
      {
        "module": "chunker", 
        "config": { "chunk-size": 1000, "overlap": 100 }
      },
      {
        "module": "embedder",
        "config": { "model": "sentence-transformer" }
      },
      {
        "module": "opensearch-sink",
        "config": { "index": "documents" }
      }
    ]
  }
}
```

### 3. Configuration Validation & Hints
**Interactive Validation**: Provide immediate feedback during configuration
```java
@ApplicationScoped
public class ConfigurationAssistant {
    
    public List<ConfigurationHint> getConfigurationHints(PipelineConfig config) {
        // Suggest optimizations
        // Warn about potential issues
        // Recommend best practices
    }
    
    public List<AvailableModule> getCompatibleModules(String currentStep) {
        // Return modules compatible with current pipeline step
        // Show module capabilities and requirements
    }
}
```

## Data Scientist Module Configuration

### 1. Module Registration Schema
**Self-Describing Modules**: Modules provide their own configuration schema
```proto
message ServiceRegistrationData {
  string module_name = 1;
  string module_version = 2;
  repeated string supported_input_types = 3;
  repeated string supported_output_types = 4;
  ConfigurationSchema configuration_schema = 5;
  ResourceRequirements resource_requirements = 6;
}

message ConfigurationSchema {
  repeated ConfigParameter parameters = 1;
}

message ConfigParameter {
  string name = 1;
  string type = 2;  // string, int, float, boolean, enum
  bool required = 3;
  string default_value = 4;
  string description = 5;
  repeated string enum_values = 6;  // For enum types
}
```

### 2. Module Development Guidelines
**Configuration Best Practices**: Guidelines for module developers
```java
// Example module configuration implementation
@ConfigurationProperties("module.chunker")
public class ChunkerConfig {
    
    @ConfigProperty(name = "chunk-size", defaultValue = "1000")
    @ConfigDescription("Size of text chunks in characters")
    @ConfigRange(min = 100, max = 10000)
    int chunkSize;
    
    @ConfigProperty(name = "overlap", defaultValue = "100") 
    @ConfigDescription("Overlap between chunks in characters")
    int overlap;
    
    @ConfigProperty(name = "split-on-sentences", defaultValue = "true")
    @ConfigDescription("Split chunks on sentence boundaries")
    boolean splitOnSentences;
}
```

### 3. Configuration Testing Framework
**Module Configuration Validation**: Test configuration handling in modules
```java
@QuarkusTest
public class ModuleConfigurationTest {
    
    @Test
    void shouldHandleValidConfiguration() {
        // Test module with valid configuration
    }
    
    @Test
    void shouldRejectInvalidConfiguration() {
        // Test module configuration validation
    }
    
    @Test
    void shouldUseDefaultConfiguration() {
        // Test default configuration behavior
    }
}
```

## Scalability Configuration Requirements

### 1. Auto-Scaling Configuration
**Dynamic Resource Management**: Configuration for auto-scaling behavior
```json
{
  "scaling-config": {
    "min-instances": 1,
    "max-instances": 10,
    "scale-up-threshold": {
      "cpu-percent": 70,
      "memory-percent": 80,
      "queue-depth": 100
    },
    "scale-down-threshold": {
      "cpu-percent": 30,
      "memory-percent": 40,
      "queue-depth": 10
    },
    "cooldown-period": "5m"
  }
}
```

### 2. Performance Tuning Configuration
**Operational Parameters**: Configure performance characteristics
```properties
# Connection pooling
engine.grpc.connection-pool.max-size=50
engine.grpc.connection-pool.min-idle=5
engine.grpc.connection-pool.max-idle-time=30m

# Thread pool configuration
engine.processing.thread-pool.core-size=10
engine.processing.thread-pool.max-size=50
engine.processing.thread-pool.queue-capacity=1000

# Circuit breaker configuration
engine.circuit-breaker.failure-threshold=5
engine.circuit-breaker.timeout=10s
engine.circuit-breaker.reset-timeout=30s
```

### 3. Resource Limits Configuration
**Resource Management**: Configure resource limits and monitoring
```json
{
  "resource-limits": {
    "memory": {
      "max-heap": "2G",
      "warning-threshold": "1.5G"
    },
    "cpu": {
      "max-cores": 4,
      "warning-threshold": 0.8
    },
    "connections": {
      "max-grpc-connections": 100,
      "max-kafka-consumers": 50
    }
  }
}
```

## Configuration Security Requirements

### 1. Secrets Management
**Sensitive Configuration**: Handle secrets and credentials securely
```java
@ApplicationScoped
public class SecureConfigurationManager {
    
    @ConfigProperty(name = "consul.token")
    @Secret
    String consulToken;
    
    @ConfigProperty(name = "kafka.password")
    @Secret  
    String kafkaPassword;
    
    // Never log or expose secret values
    // Integrate with external secret management systems
}
```

### 2. Configuration Encryption
**Data Protection**: Encrypt sensitive configuration data in Consul
```java
@ApplicationScoped
public class EncryptedConfigurationStore {
    
    public void storeEncryptedConfig(String key, Object config) {
        // Encrypt sensitive configuration data
        // Store encrypted data in Consul
        // Decrypt during retrieval
    }
}
```

### 3. Access Control
**Configuration Access**: Control who can modify configurations
```java
@ApplicationScoped
public class ConfigurationAccessControl {
    
    public boolean canModifyPipeline(String userId, String pipelineId) {
        // Implement role-based access control
        // Audit configuration changes
        // Prevent unauthorized modifications
    }
}
```

## Monitoring & Observability

### 1. Configuration Change Monitoring 
**Change Tracking**: Monitor all configuration changes
```java
@ApplicationScoped
public class ConfigurationAuditLogger {
    
    public void logConfigurationChange(ConfigurationChangeEvent event) {
        // Log who changed what when
        // Track configuration drift
        // Monitor configuration health
    }
}
```

### 2. Configuration Metrics
**Operational Metrics**: Track configuration-related metrics
```java
@ApplicationScoped
public class ConfigurationMetrics {
    
    @Gauge(name = "active_pipelines_count")
    public int getActivePipelinesCount() {
        return configurationManager.getActivePipelines().size();
    }
    
    @Counter(name = "configuration_changes_total")
    public void recordConfigurationChange() {
        // Track configuration change frequency
    }
}
```

### 3. Configuration Health Checks
**Configuration Validation**: Continuously validate configuration health
```java
@ApplicationScoped
public class ConfigurationHealthCheck {
    
    @Readiness
    public HealthCheckResponse checkConfigurationHealth() {
        // Validate configuration consistency
        // Check module availability
        // Verify configuration completeness
        return HealthCheckResponse.up("configuration");
    }
}
```

## Success Criteria

### 1. Operational Effectiveness
- **Zero-Downtime Updates**: Configuration changes without service interruption
- **Fast Propagation**: Configuration changes applied within 30 seconds
- **Error Recovery**: Automatic rollback on invalid configuration

### 2. User Experience
- **Intuitive Interface**: Front-end configuration requires minimal training
- **Quick Setup**: New pipelines configured in under 10 minutes
- **Clear Validation**: Configuration errors immediately actionable

### 3. Developer Experience
- **Simple Integration**: New modules integrate with minimal configuration
- **Self-Documenting**: Module configuration schemas auto-generate documentation
- **Testing Support**: Easy testing of module configurations