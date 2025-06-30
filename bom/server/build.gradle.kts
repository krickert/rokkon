plugins {
    `java-platform`
    `maven-publish`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    // Import library BOM - servers build on libraries
    api(platform(project(":bom:library")))
    
    constraints {
        // Server-specific dependencies
        api("io.quarkus:quarkus-grpc") // gRPC server
        api("io.quarkus:quarkus-resteasy-reactive") // REST server
        api("io.quarkus:quarkus-resteasy-reactive-jackson") // REST with Jackson
        
        // OpenAPI/Swagger
        api("io.quarkus:quarkus-smallrye-openapi")
        
        // Health and metrics
        api("io.quarkus:quarkus-smallrye-health")
        api("io.quarkus:quarkus-micrometer")
        api("io.quarkus:quarkus-micrometer-registry-prometheus")
        
        // Server utilities
        api("io.grpc:grpc-services") // gRPC reflection and health
        api("io.quarkus:quarkus-vertx") // Event bus
        
        // Container support
        api("io.quarkus:quarkus-container-image-docker")
        
        // Configuration
        api("io.quarkus:quarkus-config-yaml")
        
        // Dev services for testing
        api("io.quarkus:quarkus-junit5")
        api("io.rest-assured:rest-assured")
        api("io.quarkus:quarkus-test-common")
        api("org.testcontainers:testcontainers")
        api("org.testcontainers:junit-jupiter")
        api("org.testcontainers:consul")
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