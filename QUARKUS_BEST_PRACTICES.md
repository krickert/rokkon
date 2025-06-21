# Quarkus Best Practices - Critical Notes

## Testing Structure (CRITICAL)
1. **Three-tier test structure:**
   - `XxxTestBase` - Abstract base class with all test logic
   - `XxxTest extends XxxTestBase` - Unit test with `@QuarkusTest`
   - `XxxIT extends XxxTestBase` - Integration test with `@QuarkusIntegrationTest`

2. **Test location:**
   - Unit tests: `src/test/java`
   - Integration tests: `src/integrationTest/java`
   - Both extend the SAME base class

3. **Example structure:**
```java
// src/test/java/.../ServiceTestBase.java
public abstract class ServiceTestBase {
    protected abstract MyService getService();
    
    @Test
    void testSomething() {
        // Test logic using getService()
    }
}

// src/test/java/.../ServiceTest.java
@QuarkusTest
class ServiceTest extends ServiceTestBase {
    @Inject
    MyService service;  // Package-private injection!
    
    @Override
    protected MyService getService() {
        return service;
    }
}

// src/integrationTest/java/.../ServiceIT.java
@QuarkusIntegrationTest
class ServiceIT extends ServiceTestBase {
    // May need different setup for integration
    @Override
    protected MyService getService() {
        // Return client or injected service
    }
}
```

## Dependency Injection Best Practices
1. **NEVER use constructor injection in Quarkus**
2. **ALWAYS use field injection with package-private visibility**
3. **NO private fields for injection**

```java
// WRONG
@ApplicationScoped
public class MyService {
    private final OtherService other;
    
    @Inject
    public MyService(OtherService other) {  // DON'T DO THIS
        this.other = other;
    }
}

// WRONG
@ApplicationScoped
public class MyService {
    @Inject
    private OtherService other;  // DON'T use private
}

// CORRECT
@ApplicationScoped
public class MyService {
    @Inject
    OtherService other;  // Package-private, no constructor
}
```

## Configuration Best Practices
1. **Environment-specific configs:**
   - `application.yml` - Base/shared config
   - `application-dev.yml` - Development overrides
   - `application-test.yml` - Test overrides
   - `application-prod.yml` - Production config

2. **Use profiles:**
```yaml
# application.yml
quarkus:
  http:
    port: 8080

# application-test.yml
quarkus:
  http:
    test-port: 0  # Random port for tests
```

## Reactive Programming with Uni
1. **ALWAYS use Uni for async operations**
2. **Convert CompletionStage/CompletableFuture to Uni**
3. **Use Infrastructure.getDefaultExecutor() for blocking operations**

```java
// WRONG - Using CompletionStage
public CompletionStage<Result> doSomething() {
    return CompletableFuture.supplyAsync(() -> blockingOperation());
}

// CORRECT - Using Uni
public Uni<Result> doSomething() {
    return Uni.createFrom().item(() -> blockingOperation())
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
}
```

## Testing Best Practices
1. **Avoid mocking when possible** - Use real implementations
2. **Use @QuarkusTestResource for external dependencies**
3. **Integration tests should test the full stack**
4. **Base test classes reduce duplication**

## Common Pitfalls to Avoid
1. **Don't use @Mock in Quarkus tests** - Use @InjectMock sparingly
2. **Don't use private injection** - Always package-private
3. **Don't use constructor injection** - Field injection only
4. **Don't mix CompletionStage with Uni** - Uni everywhere
5. **Don't put integration tests in src/test** - Use integrationTest
6. **Don't repeat test logic** - Use base test classes

## Module Pattern
1. **Modules are "dumb" gRPC services**
2. **CLI runs INSIDE module containers**
3. **Only engine-consul writes to Consul**
4. **Use RegistrationCheck for validation**

## Key Annotations
- `@QuarkusTest` - For unit tests (dev mode)
- `@QuarkusIntegrationTest` - For integration tests (prod mode)
- `@Inject` - Always package-private
- `@ApplicationScoped` - Default scope for services
- `@Singleton` - For gRPC services
- `@GrpcService` - For gRPC service implementations

## CRITICAL: Protobuf Class Loading in Tests
**DO NOT DELETE THE FORCE LOADING CODE IN ProtobufTestDataHelper!**

Due to Quarkus classloading timing issues, protobuf classes MUST be loaded using reflection:
```java
// !!!! CRITICAL - DO NOT DELETE THIS FORCE LOADING !!!!
ClassLoader cl = Thread.currentThread().getContextClassLoader();
Class<?> pipeDocClass = cl.loadClass("com.rokkon.PipeDoc");
```

**NEVER simplify this to direct class references** - it will cause ClassNotFoundException in tests!