plugins {
    `java-platform`
    `maven-publish`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    // Import base BOM which includes Quarkus BOM
    api(platform(project(":bom:base")))
    
    // Direct dependencies that all CLI projects will have
    api(project(":commons:protobuf"))  // Proto files for code generation
    api("io.quarkus:quarkus-picocli")
    api("io.quarkus:quarkus-arc")
    api("io.quarkus:quarkus-config-yaml")
    // NOT including quarkus-grpc here - it brings in server dependencies!
    // CLI projects need to generate protobuf code without server components
    api("io.grpc:grpc-netty-shaded")  // For gRPC client connections
    api("io.grpc:grpc-stub")          // For gRPC stubs
    api("io.grpc:grpc-protobuf")      // For protobuf support
    api("com.google.protobuf:protobuf-java")
    api("com.google.protobuf:protobuf-java-util")
    
    constraints {
        // Only add versions for things NOT in Quarkus BOM
        // Quarkus manages: picocli, jackson, vertx, junit, mockito, testcontainers, etc.
        
        // Nothing additional needed - Quarkus BOM covers everything
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