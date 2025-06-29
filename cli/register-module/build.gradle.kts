plugins {
    java
    id("io.quarkus")
    `maven-publish`
}




dependencies {
    // Import the rokkon BOM which includes Quarkus BOM
    implementation(platform(project(":rokkon-bom")))

    // Core dependencies
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-picocli")
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.quarkus:quarkus-config-yaml")

    // Google common protos for Status and other types
    implementation("com.google.api.grpc:proto-google-common-protos")

    // Additional Quarkus extensions needed by this module
    implementation("io.quarkus:quarkus-smallrye-health")

    // gRPC health checking
    implementation("io.grpc:grpc-services") // Version from BOM

    // Testing dependencies
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("org.assertj:assertj-core")
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

// Create a configuration for the CLI jar that other projects can depend on
val cliJar by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, "cli-jar"))
    }
}

// Add the quarkus runner jar to the configuration
artifacts {
    add("cliJar", tasks.named("quarkusBuild").map {
        file("build/quarkus-app/quarkus-run.jar")
    }) {
        builtBy(tasks.named("quarkusBuild"))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "cli-register-module"
        }
    }
}
