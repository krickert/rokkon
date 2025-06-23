plugins {
    java
    id("io.quarkus")
    `maven-publish`
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("io.quarkiverse.docker:quarkus-docker-client:0.0.4")
    implementation(platform(project(":rokkon-bom")))
    
    // Quarkus dependencies come from BOM
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation("io.quarkus:quarkus-test-common")
    
    // Test frameworks (versions come from BOM)
    implementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    
    // Integration test dependencies
    integrationTestImplementation("io.quarkus:quarkus-junit5")
    integrationTestImplementation("org.junit.jupiter:junit-jupiter")
    integrationTestImplementation("org.assertj:assertj-core")
    
    // Assertions (version comes from BOM)
    implementation("org.assertj:assertj-core")
    
    // Commons libraries (versions come from BOM)
    implementation("org.apache.commons:commons-lang3")
    implementation("commons-io:commons-io")
    
    // Container testing (version comes from BOM)
    implementation("org.testcontainers:testcontainers")
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Quarkus plugin added to support integration tests

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Integration test configuration provided by Quarkus plugin

// Exclude integration tests from regular test task
tasks.test {
    exclude("**/*IT.class")
}

// Configure Quarkus to use Mutiny for gRPC code generation
quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}

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
