# Consul Project Integration Tests

This document provides a comprehensive list of all integration tests in the Consul project, along with instructions for creating `@QuarkusIntegrationTest` versions of each test.

## Overview

All integration tests in the Consul project are located in the `engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/` directory and its subdirectories. These tests are designed to validate the functionality of the Consul-related components with real Consul instances using Testcontainers.

## Integration Tests List

### Main Directory Tests

1. **BasicConsulConnectionIT** (FIXED)
   - **Purpose**: Verifies that Consul is running and dependencies can be injected.
   - **Status**: Already using `@QuarkusIntegrationTest`
   - **Location**: `engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/BasicConsulConnectionIT.java`

2. **ConsulConfigIsolatedIT**
   - **Purpose**: Demonstrates how to use isolated Consul KV namespaces for consul-config tests.
   - **Status**: Already using `@QuarkusIntegrationTest`
   - **Location**: `engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/ConsulConfigIsolatedIT.java`

3. **ConsulConfigLoadingIT**
   - **Purpose**: Tests that consul-config actually loads configuration from Consul KV.
   - **Status**: Already using `@QuarkusIntegrationTest`
   - **Location**: `engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/ConsulConfigLoadingIT.java`

4. **ConsulConfigSuccessFailIT**
   - **Purpose**: Tests both success and failure cases for consul-config.
   - **Status**: Already using `@QuarkusIntegrationTest`
   - **Location**: `engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/ConsulConfigSuccessFailIT.java`

5. **IsolatedConsulKvIT**
   - **Purpose**: Demonstrates isolated Consul KV writes for parallel test execution.
   - **Status**: Already using `@QuarkusIntegrationTest`
   - **Location**: `engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/IsolatedConsulKvIT.java`

6. **MethodicalBuildUpIT**
   - **Purpose**: Tests the methodical build-up of the engine ecosystem layer by layer.
   - **Status**: Already using `@QuarkusIntegrationTest`
   - **Location**: `engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/MethodicalBuildUpIT.java`

7. **ParallelConsulKvIT**
   - **Purpose**: Demonstrates that test isolation works for parallel execution.
   - **Status**: Already using `@QuarkusIntegrationTest`
   - **Location**: `engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/ParallelConsulKvIT.java`

### API Subdirectory Tests

8. **ClusterResourceIT**
   - **Purpose**: Tests the REST API for cluster management.
   - **Status**: Already using `@QuarkusIntegrationTest`
   - **Location**: `engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/api/ClusterResourceIT.java`

9. **PipelineConfigResourceIT**
   - **Purpose**: Tests the pipeline configuration REST API.
   - **Status**: Already using `@QuarkusIntegrationTest`
   - **Location**: `engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/api/PipelineConfigResourceIT.java`

### Service Subdirectory Tests

10. **ClusterServiceIT**
    - **Purpose**: Tests the cluster service.
    - **Status**: Already using `@QuarkusIntegrationTest`
    - **Location**: `engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/service/ClusterServiceIT.java`

11. **ModuleWhitelistServiceContainerIT**
    - **Purpose**: Container-based integration test for ModuleWhitelistService using real Docker containers.
    - **Status**: Already using `@QuarkusIntegrationTest`
    - **Location**: `engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/service/ModuleWhitelistServiceContainerIT.java`

12. **ModuleWhitelistServiceIT**
    - **Purpose**: Tests the module whitelist service with real Consul and Docker containers.
    - **Status**: Already using `@QuarkusIntegrationTest`
    - **Location**: `engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/service/ModuleWhitelistServiceIT.java`

13. **ModuleWhitelistServiceSimpleIT**
    - **Purpose**: Simple test to verify ModuleWhitelistService is properly injected.
    - **Status**: Already using `@QuarkusIntegrationTest`
    - **Location**: `engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/service/ModuleWhitelistServiceSimpleIT.java`

14. **PipelineConfigServiceIT**
    - **Purpose**: Tests the pipeline config service running in prod mode.
    - **Status**: Already using `@QuarkusIntegrationTest`
    - **Location**: `engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/service/PipelineConfigServiceIT.java`

15. **PipelineConfigServiceTest**
    - **Purpose**: Tests the pipeline config service with real Consul backend.
    - **Status**: Already using `@QuarkusIntegrationTest`
    - **Location**: `engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/service/PipelineConfigServiceTest.java`

## Creating a New @QuarkusIntegrationTest

All the integration tests in the Consul project are already using `@QuarkusIntegrationTest`. However, if you need to create a new integration test, follow these steps:

1. Create a new test class in the appropriate directory under `engine/consul/src/integrationTest/java/com/rokkon/pipeline/consul/`.
2. Add the `@QuarkusIntegrationTest` annotation to the class.
3. Add the `@QuarkusTestResource(ConsulTestResource.class)` annotation to use the Consul test container.
4. If your test needs to interact with Docker containers, add the appropriate `@QuarkusTestResource` annotation for the container.
5. Extend the appropriate base class if available, or implement the test logic directly.
6. Use dependency injection (`@Inject`) to get the services you need to test.
7. Implement test methods with `@Test` annotations.

### Example:

```java
package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
class MyNewIntegrationTest {
    
    @Inject
    MyService myService;
    
    @Test
    void testMyService() {
        // Test logic here
        assertThat(myService).isNotNull();
    }
}
```

## Special Considerations

1. **Isolated Testing**: Use `IsolatedConsulKvIntegrationTestBase` for tests that need isolated Consul KV namespaces.
2. **Container Testing**: Use `ModuleContainerResource` or a custom extension of it for tests that need Docker containers.
3. **Test Profiles**: Use `QuarkusTestProfile` implementations to configure test-specific properties.
4. **Cleanup**: Always clean up after your tests to avoid interference with other tests.

## Running Integration Tests

To run all integration tests in the Consul project:

```bash
./gradlew :engine:consul:integrationTest
```

To run a specific integration test:

```bash
./gradlew :engine:consul:integrationTest --tests "com.rokkon.pipeline.consul.MyIntegrationTest"
```