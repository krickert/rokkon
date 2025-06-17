plugins {
    java
    alias(libs.plugins.quarkus)
    `maven-publish`
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-arc")
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation("org.junit.jupiter:junit-jupiter-api")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")

    implementation("org.assertj:assertj-core:3.24.2")
    implementation("com.rokkon.pipeline:proto-definitions:1.0.0-SNAPSHOT")
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

val extractProtos = tasks.register<Copy>("extractProtos") {
      from(zipTree(configurations.runtimeClasspath.get().filter { it.name.contains("proto-definitions") }.singleFile))
      include("**/*.proto")
      into("src/main/proto")
      includeEmptyDirs = false
}

tasks.named("quarkusGenerateCode") {
    dependsOn(extractProtos)
}
