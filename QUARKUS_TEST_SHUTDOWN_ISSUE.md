# Quarkus Test Shutdown Context Error

## Problem Description

We're experiencing a persistent issue when running multiple Quarkus test classes together in the same test run. The error occurs with the first test that runs and manifests as:

```
java.lang.RuntimeException: Internal error: shutdownContext is null. This probably happened because Quarkus failed to start properly in an earlier step, or because tests were run on a Quarkus instance that had already been shut down.
    at io.quarkus.runtime.configuration.ConfigRecorder.releaseConfig(ConfigRecorder.java:109)
```

## Environment
- Quarkus version: 3.24.1
- Java: 21
- Testing with JUnit 5
- Using @QuarkusTest with @TestProfile

## Symptoms

1. When running tests individually or by class, they all pass
2. When running all tests together, the first test class fails with the shutdown context error
3. After the first test fails, all subsequent test classes are skipped/ignored
4. The error always occurs on `testConsulCleanupProperties()` method when it's the first test executed
5. If we disable that test, the error moves to the next test method

## Current Setup

We created a UnifiedTestProfile to avoid profile conflicts:
- All tests use the same @TestProfile(UnifiedTestProfile.class)
- The profile returns "test-unified" as the profile name
- We have proper application-test-unified.yml configuration

## Test Structure

```java
@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
@DisplayName("Configuration Property Tests")
class ConsulConfigSourceSimpleTest {
    @ConfigProperty(name = "rokkon.engine.grpc-port")
    int grpcPort;
    
    @Test
    void testConsulCleanupProperties() {
        // This test fails with shutdown context error
    }
}
```

## What We've Tried

1. Created UnifiedTestProfile to avoid profile switching
2. Set per_method lifecycle in junit-platform.properties
3. Disabled parallel execution
4. Created separate application-test-unified.yml
5. Added @TestInstance(TestInstance.Lifecycle.PER_CLASS)

## Questions for Research

1. Why does Quarkus think the shutdown context is null when tests run together?
2. Is there a race condition in Quarkus test initialization when multiple @QuarkusTest classes run in sequence?
3. Are there known issues with @ConfigProperty injection in tests when using custom TestProfiles?
4. Is there a proper way to ensure Quarkus properly shuts down between test classes?

## Stack Trace Details

```
Caused by: java.lang.RuntimeException: Failed to start quarkus
    at io.quarkus.runner.ApplicationImpl.doStart(Unknown Source)
    at io.quarkus.runtime.Application.start(Application.java:101)
    ...
Caused by: java.lang.RuntimeException: Internal error: shutdownContext is null
    at io.quarkus.runtime.configuration.ConfigRecorder.releaseConfig(ConfigRecorder.java:109)
```

## Search Terms
- "Quarkus shutdownContext is null test"
- "Quarkus ConfigRecorder.releaseConfig error"
- "Quarkus multiple test classes shutdown context"
- "QuarkusTest failed to start properly earlier step"