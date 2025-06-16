plugins {
    java
    id("io.quarkus")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project  
val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-logging-json")
    implementation("io.grpc:grpc-services") // Includes gRPC health service
    
    // Use the protobuf models from the parent engine project
    implementation(project(":"))
    
    // Apache Tika dependencies - use standard package which includes most parsers
    implementation("org.apache.tika:tika-core:3.2.0")
    implementation("org.apache.tika:tika-parsers-standard-package:3.2.0")
    
    // JSON Schema validation
    implementation("com.networknt:json-schema-validator:1.5.2")
    
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
    testImplementation("org.awaitility:awaitility:4.2.0")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation(project(":modules:test-utilities"))
}

group = "com.rokkon.modules"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}