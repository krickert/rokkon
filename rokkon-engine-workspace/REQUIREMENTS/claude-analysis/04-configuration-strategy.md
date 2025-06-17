# Configuration Management Strategy

## Overview
Comprehensive strategy for configuration management in the Quarkus-based Rokkon Engine, preserving successful patterns from backup-refactor while modernizing with Quarkus extensions.

## Configuration Architecture Philosophy

### Core Principles
1. **Immutable Configuration**: Record-based models for thread safety
2. **Hierarchical Organization**: Logical structure in Consul KV store
3. **Event-Driven Updates**: CDI events for configuration lifecycle
4. **Multi-Stage Validation**: Comprehensive validation pipeline
5. **Schema Evolution**: JSON schema support for backward compatibility
6. **Async Operations**: Non-blocking configuration operations

### Configuration Sources Priority
1. **System Properties**: `-D` command line arguments (highest priority)
2. **Environment Variables**: OS environment variables
3. **Consul KV Store**: Dynamic configuration from Consul
4. **Application Properties**: Static configuration files
5. **Default Values**: Hardcoded defaults (lowest priority)

## Configuration Models

### Core Configuration Records
Preserve successful immutable record pattern from backup-refactor:

```java
// Top-level cluster configuration
public record PipelineClusterConfig(
    String clusterId,
    String clusterName,
    Map<String, PipelineConfig> pipelines,
    Map<String, PipelineModuleConfiguration> moduleConfigurations,
    SecurityConstraints securityConstraints,
    Instant lastUpdated,
    String version // MD5 hash for change detection
) {
    // Validation methods
    public List<ValidationResult> validate() {
        // Multi-stage validation logic
    }
}

// Individual pipeline configuration
public record PipelineConfig(
    String pipelineId,
    String pipelineName,
    List<PipelineStepConfig> steps,
    TransportConfig inputTransport,
    TransportConfig outputTransport,
    Map<String, Object> customProperties,
    boolean enabled
) implements Validatable {
    
    // Derived properties
    public Set<String> getRequiredModules() {
        return steps.stream()
            .map(PipelineStepConfig::processorName)
            .collect(Collectors.toSet());
    }
}

// Pipeline step configuration
public record PipelineStepConfig(
    String stepId,
    String stepName,
    String processorName,
    StepType stepType,
    Map<String, Object> processorConfig,
    RetryPolicy retryPolicy,
    Duration timeout,
    boolean optional
) implements Validatable {
    
    // Default retry policy
    public static final RetryPolicy DEFAULT_RETRY = new RetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(10));
}

// Module configuration
public record PipelineModuleConfiguration(
    String moduleName,
    String moduleType,
    SchemaReference configSchema,
    Map<String, Object> defaultConfig,
    Set<String> requiredCapabilities,
    Map<String, String> healthCheckEndpoints
) implements Validatable;

// Transport configurations
public sealed interface TransportConfig permits GrpcTransportConfig, KafkaTransportConfig {
    TransportType transportType();
    Map<String, Object> transportProperties();
}

public record GrpcTransportConfig(
    String serviceName,
    String host,
    int port,
    boolean useTls,
    Duration timeout,
    Map<String, Object> transportProperties
) implements TransportConfig {
    @Override
    public TransportType transportType() { return TransportType.GRPC; }
}

public record KafkaTransportConfig(
    String topicName,
    String brokerUrl,
    String consumerGroup,
    Map<String, String> kafkaProperties,
    Map<String, Object> transportProperties
) implements TransportConfig {
    @Override
    public TransportType transportType() { return TransportType.KAFKA; }
}
```

### Configuration Validation
Multi-stage validation pipeline:

