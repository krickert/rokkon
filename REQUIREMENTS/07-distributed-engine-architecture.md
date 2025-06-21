# Distributed Engine Architecture

## Overview
Rokkon Engine is designed as a distributed system where multiple engine instances coordinate through Consul to provide high availability, load balancing, and shared configuration. This document outlines the engine-to-engine communication, self-registration, and coordination mechanisms.

## Engine Clustering Architecture

### 1. Engine Self-Registration
Each engine instance performs self-registration on startup:

```yaml
# Engine registers itself in Consul
Service:
  Name: "rokkon-engine"
  ID: "rokkon-engine-{hostname}-{uuid}"
  Address: "{container-hostname or IP}"
  Port: 9099  # gRPC port
  Tags:
    - "engine"
    - "v1"
    - "cluster={cluster-name}"
  Check:
    GRPC: "{address}:9099"
    Interval: "10s"
    Timeout: "2s"
```

**Key Points:**
- Engines register with unique IDs (hostname + UUID)
- Health checks ensure only healthy engines receive traffic
- Tags enable filtering by cluster/version
- gRPC endpoint enables engine-to-engine communication

### 2. Configuration Synchronization
All engines in a cluster share configuration through Consul watches:

```
/rokkon-clusters/{cluster-name}/
├── engines/                    # Engine registry (NEW)
│   ├── engine-1/              # Engine instance info
│   └── engine-2/              
├── config.json                # Shared cluster config
├── pipelines/                 # Pipeline definitions
├── modules/registry/          # Module registrations
└── kafka/                     # Kafka topic assignments
    └── listeners/             # Dynamic listener claims
```

**Synchronization Flow:**
1. Any engine can receive configuration updates via API
2. Engine writes to Consul KV (via engine-consul with CAS)
3. All engines receive update via Consul watch
4. Configuration is eventually consistent across cluster

### 3. Dynamic Kafka Listener Coordination
Engines coordinate Kafka consumer assignments to ensure exactly-once processing:

```yaml
# Listener claim in Consul KV
/rokkon-clusters/{cluster}/kafka/listeners/{topic}/{partition}:
  engine_id: "rokkon-engine-host1-abc123"
  claimed_at: "2024-01-19T10:00:00Z"
  lease_id: "consul-session-id"
  last_heartbeat: "2024-01-19T10:05:00Z"
```

**Coordination Protocol:**
1. Engine discovers Kafka topics from pipeline config
2. Engine attempts to claim partitions using Consul sessions
3. Only engine with active lease processes partition
4. Failed engines automatically release claims
5. Other engines detect released claims and rebalance

### 4. Load Distribution Strategies

#### Client-Side Load Balancing
Clients use Stork to discover and load balance across engines:
```properties
quarkus.stork.rokkon-engine.service-discovery.type=consul
quarkus.stork.rokkon-engine.service-discovery.consul-host=consul
quarkus.stork.rokkon-engine.load-balancer.type=least-requests
```

#### Module Request Distribution
Engines distribute module requests across available instances:
- Each engine maintains its own Stork-based discovery
- Modules are shared resource pool for all engines
- Load balancing happens at module level, not engine level

### 5. Inter-Engine Communication

Engines communicate for:
- **Configuration Propagation**: Via Consul watches (not direct)
- **Health Monitoring**: Via Consul health checks
- **Work Distribution**: Via Kafka partition claims
- **Module Sharing**: All engines can use all modules

**Future Use Cases:**
- Pipeline state synchronization
- Distributed transaction coordination
- Cache invalidation
- Metrics aggregation

### 6. Failure Scenarios and Recovery

#### Engine Failure
1. Consul health check fails
2. Engine removed from service registry
3. Clients stop routing to failed engine
4. Kafka listeners released after session timeout
5. Other engines claim released partitions

#### Network Partition
1. Partitioned engines continue processing existing work
2. Cannot claim new Kafka partitions (requires Consul)
3. Configuration updates blocked (CAS failures)
4. Rejoining partition resynchronizes state

#### Cascading Failures Prevention
- Circuit breakers on module calls
- Backpressure propagation
- Request timeouts
- Resource isolation between pipelines

## Implementation Requirements

### 1. Engine Startup Sequence
```java
@ApplicationScoped
public class EngineLifecycle {
    
    @PostConstruct
    void onStart() {
        // 1. Register self in Consul
        engineRegistration.registerSelf();
        
        // 2. Start configuration watches
        configWatcher.startWatching();
        
        // 3. Initialize Stork for module discovery
        storkInitializer.init();
        
        // 4. Start Kafka listener manager
        kafkaListenerManager.start();
        
        // 5. Mark engine as ready
        healthReporter.setReady(true);
    }
    
    @PreDestroy
    void onStop() {
        // Graceful shutdown in reverse order
    }
}
```

### 2. Required Consul Watches
- `/rokkon-clusters/{cluster}/config.json` - Cluster configuration
- `/rokkon-clusters/{cluster}/pipelines/` - Pipeline definitions  
- `/rokkon-clusters/{cluster}/modules/registry/` - Module registrations
- `/rokkon-clusters/{cluster}/kafka/listeners/` - Kafka claims

### 3. Metrics and Observability
Each engine exposes:
- Active pipeline executions
- Module call latencies
- Kafka partition assignments
- Configuration version
- Cluster membership status

## Benefits of Distributed Architecture

1. **High Availability**: Multiple engines eliminate SPOF
2. **Scalability**: Add engines to handle more load
3. **Zero-Downtime Updates**: Rolling updates possible
4. **Geographic Distribution**: Engines can run in multiple regions
5. **Resource Efficiency**: Shared module pool
6. **Automatic Failover**: Via Consul health checks

## Testing Strategy

### Integration Tests
1. Multi-engine startup and registration
2. Configuration propagation across engines
3. Kafka partition rebalancing on failure
4. Module discovery from any engine
5. Load distribution verification

### Chaos Testing
1. Random engine failures
2. Network partitions
3. Consul unavailability
4. Slow module responses
5. Resource exhaustion

## Security Considerations

1. **mTLS Between Engines**: For future inter-engine RPC
2. **Consul ACLs**: Restrict engine registration
3. **Kafka SASL/SSL**: Secure Kafka communication
4. **Module Authentication**: Validate module identity

## Migration Path

### Phase 1: Single Engine (Current)
- Basic engine runs standalone
- Registers modules
- Executes pipelines

### Phase 2: Engine Self-Registration
- Add engine Consul registration
- Implement health checks
- Enable Stork discovery

### Phase 3: Multi-Engine Support  
- Implement Consul watches
- Add configuration synchronization
- Test with 2-3 engines

### Phase 4: Dynamic Kafka Listeners
- Implement partition claiming
- Add lease management
- Enable automatic rebalancing

### Phase 5: Production Hardening
- Add comprehensive metrics
- Implement chaos testing
- Performance optimization