plugins {
    java
    alias(libs.plugins.quarkus)
    `maven-publish`
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-arc")
    implementation("org.junit.jupiter:junit-jupiter-api")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    
    implementation("org.assertj:assertj-core:3.24.2")
    // Use rokkon-protobuf for proto files
    implementation("com.rokkon.pipeline:rokkon-protobuf:1.0.0-SNAPSHOT")
    // Use rokkon-commons for utilities
    implementation("com.rokkon.pipeline:rokkon-commons:1.0.0-SNAPSHOT")
    
    // Add dependencies for container testing
    implementation("io.quarkus:quarkus-test-common")
    implementation("org.testcontainers:testcontainers:1.19.8")
    
    // Apache Commons IO for file and resource operations
    implementation("commons-io:commons-io:2.15.1")
}

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
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Integration test configuration is already provided by Quarkus plugin

// Exclude integration tests from regular test task
tasks.test {
    exclude("**/*IT.class")
}

// Proto generation configured via application.properties

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "test-utilities"
        }
    }
}

tasks.withType<GenerateModuleMetadata> {
    suppressedValidationErrors.add("enforced-platform")
}