```java
@ApplicationScoped
public class ConfigValidationService {
    
    @Inject
    List<StructuralValidationRule> structuralValidators;
    
    @Inject
    SchemaValidationService schemaValidator;
    
    public CompletionStage<ValidationResult> validateConfiguration(PipelineClusterConfig config) {
        return CompletableFuture
            .supplyAsync(() -> validateStructural(config))
            .thenCompose(structuralResult -> {
                if (!structuralResult.isValid()) {
                    return CompletableFuture.completedFuture(structuralResult);
                }
                return validateSchema(config);
            })
            .thenCompose(schemaResult -> {
                if (!schemaResult.isValid()) {
                    return CompletableFuture.completedFuture(schemaResult);
                }
                return validateBusinessRules(config);
            });
    }
    
    private ValidationResult validateStructural(PipelineClusterConfig config) {
        List<String> errors = new ArrayList<>();
        
        for (StructuralValidationRule rule : structuralValidators) {
            ValidationResult result = rule.validate(config);
            if (!result.isValid()) {
                errors.addAll(result.getErrors());
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
}
```

## Consul Integration with Quarkus

### Consul Configuration Setup
```properties
# Consul basic configuration
quarkus.consul-config.enabled=true
quarkus.consul-config.agent.host-port=${CONSUL_HOST:localhost}:${CONSUL_PORT:8500}
quarkus.consul-config.agent.token=${CONSUL_TOKEN:}

# Configuration keys to watch
quarkus.consul-config.properties-value-keys=rokkon-clusters/${CLUSTER_NAME:default}/config
quarkus.consul-config.properties-value-keys[1]=rokkon-clusters/${CLUSTER_NAME:default}/modules/registry

# Polling configuration
quarkus.consul-config.agent.connection-timeout=10s
quarkus.consul-config.agent.read-timeout=60s
```

### Dynamic Configuration Watching
```java
@ApplicationScoped
public class ConsulConfigWatcher {
    
    private static final Logger LOG = Logger.getLogger(ConsulConfigWatcher.class);
    
    @Inject
    Event<ConfigChangeEvent> configChangeEvent;
    
    @ConfigProperty(name = "consul.kv.watch.paths")
    List<String> watchPaths;
    
    @ConfigProperty(name = "consul.agent.host")
    String consulHost;
    
    @ConfigProperty(name = "consul.agent.port")
    int consulPort;
    
    private final Map<String, String> lastKnownVersions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    @Startup
    void startWatching() {
        LOG.info("Starting Consul configuration watching for paths: {}", watchPaths);
        
        for (String path : watchPaths) {
            scheduler.scheduleWithFixedDelay(
                () -> checkForChanges(path),
                0, 5, TimeUnit.SECONDS
            );
        }
    }
    
    private void checkForChanges(String path) {
        try {
            // Use Consul HTTP API to check for changes
            String currentVersion = getCurrentVersion(path);
            String lastKnownVersion = lastKnownVersions.get(path);
            
            if (!Objects.equals(currentVersion, lastKnownVersion)) {
                LOG.info("Configuration change detected for path: {}", path);
                
                // Load new configuration
                String configJson = loadConfiguration(path);
                
                // Parse and validate
                PipelineClusterConfig newConfig = parseConfiguration(configJson);
                ValidationResult validation = validateConfiguration(newConfig);
                
                if (validation.isValid()) {
                    // Update version and fire event
                    lastKnownVersions.put(path, currentVersion);
                    configChangeEvent.fire(new ConfigChangeEvent(
                        path, 
                        ConfigChangeType.UPDATED, 
                        newConfig,
                        Instant.now()
                    ));
                } else {
                    LOG.error("Invalid configuration detected for path {}: {}", 
                        path, validation.getErrors());
                }
            }
        } catch (Exception e) {
            LOG.error("Error checking for configuration changes on path: " + path, e);
        }
    }
    
    @PreDestroy
    void stopWatching() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}
```

