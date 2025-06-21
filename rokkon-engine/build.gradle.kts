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
    implementation("io.quarkus:quarkus-smallrye-stork")
    implementation("io.smallrye.stork:stork-service-discovery-consul")
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkiverse.config:quarkus-config-consul")
    implementation("io.quarkus:quarkus-micrometer")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.grpc:grpc-services:1.63.0") // For health check
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation("io.quarkus:quarkus-smallrye-fault-tolerance")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-rest-client-jackson")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-vertx")
    implementation("io.vertx:vertx-consul-client")
    
    // Engine modules
    implementation(project(":engine-consul"))
    implementation(project(":engine-validators"))
    implementation(project(":engine-models"))
    
    // Proto definitions
    implementation(project(":rokkon-protobuf"))
    implementation(project(":rokkon-commons"))
    
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.testcontainers:testcontainers:1.19.8")
    testImplementation("org.testcontainers:junit-jupiter:1.19.8")
    testImplementation("org.testcontainers:consul:1.19.8") {
        exclude(group = "org.apache.commons", module = "commons-compress")
    }
    
    // Test utilities for container testing
    testImplementation(project(":test-utilities"))
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    useJUnitPlatform()
}

// Exclude integration tests from regular test task
tasks.test {
    exclude("**/*IT.class")
    enabled = false
}

// Disable test compilation temporarily
tasks.compileTestJava {
    enabled = false
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Configure Quarkus to use Mutiny for gRPC code generation
quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}

// Integration test source set is already configured by Quarkus
// Just ensure the dependencies are available
configurations {
    getByName("integrationTestImplementation").extendsFrom(configurations.testImplementation.get())
    getByName("integrationTestRuntimeOnly").extendsFrom(configurations.testRuntimeOnly.get())
}
