plugins {
    `java-platform`
    `maven-publish`
}

javaPlatform {
    allowDependencies()
}

val slf4jVersion = "2.0.13"

dependencies {
    // Import core BOM
    api(platform(project(":bom:core")))
    
    // Don't include protobuf-stubs directly in BOM - let projects add it with exclusions
    
    // Direct dependencies that all CLI projects will have
    api(project(":commons:protobuf-stubs"))
    api("io.quarkus:quarkus-picocli")
    api("io.quarkus:quarkus-arc")
    api("io.quarkus:quarkus-config-yaml")
    api("io.grpc:grpc-netty-shaded") // For gRPC client connections
    
    constraints {
        // Additional optional dependencies that CLI projects might use
        api("info.picocli:picocli")
        api("info.picocli:picocli-codegen")
        api("org.yaml:snakeyaml")
        
        // Logging
        api("org.slf4j:slf4j-simple:${slf4jVersion}")
        
        // Common utilities that CLI apps might need
        api("com.fasterxml.jackson.core:jackson-databind")
        api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
        api("io.vertx:vertx-consul-client")
        
        // Testing dependencies for CLI projects
        api("io.quarkus:quarkus-junit5")
        api("io.quarkus:quarkus-junit5-mockito")
        api("io.quarkus:quarkus-test-common")
        api("org.junit.platform:junit-platform-launcher")
        api("org.mockito:mockito-junit-jupiter")
        api("org.testcontainers:testcontainers")
        api("org.awaitility:awaitility")
        
        // Minimal Quarkus runtime for CLI
        api("io.quarkus:quarkus-core")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
            artifactId = "bom-cli"
        }
    }
}