### Configuration Service Implementation
```java
@ApplicationScoped
public class PipelineConfigService {
    
    private static final Logger LOG = Logger.getLogger(PipelineConfigService.class);
    
    @Inject
    @RestClient
    ConsulClient consulClient;
    
    @Inject
    ConfigValidationService validationService;
    
    @Inject
    Event<ConfigChangeEvent> configChangeEvent;
    
    @ConfigProperty(name = "cluster.name")
    String clusterName;
    
    private final ObjectMapper objectMapper = createObjectMapper();
    
    public Uni<PipelineClusterConfig> loadClusterConfig() {
        String consulKey = String.format("rokkon-clusters/%s/config", clusterName);
        
        return consulClient.getKVValue(consulKey)
            .onItem().transform(this::parseClusterConfig)
            .onFailure().invoke(throwable -> 
                LOG.error("Failed to load cluster configuration", throwable));
    }
    
    public Uni<PipelineConfig> loadPipelineConfig(String pipelineId) {
        String consulKey = String.format("rokkon-clusters/%s/pipelines/%s", clusterName, pipelineId);
        
        return consulClient.getKVValue(consulKey)
            .onItem().transform(this::parsePipelineConfig)
            .onFailure().invoke(throwable -> 
                LOG.error("Failed to load pipeline configuration for: " + pipelineId, throwable));
    }
    
    public Uni<Void> savePipelineConfig(PipelineConfig config) {
        return validationService.validateConfiguration(config)
            .onItem().transformToUni(validation -> {
                if (!validation.isValid()) {
                    return Uni.createFrom().failure(
                        new ValidationException("Configuration validation failed: " + validation.getErrors())
                    );
                }
                
                return savePipelineConfigToConsul(config);
            })
            .onItem().invoke(() -> {
                configChangeEvent.fire(new ConfigChangeEvent(
                    config.pipelineId(),
                    ConfigChangeType.UPDATED,
                    config,
                    Instant.now()
                ));
            });
    }
    
    public Uni<Boolean> deletePipelineConfig(String pipelineId) {
        // Check for dependencies before deletion
        return checkPipelineDependencies(pipelineId)
            .onItem().transformToUni(hasDependencies -> {
                if (hasDependencies) {
                    return Uni.createFrom().failure(
                        new IllegalStateException("Cannot delete pipeline with dependencies: " + pipelineId)
                    );
                }
                
                return deletePipelineConfigFromConsul(pipelineId);
            })
            .onItem().invoke(deleted -> {
                if (deleted) {
                    configChangeEvent.fire(new ConfigChangeEvent(
                        pipelineId,
                        ConfigChangeType.DELETED,
                        null,
                        Instant.now()
                    ));
                }
            });
    }
    
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
```

## Consul KV Storage Structure

### Hierarchical Organization
```
/rokkon-clusters/<cluster-name>/
├── config.json                        # PipelineClusterConfig (root config)
├── pipelines/                          # Individual pipeline configurations
│   ├── document-processing.json        # PipelineConfig
│   ├── real-time-analysis.json         # PipelineConfig
│   └── batch-processing.json           # PipelineConfig
├── modules/
│   ├── registry/                       # Module configurations
│   │   ├── tika-parser.json           # PipelineModuleConfiguration
│   │   ├── embedder.json              # PipelineModuleConfiguration
│   │   └── chunker.json               # PipelineModuleConfiguration
│   └── instances/                      # Runtime module instances
│       ├── tika-parser-001.json       # ModuleInstance
│       ├── tika-parser-002.json       # ModuleInstance
│       └── embedder-001.json          # ModuleInstance
├── transport/                          # Transport configurations
│   ├── kafka-config.json              # KafkaTransportConfig
│   └── grpc-config.json               # GrpcTransportConfig
└── status/                            # Runtime status information
    ├── engine-status.json             # Engine health and status
    ├── pipeline-status.json           # Pipeline execution status
    └── module-health.json             # Module health information
```

### Configuration Examples

