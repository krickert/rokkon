# Module Recovery Strategy

## Priority Order for Module Recovery
Based on analysis of backup modules and their importance:

1. **tika-parser**: Critical document parsing functionality
2. **embedder**: Essential for semantic processing  
3. **chunker**: Required for document segmentation
4. **Additional modules**: As needed for specific pipelines

## Recovery Process for Each Module

### Step-by-Step Recovery
1. **Create Module Structure**: Use Quarkus CLI exactly as in echo module
2. **Configure Build**: Copy build.gradle.kts pattern exactly from echo
3. **Implement Service**: Follow @GrpcService + @Singleton pattern
4. **Create Tests**: Implement both unit and integration tests with abstract base
5. **Migrate Business Logic**: Preserve core logic from backup modules
6. **Validate**: Ensure 100% test pass rate before proceeding

### Business Logic Migration
- **Preserve**: Core processing algorithms and domain logic
- **Modernize**: Framework integration and configuration handling
- **Enhance**: Error handling and observability
- **Test**: Comprehensive validation of migrated functionality

## Module Implementation Pattern

### Module Creation Command
```bash
mkdir -p modules/[module-name]
cd modules/[module-name]
quarkus create app com.rokkon.pipeline:[module-name] \
  --java=21 \
  --gradle-kotlin-dsl \
  --extension=grpc,config-yaml,container-image-docker
```

### Build Configuration
Follow the exact pattern from echo module:

```kotlin
plugins {
    java
    alias(libs.plugins.quarkus)
    `maven-publish`
}

dependencies {
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation(libs.quarkus.grpc)
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-arc")
    
    // Proto definitions from shared project
    implementation("com.rokkon.pipeline:proto-definitions:1.0.0-SNAPSHOT")
    
    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.assertj)
}

// Configure Quarkus to use Mutiny for gRPC code generation
quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}

// CRITICAL: Extract proto files from jar for local stub generation
val extractProtos = tasks.register<Copy>("extractProtos") {
    from(zipTree(configurations.runtimeClasspath.get().filter { it.name.contains("proto-definitions") }.singleFile))
    include("**/*.proto")
    into("src/main/proto")
    includeEmptyDirs = false
}

tasks.named("quarkusGenerateCode") {
    dependsOn(extractProtos)
}
```

### Service Implementation
Follow the @GrpcService + @Singleton pattern:

```java
@GrpcService
@Singleton
public class TikaParserServiceImpl implements PipeStepProcessor {
    
    private static final Logger LOG = Logger.getLogger(TikaParserServiceImpl.class);
    
    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        // Module-specific business logic
        // Preserve logic from backup modules
        return Uni.createFrom().item(() -> {
            // Implementation details
        });
    }
    
    @Override
    public Uni<ServiceRegistrationData> getServiceRegistration(Empty request) {
        // Module metadata and capabilities
        return Uni.createFrom().item(ServiceRegistrationData.newBuilder()
            .setModuleName("tika-parser")
            .build());
    }
}
```

### Testing Structure
Create abstract test base class:

```java
public abstract class TikaParserServiceTestBase {
    
    protected abstract PipeStepProcessor getTikaParserService();
    
    @Test
    void testProcessDataWithValidDocument() {
        // Create test document
        // Execute service call
        // Verify response
    }
    
    @Test
    void testGetServiceRegistration() {
        // Test service registration
    }
}
```

Create unit test implementation:

```java
@QuarkusTest
class TikaParserServiceTest extends TikaParserServiceTestBase {
    
    @GrpcClient
    PipeStepProcessor pipeStepProcessor;
    
    @Override
    protected PipeStepProcessor getTikaParserService() {
        return pipeStepProcessor;
    }
}
```

Create integration test implementation:

```java
@QuarkusIntegrationTest
public class TikaParserServiceIT extends TikaParserServiceTestBase {
    
    private ManagedChannel channel;
    private PipeStepProcessor pipeStepProcessor;
    
    @BeforeEach
    void setup() {
        // Setup external gRPC client
    }
    
    @AfterEach
    void cleanup() {
        // Cleanup resources
    }
    
    @Override
    protected PipeStepProcessor getTikaParserService() {
        return pipeStepProcessor;
    }
}
```

## Module-Specific Recovery Notes

### Tika Parser Recovery
1. **Core Logic**: Preserve DocumentParser.java with its comprehensive parsing logic
2. **Configuration**: Migrate TikaConfiguration to Quarkus ConfigProperties
3. **Error Handling**: Enhance error reporting with structured details
4. **Testing**: Create tests with various document types

### Embedder Recovery
1. **Core Logic**: Preserve embedding generation algorithms
2. **Model Loading**: Update model loading for Quarkus compatibility
3. **Caching**: Implement efficient caching mechanisms
4. **Testing**: Test with various embedding models and document types

### Chunker Recovery
1. **Core Logic**: Preserve chunking algorithms and strategies
2. **Configuration**: Migrate chunking configuration options
3. **Performance**: Optimize for memory efficiency
4. **Testing**: Test with various document sizes and chunking strategies

## Success Criteria

### Technical Requirements
- All modules have 100% passing unit and integration tests
- Modules follow exact echo module pattern
- Proto extraction works correctly
- No custom modifications to build process

### Functional Requirements
- Core business logic preserved from backup modules
- All edge cases handled properly
- Error handling is comprehensive
- Performance meets or exceeds original modules

### Documentation Requirements
- Clear module documentation
- Configuration options documented
- Test coverage documented
- Integration examples provided

## Validation Process

### Unit Testing
```bash
./gradlew test
```

### Integration Testing
```bash
./gradlew quarkusIntTest
```

### Manual Verification
1. Start the module: `./gradlew quarkusDev`
2. Test with gRPC client: `grpcurl -plaintext localhost:9090 list`
3. Verify service registration: `grpcurl -plaintext localhost:9090 com.rokkon.search.sdk.PipeStepProcessor/GetServiceRegistration`
4. Test processing: `grpcurl -plaintext -d '{"document": {"id": "test-1"}}' localhost:9090 com.rokkon.search.sdk.PipeStepProcessor/ProcessData`

## Conclusion
By following this module recovery strategy exactly, we can ensure consistent, reliable module implementations that integrate seamlessly with the Rokkon Engine. The focus on preserving core business logic while modernizing the framework integration will result in modules that are both functionally equivalent and architecturally superior to the original implementations.