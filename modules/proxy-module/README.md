# PipeStepProcessor Proxy Module

This module provides a proxy service that implements the PipeStepProcessor interface in Quarkus and forwards calls to a backend module implementation. It adds Quarkus features like security, metrics, and logging to any PipeStepProcessor module.

## Architecture Overview

```
┌─────────────────────────────────────┐      ┌──────────────────────┐
│ Quarkus Proxy Service               │      │ Module Implementation │
│                                     │      │                       │
│ ┌─────────────┐    ┌─────────────┐  │      │ ┌─────────────────┐  │
│ │ Quarkus     │    │ gRPC Client │  │      │ │ gRPC Server     │  │
│ │ Features    ├────┤ Proxy       ├──┼──────┼─┤ (PipeStep       │  │
│ │             │    │             │  │      │ │  Processor)     │  │
│ └─────────────┘    └─────────────┘  │      │ └─────────────────┘  │
│                                     │      │                       │
└─────────────────────────────────────┘      └──────────────────────┘
   Handles: SSL, Auth, Metrics, Logging         Simple Implementation
            Tracing, Health Checks              Focus on Business Logic
```

## Features

- **Security**: Basic authentication and SSL support
- **Metrics**: Micrometer integration with Prometheus export
- **Logging**: Structured logging with configurable levels
- **Tracing**: OpenTelemetry integration
- **Health Checks**: Microprofile Health integration
- **Error Handling**: Graceful error handling and recovery
- **Configuration**: Externalized configuration via environment variables

## Building and Running

### Building the Proxy Module

```bash
# Make the build script executable
chmod +x docker-build.sh

# Build the Docker image
./docker-build.sh
```

### Running with Docker

```bash
# Run the proxy with the test module
    docker run -i --rm -p 39100:39100 -e MODULE_HOST=host.docker.internal pipeline/proxy-module:latest
```

### Testing the Proxy

```bash
# Make the test script executable
chmod +x test-docker.sh

# Run the test script
./test-docker.sh
```

## Configuration

The proxy service can be configured using environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| MODULE_HOST | The host where the module is running | localhost |
| MODULE_PORT | The port where the module's gRPC server is listening | 9091 |
| PROXY_PORT | The port where the proxy's gRPC server is listening | 9090 |
| MAX_RETRIES | Maximum number of connection retry attempts | 5 |
| STARTUP_TIMEOUT | Timeout in seconds for module startup | 60 |
| CHECK_INTERVAL | Interval in seconds between health checks | 5 |

## Implementation Details

### PipeStepProcessorProxy

The `PipeStepProcessorProxy` class implements the `PipeStepProcessor` interface and forwards calls to the backend module. It adds metrics, logging, and error handling to each method.

```java
@GrpcService
@Singleton
public class PipeStepProcessorProxy implements PipeStepProcessor {
    // ...
    
    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        return processingTimer.record(() -> 
            moduleClient.processData(request)
                .onItem().invoke(response -> {
                    if (response.getSuccess()) {
                        processedRequests.increment();
                    } else {
                        failedRequests.increment();
                    }
                })
                // Error handling...
        );
    }
    
    // Other methods...
}
```

### ModuleClientFactory

The `ModuleClientFactory` class creates and manages the gRPC client for connecting to the backend module.

```java
@ApplicationScoped
public class ModuleClientFactory {
    @ConfigProperty(name = "module.host", defaultValue = "localhost")
    String moduleHost;
    
    @ConfigProperty(name = "module.port", defaultValue = "9091")
    int modulePort;
    
    // ...
    
    public MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub createClient() {
        // Create and return the client
    }
}
```

### Health Check

The `ProxyHealthCheck` class implements the Microprofile Health `HealthCheck` interface to verify that the proxy can connect to the backend module.

## Deployment Options

The proxy service can be deployed in several ways:

1. **Sidecar Container**: Deploy the proxy as a sidecar container alongside the module in Kubernetes
2. **Single Container**: Package both the proxy and module in a single container
3. **Separate Containers**: Deploy the proxy and module as separate containers

## Benefits

1. **Separation of Concerns**: Module developers focus on business logic, while the proxy handles infrastructure concerns
2. **Standardized Infrastructure**: Consistent security, monitoring, and logging across modules
3. **Technology Flexibility**: Modules can be implemented in any language that supports gRPC
4. **Operational Advantages**: Simplified deployment and management