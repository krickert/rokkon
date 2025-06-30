plugins {
    java
    id("io.quarkus")
    `maven-publish`
}

dependencies {
    // Module BOM provides all standard module dependencies
    implementation(platform(project(":bom:module")))

    // Module-specific dependencies only
    implementation("org.apache.opennlp:opennlp-tools:2.3.0")
    
    // Module-specific test dependencies
    testImplementation(project(":testing:util"))
    testImplementation(project(":testing:server-util")) // For ProtobufTestDataHelper
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-core")
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// No gRPC code generation needed - using pre-generated stubs

tasks.test {
    maxHeapSize = "2g"
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Configuration to consume the CLI jar from register-module
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
            artifactId = "chunker"
        }
    }
}