#### Pipeline Cluster Configuration
```json
{
  "clusterId": "prod-cluster-01",
  "clusterName": "Production Cluster",
  "pipelines": {
    "document-processing": {
      "pipelineId": "document-processing",
      "pipelineName": "Document Processing Pipeline",
      "steps": [
        {
          "stepId": "parse-step",
          "stepName": "Document Parsing",
          "processorName": "tika-parser",
          "stepType": "PROCESSOR",
          "processorConfig": {
            "outputFormat": "structured",
            "extractMetadata": true
          },
          "retryPolicy": {
            "maxRetries": 3,
            "initialDelay": "1s",
            "maxDelay": "10s"
          },
          "timeout": "30s",
          "optional": false
        }
      ],
      "inputTransport": {
        "transportType": "KAFKA",
        "topicName": "document-input",
        "brokerUrl": "kafka:9092",
        "consumerGroup": "document-processors"
      },
      "outputTransport": {
        "transportType": "GRPC",
        "serviceName": "document-sink",
        "host": "document-sink",
        "port": 9090
      },
      "enabled": true
    }
  },
  "moduleConfigurations": {
    "tika-parser": {
      "moduleName": "tika-parser",
      "moduleType": "DOCUMENT_PARSER",
      "configSchema": {
        "schemaUrl": "https://schemas.rokkon.com/tika-parser/v1.0.0",
        "version": "1.0.0"
      },
      "defaultConfig": {
        "maxFileSize": "10MB",
        "timeout": "30s",
        "supportedTypes": ["pdf", "docx", "txt", "html"]
      },
      "requiredCapabilities": ["document-parsing", "metadata-extraction"],
      "healthCheckEndpoints": {
        "health": "/health",
        "ready": "/ready"
      }
    }
  },
  "securityConstraints": {
    "requireAuthentication": true,
    "allowedOrigins": ["*.rokkon.com"],
    "encryptionRequired": true
  },
  "lastUpdated": "2024-01-15T10:30:00Z",
  "version": "a1b2c3d4e5f6"
}
```

## Configuration Events and Lifecycle

### Configuration Change Events
```java
public record ConfigChangeEvent(
    String configPath,
    ConfigChangeType changeType,
    Object newConfiguration,
    Instant timestamp
) {
    
    public enum ConfigChangeType {
        CREATED,
        UPDATED,  
        DELETED,
        VALIDATED,
        VALIDATION_FAILED
    }
}

@ApplicationScoped
public class ConfigurationEventHandler {
    
    private static final Logger LOG = Logger.getLogger(ConfigurationEventHandler.class);
    
    @Inject
    PipelineOrchestrator orchestrator;
    
    @Inject
    ModuleRegistrationService moduleService;
    
    public void onConfigurationChange(@Observes ConfigChangeEvent event) {
        LOG.info("Configuration change detected: {} for path: {}", 
            event.changeType(), event.configPath());
        
        switch (event.changeType()) {
            case UPDATED -> handleConfigurationUpdate(event);
            case DELETED -> handleConfigurationDeletion(event);
            case CREATED -> handleConfigurationCreation(event);
        }
    }
    
    private void handleConfigurationUpdate(ConfigChangeEvent event) {
        if (event.newConfiguration() instanceof PipelineConfig pipelineConfig) {
            // Reload pipeline configuration
            orchestrator.reloadPipelineConfiguration(pipelineConfig);
        } else if (event.newConfiguration() instanceof PipelineModuleConfiguration moduleConfig) {
            // Update module configuration
            moduleService.updateModuleConfiguration(moduleConfig);
        }
    }
}
```

## Testing Configuration Management

