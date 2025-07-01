plugins {
    java
    id("io.quarkus")
    `maven-publish`
}

dependencies {
    // Use the CLI BOM - includes all common CLI dependencies
    implementation(platform(project(":bom:cli")))
    
    // Add compile-only dependency for gRPC code generation
    // This won't be included in runtime, avoiding server components
    compileOnly("io.quarkus:quarkus-grpc")
    
    // Project-specific dependencies only
    implementation("io.quarkus:quarkus-vertx")  // Provides Vertx for dependency injection
    implementation("io.vertx:vertx-consul-client")
    implementation("com.fasterxml.jackson.core:jackson-databind") 
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

    // Testing dependencies (all from CLI BOM)
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // Quarkus test dependencies for CLI
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("io.quarkus:quarkus-test-common")
    
    // Additional test dependencies
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:consul")
    testImplementation("org.awaitility:awaitility")
}

group = "com.rokkon.pipeline"
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "cli-seed-engine-consul-config"
        }
    }
}