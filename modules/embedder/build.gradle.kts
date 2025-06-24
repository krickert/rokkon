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
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-micrometer")
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
    implementation("io.quarkus:quarkus-opentelemetry")

    // DJL (Deep Java Library) for ML inference
    implementation("ai.djl.huggingface:tokenizers:0.33.0")
    implementation("ai.djl.pytorch:pytorch-model-zoo:0.33.0")
    implementation("ai.djl.pytorch:pytorch-jni:2.5.1-0.33.0")

    // CUDA support for GPU acceleration (if on amd64 architecture)
    if (System.getProperty("os.arch") == "amd64") {
        implementation("ai.djl.pytorch:pytorch-native-cu124:2.5.1")
    }

    // Apache Commons for utilities
    implementation("org.apache.commons:commons-lang3:3.12.0")

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
version = "1.0.0-SNAPSHOT"
description = "embedder"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Configure Quarkus to use Mutiny for gRPC code generation
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "embedder-module"
        }
    }
}