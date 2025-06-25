# Rokkon Test Configuration Guide

This document explains how the test infrastructure is configured to avoid port conflicts and ensure reliable test execution.

## Overview

The Rokkon project uses Quarkus for microservices and gRPC for inter-service communication. Tests need to start multiple services that bind to HTTP and gRPC ports, which can cause conflicts during parallel execution.

## Key Configuration Changes

### 1. Dynamic Port Allocation

All test configurations use port `0` to let the OS assign random available ports:

```yaml
# src/test/resources/application.yml
quarkus:
  http:
    port: 0          # Random port for HTTP
    test-port: 0     # Random port for HTTP tests
  grpc:
    server:
      port: 0        # Random port for gRPC
      test-port: 0   # Random port for gRPC tests
```

### 2. JUnit Platform Configuration

Due to Quarkus startup limitations, some modules need sequential test execution:

```properties
# src/test/resources/junit-platform.properties

# For modules with Quarkus startup conflicts (test-module, engine/consul):
junit.jupiter.execution.parallel.enabled=false
junit.jupiter.testinstance.lifecycle.default=per_class

# For other modules that can run in parallel:
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent
junit.jupiter.execution.parallel.mode.classes.default=concurrent
junit.jupiter.testinstance.lifecycle.default=per_class
junit.jupiter.execution.parallel.config.strategy=dynamic
junit.jupiter.execution.parallel.config.dynamic.factor=0.5
```

### 3. Test Port Injection

Tests should inject the actual assigned ports instead of hardcoding them:

```java
@QuarkusTest
class MyTest {
    @ConfigProperty(name = "quarkus.http.test-port")
    int httpTestPort;
    
    @ConfigProperty(name = "quarkus.grpc.server.test-port", defaultValue = "0")
    int grpcTestPort;
    
    @Test
    void testSomething() {
        // Use httpTestPort and grpcTestPort instead of hardcoded values
    }
}
```

### 4. Dynamic Port Helper for Integration Tests

For integration tests that need to simulate external services:

```java
public abstract class ConsulIntegrationTestBase {
    /**
     * Find an available port for testing.
     */
    protected static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Could not find available port", e);
        }
    }
}
```

## Module-Specific Configurations

### test-module
- **Parallel Execution**: Disabled (Quarkus startup conflicts)
- **Test Results**: 33/33 passing
- **Key Files**:
  - `modules/test-module/src/test/resources/application.yml`
  - `modules/test-module/src/test/resources/junit-platform.properties`

### engine/consul
- **Parallel Execution**: Disabled (Quarkus startup conflicts)
- **Key Changes**: 
  - Updated `GlobalModuleRegistryServiceIT` to use `findAvailablePort()`
  - Added port helper to `ConsulIntegrationTestBase`

### engine/seed-config
- **Parallel Execution**: Enabled
- **Test Results**: 19/19 passing
- **Key Files**:
  - `engine/seed-config/src/test/resources/application.properties`
  - `engine/seed-config/src/test/resources/junit-platform.properties`

### rokkon-engine
- **Parallel Execution**: Enabled
- **Key Changes**:
  - Changed `ModuleRegistrationTest` from port 9001 to 0
  - Created `junit-platform.properties`

### engine/validators
- **Parallel Execution**: Enabled
- **Key Files**:
  - `engine/validators/src/test/resources/application.yml`
  - `engine/validators/src/test/resources/junit-platform.properties`

## Common Issues and Solutions

### Issue: "Port already bound" errors
**Solution**: Ensure test configuration uses port 0 and disable parallel execution if needed

### Issue: Hardcoded ports in tests
**Solution**: Replace with `@ConfigProperty` injection or `findAvailablePort()`

### Issue: Tests fail when run together but pass individually
**Solution**: Disable parallel execution in `junit-platform.properties`

### Issue: "shutdownContext is null" errors
**Solution**: This indicates Quarkus startup conflicts - disable parallel execution

## Running Tests

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :modules:test-module:test

# Run with fresh build (no cache)
./gradlew test --no-build-cache

# Run specific test class
./gradlew test --tests "com.rokkon.testmodule.health.SimpleHealthCheckTest"
```

## Best Practices

1. **Always use random ports** in test configurations (port: 0)
2. **Inject ports** using `@ConfigProperty` instead of hardcoding
3. **Disable parallel execution** for modules with Quarkus startup issues
4. **Use findAvailablePort()** for integration tests needing dynamic ports
5. **Clean up resources** in @AfterEach methods to prevent leaks

## Troubleshooting

1. **Check test reports**: `build/reports/tests/test/index.html`
2. **Look for port conflicts**: Search logs for "Address already in use"
3. **Verify configuration**: Ensure `application.yml` and `junit-platform.properties` exist in test resources
4. **Test isolation**: Run failing test class individually to confirm it's a conflict issue

## Future Improvements

1. Investigate Quarkus test framework updates that might allow parallel execution
2. Consider using test containers for better isolation
3. Implement automatic port conflict detection and retry
4. Add CI/CD specific configurations for different environments