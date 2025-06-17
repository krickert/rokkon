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
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation(libs.quarkus.grpc)
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-arc")
    implementation("com.rokkon.pipeline:proto-definitions:1.0.0-SNAPSHOT")
    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.assertj)
    testImplementation("io.rest-assured:rest-assured")
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"
description = "echo"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
        }
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

tasks.test {
    exclude("**/*IT.class")
}