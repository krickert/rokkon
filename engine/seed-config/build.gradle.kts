plugins {
    java
    id("io.quarkus")
}



dependencies {
    // Use our BOM
    implementation(platform(project(":rokkon-bom")))

    implementation("io.quarkus:quarkus-picocli")
    implementation("io.quarkus:quarkus-arc")

    // Vert.x Consul client for interacting with Consul
    implementation("io.vertx:vertx-consul-client")

    // Jackson for JSON and YAML support
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

    // Test dependencies
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation(project(":testing:util"))
    testImplementation("org.assertj:assertj-core")
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

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}
