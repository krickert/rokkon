plugins {
    `java-platform`
    `maven-publish`
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

val quarkusPlatformVersion: String by project

javaPlatform {
    allowDependencies()
}

dependencies {
    // Import Quarkus BOM - this provides versions for most dependencies
    api(platform("io.quarkus.platform:quarkus-bom:${quarkusPlatformVersion}"))
    
    // Core dependencies that EVERY Rokkon module needs
    api("io.quarkus:quarkus-arc")  // CDI container - needed by all Quarkus apps
    api("io.quarkus:quarkus-grpc") // gRPC support - core to Rokkon
    api("com.rokkon.pipeline:rokkon-protobuf:${project.version}") // Proto definitions - used everywhere
    api("com.rokkon.pipeline:rokkon-commons:${project.version}") // Common utilities - used everywhere
    
    // Additional module constraints
    constraints {
        // Other Rokkon modules
        api("com.rokkon.pipeline:engine-models:${project.version}")
        api("com.rokkon.pipeline:test-utilities:${project.version}")
        
        // Dependencies NOT in Quarkus BOM that we want to standardize
        // (Most Quarkus extensions and common libraries are already managed by quarkus-bom)
        
        // Testing dependencies
        api("org.assertj:assertj-core:3.26.3")
        api("com.github.marschall:memoryfilesystem:2.8.1")
        
        // JUnit - ensure consistent versions (Quarkus BOM provides these but we can override if needed)
        api("org.junit.jupiter:junit-jupiter-api:5.11.0")
        api("org.junit.jupiter:junit-jupiter-engine:5.11.0")
        api("org.junit.jupiter:junit-jupiter-params:5.11.0")
        
        // Quarkus testing (version from Quarkus BOM, but good to have in constraints)
        api("io.quarkus:quarkus-test-common:${quarkusPlatformVersion}")
        api("io.quarkus:quarkus-junit5:${quarkusPlatformVersion}")
        
        // Testcontainers - centrally managed version
        api("org.testcontainers:testcontainers:1.21.2")
        api("org.testcontainers:junit-jupiter:1.21.2")
        api("org.testcontainers:consul:1.21.2")
        
        // Apache Commons libraries
        api("org.apache.commons:commons-lang3:3.17.0")
        api("commons-io:commons-io:2.18.0")
        api("org.apache.commons:commons-collections4:4.4")
        api("org.apache.commons:commons-text:1.12.0")
        
        // Any other non-Quarkus managed dependencies can go here
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
        }
    }
}