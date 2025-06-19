# Core Architecture Requirements - Quarkus Migration

## Overview
Migrate Rokkon Engine from Micronaut to Quarkus with a focus on reliability, ease of testing, and maintainability. Start with a monolithic approach to eliminate the multi-module brittleness experienced with Micronaut.

## Architecture Principles
- **Pure Orchestration Layer**: Engine contains no business logic, only routing and coordination
- **Language Agnostic Modules**: gRPC services can be written in any language
- **Configuration-Driven Routing**: All message routing from pipeline configuration
- **Explicit Module Registration**: CI/CD driven module registration via CLI
- **Infrastructure Abstraction**: Modules remain unaware of Consul, Kafka, orchestration

## Monolithic Package Structure

```
src/main/java/com/rokkon/engine/
├── core/              # Core functionality - brings everything together
│   ├── orchestrator/  # Main pipeline orchestration logic
│   ├── registry/      # Module registration and lifecycle
│   └── coordinator/   # Message routing coordination
├── commons/           # Shared events and common utilities
│   ├── events/        # Common event types
│   ├── models/        # Shared data models
│   └── utils/         # Common utilities
├── grpc/              # gRPC transport layer
│   ├── server/        # gRPC server implementations
│   ├── client/        # gRPC client connections
│   └── interceptors/  # gRPC interceptors and middleware
├── kafka/             # Kafka transport with dynamic listeners
│   ├── producers/     # Kafka message producers
│   ├── consumers/     # Dynamic Kafka consumers
│   └── routing/       # Message routing logic
└── slotmanager/       # Kafka slot management (TBD - may not be needed)
    ├── claims/        # Consul-based partition claims
    └── coordination/  # Slot coordination logic
```

## Key Requirements

### 1. Deployment Strategy
- **Target**: JVM mode initially
- **Future Preparation**: Structure code for eventual native compilation
- **No Native Testing**: Don't test native mode initially
- **Focus**: Single deployment artifact, fast startup in JVM mode

### 2. Service Discovery & Configuration  
- **Consul Integration**: Maintain current Consul-based service discovery
- **Dynamic Configuration**: Preserve real-time config updates via Consul KV watches
- **Extension**: Use Quarkus consul-config extension
- **Compatibility**: Maintain existing Consul storage structure

### 3. Core Interface Requirements
Every module must implement:
```proto
service ModuleService {
  rpc ProcessData(ProcessRequest) returns (ProcessResponse);
  rpc GetServiceRegistration() returns (ServiceRegistrationData);
  rpc Check(HealthCheckRequest) returns (HealthCheckResponse); // gRPC health
}
```

### 4. Message Flow Architecture
- **Primary Transport**: gRPC-first implementation
- **Secondary Transport**: Kafka integration (added after gRPC working)
- **Routing**: Configuration-driven message routing
- **Backpressure**: Natural gRPC backpressure handling

### 5. Health and Monitoring
- **Health Checks**: Integrate with Quarkus Health extension
- **Service Status**: Comprehensive module status tracking
- **Consul Health**: Maintain Consul health check integration
- **Metrics**: Basic metrics for monitoring (Quarkus Micrometer)

## Migration Strategy

### Phase 1: Foundation
1. Setup Quarkus project with required extensions
2. Migrate core models and protobuf definitions
3. Implement basic Consul configuration integration
4. Create simple gRPC server structure

### Phase 2: Core Engine
1. Implement module registration system
2. Create pipeline orchestration logic
3. Add gRPC client connection management
4. Implement configuration-driven routing

### Phase 3: Module Integration
1. Convert chunker module as proof of concept
2. Test end-to-end gRPC flow
3. Validate Consul integration
4. Ensure reliable testing setup

### Phase 4: Transport Enhancement
1. Add Kafka transport layer
2. Implement dynamic Kafka listeners
3. Add slot management if needed
4. Complete transport abstraction

## Success Criteria
- **Reliability**: No more 2-3 hour debugging sessions for Consul integration
- **Testing**: Easy integration testing with random ports
- **Maintainability**: Clear separation of concerns
- **Performance**: Fast startup and efficient resource usage
- **Compatibility**: Existing modules work without changes
