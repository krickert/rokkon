plugins {
    `java-platform`
    `maven-publish`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    // Import core BOM
    api(platform(project(":bom:core")))
    
    constraints {
        // Library dependencies - shared code that supports servers but isn't a server itself
        api("io.quarkus:quarkus-arc") // CDI support
        
        // gRPC stubs and protobuf
        api("io.grpc:grpc-stub")
        api("io.grpc:grpc-protobuf")
        api("io.quarkus:quarkus-grpc") // For Mutiny stubs generation
        
        // Common utilities
        api("com.fasterxml.jackson.core:jackson-databind")
        api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
        api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
        
        // Consul client
        api("com.orbitz.consul:consul-client")
        
        // Apache Commons
        api("org.apache.commons:commons-lang3")
        api("commons-io:commons-io")
        
        // Validation
        api("jakarta.validation:jakarta.validation-api")
        api("io.quarkus:quarkus-hibernate-validator")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
            artifactId = "bom-library"
        }
    }
}