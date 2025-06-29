plugins {
    java
    id("io.quarkus")
    `maven-publish`
}



dependencies {
    // Import the rokkon BOM which includes Quarkus BOM
    implementation(platform(project(":rokkon-bom")))

    // Core dependencies (arc, grpc, protobuf, commons) come from BOM

    // Additional Quarkus extensions needed by this module
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-micrometer")
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
    implementation("io.quarkus:quarkus-opentelemetry")

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
    testImplementation("org.mockito:mockito-core:5.3.1")
    testImplementation("org.mockito:mockito-junit-jupiter:5.3.1")
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

// Copy module entrypoint script and CLI jar for Docker build
tasks.register<Copy>("copyModuleEntrypoint") {
    from(rootProject.file("scripts/module-entrypoint.sh"))
    into(layout.buildDirectory)
    rename { "module-entrypoint.sh" }
}

tasks.register<Copy>("copyRokkonCli") {
    dependsOn(":engine:cli-register:quarkusBuild")
    from(project(":engine:cli-register").file("build/quarkus-app/quarkus-run.jar"))
    into(layout.buildDirectory)
    rename { "rokkon-cli.jar" }
}

// Create a task that runs after all Quarkus tasks are complete
tasks.register("postQuarkusTasks") {
    // This task will run after quarkusBuild is complete, but is not part of the dependency chain
    // This breaks the circular dependency
    doLast {
        // This task does nothing itself, but serves as a hook for other tasks
        println("Running post-Quarkus tasks")
    }
}

// Make sure postQuarkusTasks runs after quarkusBuild
tasks.named("quarkusBuild") {
    finalizedBy("postQuarkusTasks")
}

// Make copyModuleEntrypoint and copyRokkonCli run after postQuarkusTasks
tasks.named("postQuarkusTasks") {
    finalizedBy("copyModuleEntrypoint", "copyRokkonCli")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "proxy-module"
        }
    }
}
