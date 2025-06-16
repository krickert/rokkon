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
    implementation("io.quarkus:quarkus-junit5")
    
    // Shared protobuf definitions
    implementation(project(":modules:rokkon-proto"))
    
    // TODO: Module dependencies create circular references
    // Need to rethink test-utilities architecture
    // implementation(project(":modules:echo-service"))
    // implementation(project(":modules:tika-parser"))
    // implementation(project(":modules:chunker"))
    // implementation(project(":modules:embedder"))
    
    // Test utilities
    implementation("org.slf4j:slf4j-api")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    
    // Assertions and testing support
    implementation("org.junit.jupiter:junit-jupiter-api")
    implementation("org.assertj:assertj-core:3.24.2")
}

group = "com.rokkon.test"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}