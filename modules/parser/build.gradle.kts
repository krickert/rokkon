plugins {
    java
    alias(libs.plugins.quarkus)
    `maven-publish`
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation(libs.quarkus.grpc)
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-arc")
    
    // Proto definitions and utilities from new structure
    implementation("com.rokkon.pipeline:rokkon-protobuf:1.0.0-SNAPSHOT")
    implementation("com.rokkon.pipeline:rokkon-commons:1.0.0-SNAPSHOT")
    
    // Apache Tika dependencies - use standard package which includes most parsers
    implementation("org.apache.tika:tika-core:3.2.0")
    implementation("org.apache.tika:tika-parsers-standard-package:3.2.0")
    
    // JSON Schema validation
    implementation("com.networknt:json-schema-validator:1.5.2")
    
    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.assertj)
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("com.rokkon.pipeline:test-utilities:1.0.0-SNAPSHOT")
    testImplementation("com.rokkon.pipeline:rokkon-protobuf:1.0.0-SNAPSHOT")
    
    // Apache Commons IO for file operations
    testImplementation("commons-io:commons-io:2.15.1")
    // Apache Commons Compress for reading from JARs/ZIPs
    testImplementation("org.apache.commons:commons-compress:1.25.0")

}

// Configure Quarkus to use Mutiny for gRPC code generation
quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}

// No need to extract protos - using scan-for-proto instead

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    maxHeapSize = "3g"
}

// Exclude integration tests from the regular test task
tasks.test {
    exclude("**/*IT.class")
    // Comprehensive tests are now enabled!
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "parser-module"
        }
    }
}