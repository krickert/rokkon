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

    // Core dependencies (arc, grpc, protobuf, commons) come from BOM

    // Additional Quarkus extensions needed by this module
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-micrometer")
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
    implementation("io.quarkus:quarkus-opentelemetry")

    // Testing dependencies
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.assertj:assertj-core") // Version from BOM
    testImplementation("io.grpc:grpc-services") // Version from BOM
    testImplementation("org.testcontainers:testcontainers") // Version from BOM
    testImplementation("org.testcontainers:junit-jupiter") // Version from BOM
    testImplementation(project(":test-utilities"))
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"  // Test module version

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}

// Exclude integration tests from regular test task (like reference implementation)
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

// Standard processResources configuration
// If we need project properties in the future, we can add them as standard application properties

// Copy CLI jar for Docker build
tasks.register<Copy>("copyDockerAssets") {
    dependsOn(":engine:cli-register:quarkusBuild")
    from(project(":engine:cli-register").file("build/quarkus-app/quarkus-run.jar")) {
        rename { "rokkon-cli.jar" }
    }
    into(layout.buildDirectory.dir("docker"))
}

// Hook the copy task before Docker build
tasks.named("quarkusBuild") {
    dependsOn("copyDockerAssets")
}

// Fix task dependencies
tasks.named("quarkusGenerateCode") {
    dependsOn("copyDockerAssets")
}

tasks.named("processResources") {
    dependsOn("copyDockerAssets")
}

// Clean task will automatically clean build directory including docker assets

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "test-module"
        }
    }
}
