plugins {
    java
    id("io.quarkus")
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
    implementation("io.quarkus:quarkus-jackson")
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-micrometer")
    implementation("io.quarkus:quarkus-arc")
    
    // Proto definitions from shared project
    implementation("com.rokkon.pipeline:proto-definitions:1.0.0-SNAPSHOT")
    
    // Engine modules for Consul integration
    implementation("com.rokkon.pipeline:engine-consul:1.0.0-SNAPSHOT")
    
    // Observability
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
    implementation("io.quarkus:quarkus-opentelemetry")
    
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("io.rest-assured:rest-assured")
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

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

// CRITICAL: Extract proto files from jar for local stub generation
val extractProtos = tasks.register<Copy>("extractProtos") {
    from(zipTree(configurations.runtimeClasspath.get().filter { it.name.contains("proto-definitions") }.singleFile))
    include("**/*.proto")
    into("src/main/proto")
    includeEmptyDirs = false
}

tasks.named("quarkusGenerateCode") {
    dependsOn(extractProtos)
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
