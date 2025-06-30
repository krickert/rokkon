plugins {
    `java-platform`
    `maven-publish`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    // Import server BOM since modules are gRPC servers
    api(platform(project(":bom:server")))
    
    constraints {
        // Module-specific dependencies
        api("io.quarkus:quarkus-grpc")
        
        // Container image building for modules
        api("io.quarkus:quarkus-container-image-docker")
        
        // Health checks for modules
        api("io.quarkus:quarkus-smallrye-health")
        
        // Metrics for modules
        api("io.quarkus:quarkus-micrometer")
        api("io.quarkus:quarkus-micrometer-registry-prometheus")
        
        // Testing support for modules
        api("io.quarkus:quarkus-junit5")
        api("io.rest-assured:rest-assured")
        api("io.quarkus:quarkus-test-common")
        api("org.testcontainers:testcontainers")
        api("org.testcontainers:junit-jupiter")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
            artifactId = "bom-module"
        }
    }
}