# Bill of Materials (BOM) Structure

## Overview

The Rokkon project uses a hierarchical Bill of Materials (BOM) structure to manage dependencies across different types of projects. This approach ensures consistency, reduces duplication, and makes dependency management more maintainable.

## BOM Hierarchy

```
bom:base
  ├── bom:library
  ├── bom:cli
  ├── bom:module
  └── bom:server
```

### Base BOM (`bom:base`)

The foundation BOM that includes only the Quarkus BOM. All other BOMs depend on this.

**Key characteristics:**
- Contains only `quarkus-bom` dependency
- Parent for all other BOMs
- Ensures consistent Quarkus version across the entire project

### Library BOM (`bom:library`)

For shared libraries and engine components that don't need server capabilities.

**Use for:**
- Engine components (validators, consul)
- Shared utilities
- Libraries that other projects depend on

**Provides:**
- CDI support (`quarkus-arc`)
- gRPC code generation (`quarkus-grpc`)
- Protobuf and gRPC stubs
- Jackson for JSON processing
- Common utilities (Apache Commons, Commons IO)

**Note:** Test dependencies like `awaitility` should be declared with explicit versions in individual projects, not in the BOM.

### CLI BOM (`bom:cli`)

For command-line applications.

**Use for:**
- Command-line tools (register-module, seed-engine-consul-config)
- Batch processing utilities

**Provides:**
- Picocli for CLI functionality
- REST client capabilities
- Configuration support
- Explicitly excludes `quarkus-grpc` to avoid server components

**Important:** CLI apps requiring gRPC code generation should use:
```kotlin
compileOnly("io.quarkus:quarkus-grpc")
```

### Module BOM (`bom:module`)

For pipeline processing modules that participate in the data pipeline.

**Use for:**
- Pipeline modules (chunker, parser, embedder, echo)
- Connector modules

**Provides:**
- Full gRPC server and client support
- REST endpoints
- Health checks and metrics
- Container image building
- Service discovery components

### Server BOM (`bom:server`)

For full server applications with complete observability and API capabilities.

**Use for:**
- Main engine server (engine:pipestream)
- Future connector services

**Provides:**
- Everything from library BOM
- Full REST and gRPC server support
- Validation support (`quarkus-hibernate-validator`)
- OpenAPI/Swagger documentation
- Metrics and monitoring (Micrometer, Prometheus)
- Health checks
- Container support

## Migration Patterns

### 1. Identify Project Type

Before migrating, determine which BOM is appropriate:

```
Is it a CLI tool? → cli BOM
Is it a pipeline module? → module BOM  
Is it a full server? → server BOM
Is it a library/engine component? → library BOM
```

### 2. Update build.gradle.kts

Replace the old BOM reference:

```kotlin
// Old
dependencies {
    implementation(platform(project(":rokkon-bom")))
    // ... dependencies
}

// New
dependencies {
    implementation(platform(project(":bom:module"))) // or appropriate BOM
    // ... only project-specific dependencies
}
```

### 3. Remove Redundant Dependencies

After switching to the new BOM, remove dependencies that are now provided:

```kotlin
// Before
dependencies {
    implementation(platform(project(":rokkon-bom")))
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-grpc")
    implementation("com.google.protobuf:protobuf-java")
    // ... many more
}

// After  
dependencies {
    implementation(platform(project(":bom:module")))
    // Only module-specific dependencies not in BOM
}
```

### 4. Update Protobuf Configuration

Change protobuf scanning in `application.yml`:

```yaml
# Old
quarkus:
  generate-code:
    grpc:
      scan-for-proto: com.rokkon.pipeline:rokkon-protobuf

# New
quarkus:
  generate-code:
    grpc:
      scan-for-proto: com.rokkon.pipeline:protobuf
```

## Best Practices

### 1. Keep BOMs Focused

Each BOM should only include dependencies relevant to its project type. Don't add module-specific dependencies to BOMs.

### 2. Version Management

- Let Quarkus BOM manage versions where possible
- Only specify versions in BOM constraints for non-Quarkus dependencies
- Use explicit versions in projects only for test dependencies

### 3. Test Dependencies

Test-only dependencies should generally not be in BOMs. Projects should declare test dependencies with explicit versions:

```kotlin
testImplementation("org.awaitility:awaitility:4.2.0")
```

### 4. Protobuf Generation

All projects generate their own protobuf code from the shared `commons:protobuf` JAR. This ensures:
- No classpath conflicts
- Consistent code generation
- Easier debugging

### 5. Dependency Constraints

Use constraints in BOMs only for libraries not managed by Quarkus:

```kotlin
constraints {
    api("com.orbitz.consul:consul-client:1.5.3")
    api("io.swagger.core.v3:swagger-annotations:2.2.21")
}
```

### 6. Documentation

When adding new dependencies to BOMs, document:
- Why the dependency is needed
- Which types of projects will use it
- Any special configuration required

## Common Issues and Solutions

### Issue: Missing validation annotations

**Solution:** Add `quarkus-hibernate-validator` to server/module BOMs that need validation.

### Issue: CLI app includes unnecessary server components

**Solution:** CLI BOM excludes `quarkus-grpc`. Use `compileOnly` for code generation if needed.

### Issue: Duplicate protobuf classes

**Solution:** Ensure all projects scan for `com.rokkon.pipeline:protobuf`, not old artifact names.

### Issue: Test dependency versions conflicting

**Solution:** Declare test dependencies with explicit versions in individual projects, not in BOMs.

## Future Considerations

1. **Version Catalog Migration**: The project has a `gradle/libs.versions.toml` that could centralize version management further.

2. **Platform vs Implementation**: Consider whether some BOMs should use `api` vs `implementation` for better dependency isolation.

3. **BOM Composition**: As the project grows, consider if additional specialized BOMs are needed (e.g., `bom:connector`, `bom:test`).