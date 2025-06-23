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
    // Import the rokkon BOM which includes Quarkus BOM
    implementation(platform(project(":rokkon-bom")))

    // Core dependencies
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-picocli")
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.quarkus:quarkus-config-yaml")

    // Additional Quarkus extensions needed by this module
    implementation("io.quarkus:quarkus-smallrye-health")

    // gRPC health checking
    implementation("io.grpc:grpc-services") // Version from BOM

    // Testing dependencies
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("org.assertj:assertj-core") // Version from BOM
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

// Exclude integration tests from regular test task
tasks.test {
    exclude("**/*IT.class")
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
            artifactId = "cli-register"
        }
    }
}
