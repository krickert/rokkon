# Engine Consul Configuration Testing Challenges

## Problem Statement

The engine:pipestream module has Consul configuration enabled (`quarkus.consul-config.enabled=true`), which means:
1. The application tries to connect to Consul at startup to fetch configuration
2. If Consul is not available or doesn't have the expected config keys, the application fails to start
3. This makes unit tests fail because they start the Quarkus context but don't have Consul running

## Configuration Dependencies

From the test output, we see these Consul-related config properties:
- `quarkus.consul-config.enabled`
- `quarkus.consul-config.agent.host`
- `quarkus.consul-config.agent.port`
- `quarkus.consul-config.format`
- `quarkus.consul-config.watch.enabled`
- `quarkus.consul-config.watch.period`

## Solution Approaches

### 1. For Unit Tests - Disable Consul Config
Create a test profile that disables Consul configuration entirely:

```java
public class NoConsulConfigTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Disable Consul configuration
            "quarkus.consul-config.enabled", "false",
            "quarkus.consul.enabled", "false",
            
            // Provide any required config values directly
            "rokkon.cluster.name", "test-cluster",
            "rokkon.engine.name", "test-engine",
            // Add other required config here
        );
    }
}
```

### 2. For Integration Tests - Use ConsulContainer with Config Seeding
For tests that need real Consul interaction:

```java
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResourceWithConfig.class)
public class EngineWithConsulIT {
    // Test real Consul integration
}

public class ConsulTestResourceWithConfig implements QuarkusTestResourceLifecycleManager {
    private ConsulContainer consul;
    
    @Override
    public Map<String, String> start() {
        consul = new ConsulContainer("consul:latest");
        consul.start();
        
        // Seed configuration BEFORE the engine starts
        seedConsulConfiguration();
        
        return Map.of(
            "quarkus.consul.host", consul.getHost(),
            "quarkus.consul.port", String.valueOf(consul.getMappedPort(8500)),
            "quarkus.consul-config.agent.host", consul.getHost(),
            "quarkus.consul-config.agent.port", String.valueOf(consul.getMappedPort(8500))
        );
    }
    
    private void seedConsulConfiguration() {
        // Use Consul client to seed required KV pairs
        ConsulClient client = new ConsulClient(consul.getHost(), consul.getMappedPort(8500));
        
        // Seed engine configuration
        client.setKVValue("config/rokkon-engine/application", """
            rokkon:
              cluster:
                name: test-cluster
              engine:
                name: test-engine
              modules:
                whitelist:
                  - echo
                  - test-module
            """);
        
        // Seed any other required configuration
        client.setKVValue("config/rokkon-engine/database", """
            quarkus:
              datasource:
                db-kind: h2
                jdbc:
                  url: jdbc:h2:mem:test
            """);
    }
}
```

### 3. Abstract Getter Pattern for Engine Tests
Similar to dynamic-grpc, we can create an abstract base:

```java
public abstract class AbstractEngineTestBase {
    protected ConsulClient consulClient;
    protected ClusterService clusterService;
    
    protected abstract ConsulClient getConsulClient();
    protected abstract ClusterService getClusterService();
    
    @BeforeEach
    void setupBase() {
        additionalSetup();
        consulClient = getConsulClient();
        clusterService = getClusterService();
    }
    
    protected void additionalSetup() {}
}

// Unit test implementation
class EngineUnitTest extends AbstractEngineTestBase {
    private MockConsulClient mockConsul = new MockConsulClient();
    private MockClusterService mockCluster = new MockClusterService();
    
    @Override
    protected ConsulClient getConsulClient() {
        return mockConsul;
    }
    
    @Override
    protected ClusterService getClusterService() {
        return mockCluster;
    }
}
```

## Key Insights

1. **Configuration Bootstrap Order**: Consul config is fetched BEFORE CDI beans are created, so we need Consul running and seeded before Quarkus starts

2. **Config Format**: The engine expects YAML format in Consul KV store (note the `quarkus.consul-config.format` property)

3. **Watch Mechanism**: The engine watches for config changes (`quarkus.consul-config.watch.enabled`), which could cause test flakiness if not handled properly

4. **Multiple Config Sources**: The engine uses both local application.yml AND Consul config, with Consul taking precedence

## Recommended Test Strategy

1. **Unit Tests**: 
   - Use `@TestProfile(NoConsulConfigTestProfile.class)`
   - Provide all required config via the profile
   - Mock all Consul-dependent services

2. **Component Tests**:
   - Use `@TestProfile(MockConsulTestProfile.class)` 
   - Provide minimal config to start the app
   - Mock external service calls

3. **Integration Tests**:
   - Use real ConsulContainer with proper seeding
   - Test actual Consul config fetching and watching
   - Verify config changes propagate correctly

## Critical Config Keys to Seed

Based on the errors, these config keys are likely required:
- `rokkon.cluster.name`
- `rokkon.engine.name`
- `rokkon.modules.whitelist`
- Database configuration (if using persistence)
- gRPC service configuration
- Any custom business configuration

## Example: Comprehensive Test Profile

```java
public class EngineTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        
        // Consul settings
        config.put("quarkus.consul-config.enabled", "false");
        config.put("quarkus.consul.enabled", "false");
        
        // Engine configuration
        config.put("rokkon.cluster.name", "test-cluster");
        config.put("rokkon.engine.name", "test-engine");
        config.put("rokkon.engine.startup.create-cluster", "false");
        
        // Module configuration
        config.put("rokkon.modules.whitelist[0]", "echo");
        config.put("rokkon.modules.whitelist[1]", "test-module");
        
        // gRPC configuration
        config.put("quarkus.grpc.server.port", "0"); // Random port
        config.put("quarkus.grpc.server.test-port", "0");
        
        // Stork service discovery
        config.put("quarkus.stork.service-discovery.enabled", "false");
        
        // Health checks
        config.put("quarkus.health.extensions.enabled", "false");
        
        // Logging
        config.put("quarkus.log.level", "INFO");
        config.put("quarkus.log.category.\"com.rokkon\".level", "DEBUG");
        
        return config;
    }
}
```

## Next Steps

1. Create a `NoConsulConfigTestProfile` for unit tests
2. Update all `@QuarkusTest` annotations to use this profile
3. Create `ConsulTestResourceWithConfig` for integration tests that properly seeds config
4. Document which config keys are required for the engine to start
5. Consider creating a `TestConfigSeeder` utility class that can be reused across tests

## Lessons Learned

1. **Consul Config is Early Binding**: Unlike CDI beans, Consul config is fetched during Quarkus startup, before the application context is fully initialized

2. **Test Profiles are Critical**: Without proper test profiles, tests will try to connect to Consul and fail

3. **Config Seeding Must Be Complete**: Missing even one required config key can cause startup failures

4. **Integration Tests Need Real Config**: For true integration tests, we need to seed Consul with production-like configuration

This is indeed a very complex testing scenario that requires careful handling of configuration at multiple levels!