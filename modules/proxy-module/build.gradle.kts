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

    // Security extensions
    implementation("io.quarkus:quarkus-security")
    implementation("io.quarkus:quarkus-elytron-security-properties-file")
    implementation("io.quarkus:quarkus-elytron-security-oauth2")

    // SSL support
    implementation("io.quarkus:quarkus-elytron-security")

    // Testing dependencies
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.assertj:assertj-core") // Version from BOM
    testImplementation("io.grpc:grpc-services") // Version from BOM
    testImplementation("org.testcontainers:testcontainers") // Version from BOM
    testImplementation("org.testcontainers:junit-jupiter") // Version from BOM
    testImplementation(project(":testing:util"))

    // Mockito for mocking in tests
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
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
        rename { "register-module-cli.jar" }
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
            artifactId = "proxy-module"
        }
    }
}
