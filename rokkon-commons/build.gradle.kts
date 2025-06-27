// rokkon-commons/build.gradle.kts
plugins {
    java
    id("io.quarkus")
    `maven-publish`
}

repositories {
    mavenCentral()
    mavenLocal()
}

// These values WILL now be correctly resolved from your root gradle.properties
val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

// gRPC version for grpc-services dependency
val grpcVersion: String = project.findProperty("grpc.version") as String

dependencies {
    // Import the rokkon BOM which includes Quarkus BOM
    implementation(platform(project(":rokkon-bom")))
    
    // Core dependencies (arc, grpc, protobuf) come from BOM automatically
    
    // Additional dependencies needed by this module
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.swagger.core.v3:swagger-annotations:2.2.21")

    // gRPC health checking - using version from gradle.properties
    implementation("io.grpc:grpc-services:${grpcVersion}")

    // Testing
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-core") // Version managed by BOM
    testImplementation("com.github.marschall:memoryfilesystem") // Version managed by BOM
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

tasks.withType<GenerateModuleMetadata> {
    suppressedValidationErrors.add("enforced-platform")
}

// Fix Quarkus task dependencies
tasks.named("quarkusBuildAppModel") {
    dependsOn("jar")
}

tasks.named("quarkusGenerateCodeTests") {
    dependsOn("jar")
}

tasks.named("quarkusGenerateTestAppModel") {
    dependsOn("jar")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("Rokkon Commons")
                description.set("Common utilities and protobuf helpers for Rokkon Engine")
            }
        }
    }

    repositories {
        mavenLocal()
    }
}