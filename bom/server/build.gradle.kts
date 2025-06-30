plugins {
    `java-platform`
    `maven-publish`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    // Import base BOM directly - parallel structure, not hierarchical
    api(platform(project(":bom:base")))
    
    // Common project dependencies
    api(project(":commons:protobuf"))      // Proto files for code generation
    api(project(":commons:util"))
    api(project(":commons:interface"))
    api(project(":commons:data-util"))     // Sample data creation utilities
    
    // Include everything from library BOM
    api("io.quarkus:quarkus-arc")
    api("io.grpc:grpc-stub")
    api("io.grpc:grpc-protobuf") 
    api("com.google.protobuf:protobuf-java-util")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.core:jackson-annotations")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    api("org.apache.commons:commons-lang3")
    api("commons-io:commons-io")
    
    // Server-specific dependencies (versions from Quarkus BOM)
    api("io.quarkus:quarkus-grpc") // gRPC server
    api("io.quarkus:quarkus-rest") // REST server
    api("io.quarkus:quarkus-rest-jackson") // REST with Jackson
    api("io.quarkus:quarkus-smallrye-openapi")
    api("io.quarkus:quarkus-smallrye-health")
    api("io.quarkus:quarkus-micrometer")
    api("io.quarkus:quarkus-micrometer-registry-prometheus")
    api("io.grpc:grpc-services") // gRPC reflection and health
    api("io.quarkus:quarkus-vertx") // Event bus
    api("io.quarkus:quarkus-container-image-docker")
    api("io.quarkus:quarkus-config-yaml")
    
    // Testing (versions from Quarkus BOM)
    api("io.quarkus:quarkus-junit5")
    api("io.rest-assured:rest-assured")
    api("io.quarkus:quarkus-test-common")
    api("org.testcontainers:testcontainers")
    api("org.testcontainers:junit-jupiter")
    
    constraints {
        // Only add versions for things NOT in Quarkus BOM
        api("com.orbitz.consul:consul-client:1.5.3")
        api("io.swagger.core.v3:swagger-annotations:2.2.21")
        api("org.testcontainers:consul:1.19.3")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
            artifactId = "bom-server"
        }
    }
}