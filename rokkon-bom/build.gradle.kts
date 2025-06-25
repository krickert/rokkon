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
        
        // Dependencies NOT managed by Quarkus BOM:
        
        // Testing dependencies not in Quarkus
        api("org.assertj:assertj-core:3.26.3")
        api("com.github.marschall:memoryfilesystem:2.8.1")
        
        // Apache Commons - only what Quarkus doesn't provide
        api("org.apache.commons:commons-collections4:4.4")
        
        // Note: The following ARE managed by Quarkus BOM 3.24.1, so we don't override:
        // - JUnit (all versions)
        // - Testcontainers (1.21.2)
        // - commons-lang3 (3.17.0)
        // - commons-io (2.19.0)
        // - commons-text (1.13.1)
        // - proto-google-common-protos (2.58.0)
        // - All Quarkus modules (use ${quarkusPlatformVersion})
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
        }
    }
}