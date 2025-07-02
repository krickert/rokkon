# Module Project Template

This template shows how to create a new module in the pipeline project.

## Minimal build.gradle.kts

With our optimized module BOM, a typical module build file is very minimal:

```kotlin
plugins {
    java
    id("io.quarkus")
    `maven-publish`
}

dependencies {
    // Module BOM provides all standard module dependencies
    implementation(platform(project(":bom:module")))
    
    // Add only module-specific dependencies here
    // Example: implementation("org.apache.opennlp:opennlp-tools:2.3.0")
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Configuration to consume the CLI jar from register-module
val cliJar by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, "cli-jar"))
    }
}

dependencies {
    cliJar(project(":cli:register-module", "cliJar"))
}

// Copy CLI jar for Docker build
tasks.register<Copy>("copyDockerAssets") {
    from(cliJar) {
        rename { "register-module-cli.jar" }
    }
    into(layout.buildDirectory.dir("docker"))
}

// Hook the copy task before Docker build
tasks.named("quarkusBuild") {
    dependsOn("copyDockerAssets")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "your-module-name"
        }
    }
}
```

## Minimal application.yml

```yaml
quarkus:
  application:
    name: your-module-name
    
  generate-code:
    grpc:
      scan-for-proto: none  # Using pre-generated stubs from protobuf-stubs
      
  http:
    port: 8080  # REST API port
    
  grpc:
    server:
      port: 9090  # gRPC server port
      host: 0.0.0.0
      enable-reflection-service: true
      
  log:
    level: INFO
    category:
      "com.rokkon":
        level: DEBUG
```

## What the Module BOM Provides

The module BOM automatically includes ALL standard dependencies that modules need:

### Core Dependencies (Included)
- `commons:protobuf-stubs` - Pre-generated gRPC stubs
- `commons:util` - Utilities like ProcessingBuffer
- `commons:interface` - Common models and interfaces

### Quarkus Extensions (Included)
- `quarkus-arc` - CDI dependency injection
- `quarkus-grpc` - gRPC server support
- `quarkus-jackson` - JSON processing
- `quarkus-rest` - REST API support
- `quarkus-rest-jackson` - REST with Jackson
- `quarkus-smallrye-openapi` - OpenAPI/Swagger support
- `quarkus-config-yaml` - YAML configuration
- `quarkus-smallrye-health` - Health checks
- `quarkus-micrometer` - Metrics
- `quarkus-micrometer-registry-prometheus` - Prometheus metrics
- `protobuf-java-util` - For JsonFormat and other protobuf utilities

### Additional Dependencies (Version managed, add if needed)
- `quarkus-container-image-docker` - Docker image building
- `quarkus-opentelemetry` - Distributed tracing
- Test dependencies (quarkus-junit5, rest-assured, testcontainers, etc.)

## Module Structure

```
your-module/
├── build.gradle.kts
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/rokkon/pipeline/yourmodule/
│   │   │       ├── YourModuleServiceImpl.java    # gRPC service implementation
│   │   │       ├── YourModuleResource.java       # REST API endpoint
│   │   │       └── YourModuleHealthCheck.java    # Health check
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/
│           └── com/rokkon/pipeline/yourmodule/
│               └── YourModuleServiceTest.java
```

## Key Points

1. **No need to declare common dependencies** - The module BOM provides everything
2. **Pre-generated stubs** - No gRPC code generation at build time
3. **Both REST and gRPC** - All modules expose both interfaces
4. **Standard ports** - REST on 8080, gRPC on 9090 (configurable)
5. **Health checks included** - Available at `/q/health`
6. **Metrics included** - Available at `/q/metrics`
7. **OpenAPI included** - Available at `/q/openapi`