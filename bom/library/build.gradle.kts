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
    
    // Proto files for ALL projects to generate from
    api(project(":commons:protobuf"))
    
    // Provide common dependencies that libraries need
    // These are "api" dependencies so projects using library BOM get them automatically
    api("io.quarkus:quarkus-arc") // CDI support
    api("io.quarkus:quarkus-grpc") // For gRPC code generation
    
    // gRPC stubs and protobuf (versions from Quarkus BOM)
    api("io.grpc:grpc-stub")
    api("io.grpc:grpc-protobuf") 
    api("com.google.protobuf:protobuf-java")
    api("com.google.protobuf:protobuf-java-util")
    
    // Jackson (versions from Quarkus BOM)
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.core:jackson-annotations")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    // Common utilities (versions from Quarkus BOM)
    api("org.apache.commons:commons-lang3")
    api("commons-io:commons-io")
    
    constraints {
        // Only add version constraints for things NOT in Quarkus BOM
        api("com.orbitz.consul:consul-client:1.5.3")
        api("io.swagger.core.v3:swagger-annotations:2.2.21")
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