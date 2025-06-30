plugins {
    `java-library`
    id("io.quarkus")
    `maven-publish`
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // Use library BOM which has gRPC dependencies
    implementation(platform(project(":bom:library")))
    
    // Dependencies for proto files and code generation
    implementation(project(":commons:protobuf"))
    
    // Quarkus gRPC ONLY for code generation - not included in runtime
    compileOnly("io.quarkus:quarkus-grpc")
    
    // Client-only gRPC dependencies that will be exposed to consumers
    api("io.grpc:grpc-stub")
    api("io.grpc:grpc-protobuf") 
    api("com.google.protobuf:protobuf-java")
    api("io.quarkus:quarkus-grpc-stubs") // For Mutiny stubs only
    api("io.quarkus:quarkus-grpc-api") // API interfaces only
    api("javax.annotation:javax.annotation-api")
    
    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("io.grpc:grpc-netty-shaded")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("io.quarkus:quarkus-grpc") // Need full gRPC for tests
}

// Remove the sourceSets configuration since Quarkus will scan the dependency
// The proto files come from the commons:protobuf dependency

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Exclude application.yml from the JAR to prevent downstream projects from regenerating stubs
    exclude("application.yml")
    exclude("application.properties")
}

// Disable Quarkus application building - this is just a library
tasks.named("quarkusBuild") {
    enabled = false
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "protobuf-stubs"
        }
    }
}