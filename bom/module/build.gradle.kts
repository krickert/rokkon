plugins {
    `java-platform`
    `maven-publish`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    // Import base BOM for version management
    api(platform(project(":bom:base")))
    
    // Common dependencies that ALL modules need
    api(project(":commons:protobuf"))         // Proto files for code generation
    api(project(":commons:util"))             // Utilities like ProcessingBuffer
    api(project(":commons:interface"))        // Common models and interfaces
    api(project(":commons:data-util"))        // Sample data creation utilities
    
    // Standard module dependencies that ALL modules use
    api("io.quarkus:quarkus-arc")
    api("io.quarkus:quarkus-grpc")
    api("io.quarkus:quarkus-jackson")
    api("io.quarkus:quarkus-rest")
    api("io.quarkus:quarkus-rest-jackson")
    api("io.quarkus:quarkus-smallrye-openapi")
    api("io.quarkus:quarkus-config-yaml")
    api("io.quarkus:quarkus-smallrye-health")
    api("io.quarkus:quarkus-micrometer")
    api("io.quarkus:quarkus-micrometer-registry-prometheus")
    api("com.google.protobuf:protobuf-java-util")
    
    // Common utilities
    api("org.apache.commons:commons-lang3")
    api("commons-io:commons-io")
    
    // JSON processing - required for health checks and REST
    api("jakarta.json:jakarta.json-api")
    api("io.quarkus:quarkus-jsonp")  // JSON-P implementation
    api("io.quarkus:quarkus-jsonb")  // JSON-B implementation
    
    // OpenTelemetry and logging support - standard for all modules
    api("io.quarkus:quarkus-opentelemetry")
    api("io.quarkus:quarkus-smallrye-context-propagation")  // Required for context propagation
    api("io.quarkus:quarkus-vertx")  // Provides MDC provider for OpenTelemetry
    api("io.quarkus:quarkus-logging-json")  // JSON logging support
    
    // Container image support - all modules need this
    api("io.quarkus:quarkus-container-image-docker")
    
    constraints {
        // Additional module dependencies that some modules might use
        api("io.quarkus:quarkus-opentelemetry")
        
        // Security extensions (for proxy-module)
        api("io.quarkus:quarkus-security")
        api("io.quarkus:quarkus-elytron-security-properties-file")
        api("io.quarkus:quarkus-elytron-security-oauth2")
        api("io.quarkus:quarkus-elytron-security")
        
        // Parser module dependencies
        api("org.apache.tika:tika-core:3.2.0")
        api("org.apache.tika:tika-parsers-standard-package:3.2.0")
        api("com.networknt:json-schema-validator:1.5.2")
        
        // Test dependencies
        api("org.apache.commons:commons-compress:1.27.1")
        api("org.mockito:mockito-core:5.3.1")
        api("org.mockito:mockito-junit-jupiter:5.3.1")
        api("io.quarkus:quarkus-junit5-mockito")
        
        // Testing support for modules (versions from Quarkus BOM)
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