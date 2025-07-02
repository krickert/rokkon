plugins {
    java
    id("io.quarkus")
    `maven-publish`
}



dependencies {
    // Module BOM provides all standard module dependencies
    implementation(platform(project(":bom:module")))

    // Module-specific dependencies only
    implementation("io.quarkus:quarkus-opentelemetry") // Not in module BOM by default

    // Apache Tika dependencies - use standard package which includes most parsers
    implementation("org.apache.tika:tika-core")
    implementation("org.apache.tika:tika-parsers-standard-package")

    // Rokkon commons util for ProcessingBuffer and other utilities
    implementation(project(":commons:util"))

    // JSON Schema validation
    implementation("com.networknt:json-schema-validator")

    // Testing dependencies
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.assertj:assertj-core") // Version from BOM
    testImplementation("io.grpc:grpc-services") // Version from BOM
    testImplementation("org.testcontainers:testcontainers") // Version from BOM
    testImplementation("org.testcontainers:junit-jupiter") // Version from BOM
    testImplementation(project(":testing:util"))
    testImplementation(project(":testing:server-util"))

    // Apache Commons IO for file operations
    testImplementation("commons-io:commons-io")
    // Apache Commons Compress for reading from JARs/ZIPs
    // https://mvnrepository.com/artifact/org.apache.commons/commons-compress
    testImplementation("org.apache.commons:commons-compress")
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

// Exclude integration tests from regular test task
tasks.test {
    exclude("**/*IT.class")
    maxHeapSize = "3g"
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Configuration to consume the CLI jar from cli-register-module
val cliJar by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, "cli-jar"))
    }
}

dependencies {
    cliJar(project(":cli:register-module", "cliJar"))
}

// Copy CLI jar for Docker build
tasks.register<Copy>("copyDockerAssets") {
    from(cliJar) {
        rename { "pipeline-cli.jar" }
    }
    into(layout.buildDirectory.dir("docker"))
}

// Hook the copy task before Docker build
tasks.named("quarkusBuild") {
    dependsOn("copyDockerAssets")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "parser-module"
        }
    }
}
