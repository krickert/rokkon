# Rokkon BOM (Bill of Materials)

This module provides centralized dependency management for the Rokkon project.

## What's Included

The Rokkon BOM:
1. Imports the Quarkus BOM, which manages versions for:
   - All Quarkus extensions (quarkus-grpc, quarkus-jackson, quarkus-rest, etc.)
   - Common libraries (Jackson, REST Assured, SLF4J, etc.)
   - Testing frameworks (JUnit 5, AssertJ, etc.)

2. Automatically includes core dependencies every Rokkon module needs:
   - `io.quarkus:quarkus-arc` - CDI container (required for all Quarkus apps)
   - `io.quarkus:quarkus-grpc` - gRPC support (core to Rokkon architecture)
   - `com.rokkon.pipeline:commons-protobuf` - Proto definitions used throughout
   - `com.rokkon.pipeline:commons-interface` - Common interfaces and models
   - `com.rokkon.pipeline:commons-util` - Common utilities and helpers

3. Provides version constraints for:
   - Rokkon internal modules (commons-protobuf, commons-interface, commons-util)
   - Additional dependencies not managed by Quarkus

## How to Use

In your module's `build.gradle.kts`:

```kotlin
dependencies {
    // Import the rokkon BOM
    implementation(platform(project(":rokkon-bom")))
    
    // Core dependencies are automatically included:
    // - quarkus-arc, quarkus-grpc, commons-protobuf, commons-interface
    
    // Add only what you need beyond the core:
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-rest")
    testImplementation("org.assertj:assertj-core")
    
    // All versions are managed by the BOM!
}
```

## Common Dependencies

Most modules will need these core dependencies:
- `com.rokkon.pipeline:commons-protobuf` - Proto definitions
- `com.rokkon.pipeline:commons-interface` - Common interfaces and models
- `com.rokkon.pipeline:commons-util` - Common utilities and helpers
- `io.quarkus:quarkus-grpc` - For gRPC services
- `io.quarkus:quarkus-jackson` - For JSON processing

## Version Management

The BOM uses the Quarkus version specified in `gradle.properties`:
- `quarkusPlatformVersion=3.23.4`

This ensures all modules use consistent versions throughout the project.