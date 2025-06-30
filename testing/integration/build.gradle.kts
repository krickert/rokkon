plugins {
    java
    id("io.quarkus")
}

dependencies {
    implementation("io.quarkiverse.docker:quarkus-docker-client:0.0.4")
    // Import the rokkon BOM which includes Quarkus BOM
    implementation(platform(project(":rokkon-bom")))
    
    // Testing framework dependencies
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.assertj:assertj-core")
    
    // Testcontainers dependencies
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    
    // Testing utilities from our testing/util module
    testImplementation(project(":testing:util"))
    
    // Protocol buffer definitions
    testImplementation(project(":commons:protobuf"))
    
    // gRPC dependencies for client connections
    testImplementation("io.grpc:grpc-stub")
    testImplementation("io.grpc:grpc-protobuf")
    testImplementation("io.grpc:grpc-netty-shaded")
    
    // Consul client for verification
    testImplementation("io.vertx:vertx-consul-client")
    // https://mvnrepository.com/artifact/org.testcontainers/consul
    testImplementation("org.testcontainers:consul:1.21.3")
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    // Increase timeout for container startup
    systemProperty("testcontainers.startup.timeout", "300")
    // Enable testcontainers reuse for faster local development
    systemProperty("testcontainers.reuse.enable", "true")
    // Pass project version to tests
    systemProperty("project.version", version)
    
    // Ensure we have enough memory for running multiple containers
    maxHeapSize = "2g"
}

// Configure Quarkus to use Mutiny for gRPC code generation
quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}

// Ensure Docker images are built before running integration tests
// Temporarily commented out to test integration framework
// tasks.named("quarkusIntTest") {
//     dependsOn(":rokkon-engine:build")
//     dependsOn(":modules:test-module:build")
// }