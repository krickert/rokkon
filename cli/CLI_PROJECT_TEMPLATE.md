# CLI Project Template

This template shows how to create a new CLI application in the Rokkon project.

## Minimal build.gradle.kts

```kotlin
plugins {
    java
    id("io.quarkus")
    `maven-publish`
}

dependencies {
    // CLI BOM provides all common dependencies:
    // - commons:protobuf-stubs (pre-generated gRPC stubs)
    // - quarkus-picocli (CLI framework)
    // - quarkus-arc (CDI dependency injection)
    // - quarkus-config-yaml (YAML configuration support)
    // - grpc-netty-shaded (gRPC client transport)
    implementation(platform(project(":bom:cli")))
    
    // Add any project-specific dependencies here
    // Example:
    // implementation("io.vertx:vertx-consul-client")
    
    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Required for Quarkus source JAR generation
tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
    dependsOn("compileQuarkusGeneratedSourcesJava")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "cli-your-command-name"
        }
    }
}
```

## Minimal application.properties

```properties
# Disable Quarkus banner for cleaner CLI output
quarkus.banner.enabled=false

# Logging configuration
quarkus.log.console.format=%d{HH:mm:ss} %-5p %m%n
quarkus.log.level=INFO

# Application name
quarkus.application.name=your-command-name

# Disable gRPC code generation - we use pre-generated stubs
quarkus.generate-code.grpc.scan-for-proto=none
```

## Main CLI Class Template

```java
package com.rokkon.pipeline.cli;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@TopCommand
@Command(
    name = "your-command",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Description of your CLI tool"
)
public class YourCommandCLI implements Runnable {
    
    @Override
    public void run() {
        System.out.println("Use 'your-command --help' for available commands");
    }
}
```

## Command Class Template

```java
package com.rokkon.pipeline.cli;

import io.quarkus.logging.Log;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import jakarta.inject.Inject;
import java.util.concurrent.Callable;

@Command(
    name = "action",
    description = "Perform some action",
    mixinStandardHelpOptions = true
)
public class ActionCommand implements Callable<Integer> {
    
    @Inject
    YourService yourService;  // CDI injection works!
    
    @Option(names = {"--option"}, description = "Some option")
    String option;
    
    @Override
    public Integer call() throws Exception {
        Log.infof("Executing action with option: %s", option);
        
        // Your business logic here
        boolean success = yourService.doSomething(option);
        
        return success ? 0 : 1;
    }
}
```

## Service Template

```java
package com.rokkon.pipeline.cli.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class YourService {
    
    public boolean doSomething(String input) {
        Log.infof("Processing: %s", input);
        // Your business logic here
        return true;
    }
}
```

## Key Benefits

1. **Minimal configuration** - Most dependencies come from the CLI BOM
2. **No HTTP server** - Pure CLI application
3. **Pre-generated stubs** - No need to regenerate gRPC stubs
4. **CDI support** - Dependency injection works out of the box
5. **Standard structure** - All CLI apps follow the same pattern