### Configuration Testing Strategy
```java
@QuarkusTest
@TestProfile(ConfigurationTestProfile.class)
class PipelineConfigServiceTest {
    
    @Inject
    PipelineConfigService configService;
    
    @Inject
    TestDataManager testDataManager;
    
    @BeforeEach
    void setupTestData() {
        testDataManager.setupTestConfiguration();
    }
    
    @AfterEach
    void cleanupTestData() {
        testDataManager.cleanupTestConfiguration();
    }
    
    @Test
    void testConfigurationRoundTrip() {
        // Create test configuration
        PipelineConfig testConfig = createTestPipelineConfig();
        
        // Save configuration
        configService.savePipelineConfig(testConfig)
            .await().atMost(Duration.ofSeconds(10));
        
        // Load configuration
        PipelineConfig loadedConfig = configService
            .loadPipelineConfig(testConfig.pipelineId())
            .await().atMost(Duration.ofSeconds(10));
        
        // Verify roundtrip
        assertThat(loadedConfig).isEqualTo(testConfig);
    }
    
    @Test
    void testConfigurationValidation() {
        // Test various validation scenarios
        PipelineConfig invalidConfig = createInvalidPipelineConfig();
        
        assertThatThrownBy(() -> 
            configService.savePipelineConfig(invalidConfig)
                .await().atMost(Duration.ofSeconds(10))
        ).isInstanceOf(ValidationException.class);
    }
    
    @Test
    void testDynamicConfigurationUpdate() {
        AtomicReference<ConfigChangeEvent> capturedEvent = new AtomicReference<>();
        
        // Observer to capture configuration change events
        public void captureConfigEvent(@Observes ConfigChangeEvent event) {
            capturedEvent.set(event);
        }
        
        // Update configuration and verify event
        PipelineConfig updatedConfig = createUpdatedPipelineConfig();
        configService.savePipelineConfig(updatedConfig).await().atMost(Duration.ofSeconds(10));
        
        // Verify event was fired
        await().atMost(Duration.ofSeconds(5))
            .until(() -> capturedEvent.get() != null);
        
        ConfigChangeEvent event = capturedEvent.get();
        assertThat(event.changeType()).isEqualTo(ConfigChangeType.UPDATED);
        assertThat(event.newConfiguration()).isEqualTo(updatedConfig);
    }
}
```

## Configuration Migration and Evolution

### Schema Evolution Strategy
```java
@ApplicationScoped
public class ConfigurationMigrationService {
    
    private static final Map<String, ConfigurationMigrator> MIGRATORS = Map.of(
        "1.0.0->1.1.0", new V1_0_to_V1_1_Migrator(),
        "1.1.0->1.2.0", new V1_1_to_V1_2_Migrator()
    );
    
    public PipelineConfig migrateConfiguration(PipelineConfig config, String targetVersion) {
        String currentVersion = detectConfigurationVersion(config);
        
        if (currentVersion.equals(targetVersion)) {
            return config;
        }
        
        String migrationKey = currentVersion + "->" + targetVersion;
        ConfigurationMigrator migrator = MIGRATORS.get(migrationKey);
        
        if (migrator == null) {
            throw new IllegalArgumentException(
                "No migration path from " + currentVersion + " to " + targetVersion);
        }
        
        return migrator.migrate(config);
    }
}
```

## Operational Configuration Management

### Configuration Backup and Recovery
```java
@ApplicationScoped
public class ConfigurationBackupService {
    
    @Scheduled(every = "1h")
    void backupConfigurations() {
        // Backup all configurations to external storage
        // Create versioned snapshots
        // Maintain backup retention policy
    }
    
    public Uni<Void> restoreConfiguration(String backupId) {
        // Restore configuration from backup
        // Validate configuration before restore
        // Handle restoration failures gracefully
    }
}
```

### Configuration Monitoring
```java
@ApplicationScoped
public class ConfigurationMonitoringService {
    
    @Inject
    MeterRegistry meterRegistry;
    
    public void recordConfigurationChange(ConfigChangeEvent event) {
        Counter.builder("configuration.changes")
            .tag("type", event.changeType().name())
            .tag("path", event.configPath())
            .register(meterRegistry)
            .increment();
    }
    
    public void recordValidationFailure(String configPath, List<String> errors) {
        Counter.builder("configuration.validation.failures")
            .tag("path", configPath)
            .register(meterRegistry)
            .increment();
    }
}
```

This comprehensive configuration management strategy preserves the successful patterns from the backup-refactor while modernizing with Quarkus extensions, ensuring reliable, maintainable, and scalable configuration management for the Rokkon Engine.