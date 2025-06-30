plugins {
    java
    id("io.quarkus")
    `maven-publish`
}




dependencies {
    // Use the CLI BOM - includes all common CLI dependencies:
    // - protobuf-stubs (pre-generated)
    // - quarkus-picocli
    // - quarkus-arc
    // - quarkus-config-yaml
    // - grpc-netty-shaded
    implementation(platform(project(":bom:cli")))
    
    // Add compile-only dependency for gRPC code generation
    // This won't be included in runtime, avoiding server components
    compileOnly("io.quarkus:quarkus-grpc")
    
    // Test dependencies from BOM
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Create sourcesJar task
tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
    dependsOn("compileQuarkusGeneratedSourcesJava")
}

// Configuration for exposing the runner JAR to consuming projects
val cliJar by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, "cli-jar"))
    }
}

// Expose the runner JAR from quarkusBuild
artifacts {
    add("cliJar", file("build/quarkus-app/quarkus-run.jar")) {
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
