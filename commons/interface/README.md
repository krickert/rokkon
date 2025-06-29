# Rokkon Commons

## Overview
Rokkon Commons is a shared utility library that provides common functionality used across all Rokkon Engine components and modules. It contains reusable code for gRPC utilities, test helpers, configuration models, and other cross-cutting concerns.

## Purpose

This library exists to:
- Reduce code duplication across modules
- Provide consistent implementations of common patterns
- Share test utilities and helpers
- Maintain a single source of truth for common functionality

## Key Components

### 1. gRPC Utilities
- **ProtobufHelpers**: Utilities for working with Protocol Buffer messages
- **GrpcExceptionHandler**: Consistent error handling for gRPC services
- **MetadataUtils**: Helper methods for gRPC metadata manipulation

### 2. Test Utilities
- **ProtobufTestDataHelper**: Loads test protobuf messages from resources
- **TestDocumentGenerator**: Creates sample documents for testing
- **GrpcTestHelpers**: Utilities for testing gRPC services

### 3. Configuration Helpers
- **ConfigValidators**: Common validation logic for configurations
- **EnvironmentUtils**: Environment variable and property helpers
- **JsonSchemaValidator**: JSON Schema validation utilities

### 4. Common Models
- **ServiceHealthStatus**: Shared health check models
- **PipelineMetrics**: Common metrics structures
- **ErrorCategories**: Standardized error categorization

## Usage

### Adding to Your Project

```kotlin
dependencies {
    implementation("com.rokkon.pipeline:rokkon-commons:1.0.0-SNAPSHOT")
}
```

### Example: Using ProtobufTestDataHelper

```java
import com.rokkon.test.data.ProtobufTestDataHelper;

// Load test documents from resources
List<Document> testDocs = ProtobufTestDataHelper.loadFromDirectory(
    "test-documents/raw", 
    Document::parseFrom
);

// Load chunker output
List<PipeDoc> chunks = ProtobufTestDataHelper.loadFromDirectory(
    "test-documents/chunker-pipe-docs",
    PipeDoc::parseFrom
);
```

### Example: Using GrpcExceptionHandler

```java
import com.rokkon.grpc.GrpcExceptionHandler;

@GrpcService
public class MyServiceImpl implements MyService {
    
    @Override
    public Uni<Response> processRequest(Request request) {
        return doProcessing(request)
            .onFailure().transform(GrpcExceptionHandler::handleException);
    }
}
```

## Project Structure

```
rokkon-commons/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/rokkon/
│   │   │       ├── grpc/         # gRPC utilities
│   │   │       ├── config/       # Configuration helpers
│   │   │       ├── test/         # Test utilities
│   │   │       └── util/         # General utilities
│   │   └── resources/
│   └── test/
│       ├── java/                  # Unit tests
│       └── resources/             # Test resources
├── build.gradle.kts
└── README.md
```

## Development

### Building

```bash
# Build the library
./gradlew build

# Publish to Maven Local
./gradlew publishToMavenLocal

# Run tests
./gradlew test
```

### Adding New Utilities

When adding new utilities:
1. Consider if it's truly reusable across multiple modules
2. Add comprehensive unit tests
3. Document the utility with JavaDoc
4. Update this README if adding a new category

### Best Practices

1. **Keep it Simple**: Only add utilities that are used by multiple modules
2. **Avoid Dependencies**: Minimize external dependencies to reduce conflicts
3. **Backward Compatibility**: Changes should not break existing modules
4. **Clear Documentation**: Every public API should have JavaDoc

## Dependencies

Minimal dependencies to avoid conflicts:
- Quarkus Arc (CDI)
- Protocol Buffers
- JUnit 5 (test scope)
- AssertJ (test scope)

## Testing

All utilities should have comprehensive unit tests:

```java
@Test
void testProtobufHelper() {
    // Given
    String testDir = "test-documents/raw";
    
    // When
    List<Document> docs = ProtobufTestDataHelper.loadFromDirectory(
        testDir, Document::parseFrom
    );
    
    // Then
    assertThat(docs).isNotEmpty();
    assertThat(docs.get(0).getId()).isNotBlank();
}
```

## Version Management

This library follows the same versioning as the main Rokkon Engine:
- Current version: 1.0.0-SNAPSHOT
- Released versions will follow semantic versioning

## Contributing

When contributing to rokkon-commons:
1. Ensure the utility is genuinely reusable
2. Add unit tests with >80% coverage
3. Update documentation
4. Consider backward compatibility

## Common Pitfalls

1. **Don't add module-specific code**: This is for shared utilities only
2. **Avoid heavy dependencies**: Keep the library lightweight
3. **Don't break existing APIs**: Use deprecation for changes
4. **Test thoroughly**: Bugs here affect all modules