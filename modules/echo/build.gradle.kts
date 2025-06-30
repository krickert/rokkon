plugins {
    java
    id("io.quarkus")
    `maven-publish`
}



dependencies {
    // Module BOM provides all standard module dependencies
    implementation(platform(project(":bom:module")))

    // Module-specific dependencies only
    implementation(project(":commons:util")) // For SampleDataLoader
    implementation("io.quarkus:quarkus-opentelemetry") // Not in module BOM by default
    
    // Module-specific test dependencies
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.assertj:assertj-core")
    testImplementation("io.grpc:grpc-services") // For gRPC health checks in tests
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation(project(":testing:util"))
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"
description = "echo"

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

// Configuration to consume the CLI jar from cli-register
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
            artifactId = "echo-module"
        }
    }
}
