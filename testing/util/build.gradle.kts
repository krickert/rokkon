plugins {
    java
    id("io.quarkus")
    `maven-publish`
    `java-library`
}



dependencies {
    implementation("io.quarkiverse.docker:quarkus-docker-client:0.0.4")
    implementation(platform(project(":rokkon-bom")))

    // Quarkus dependencies come from BOM
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation("io.quarkus:quarkus-test-common")

    // Rokkon protobuf definitions for PipeDoc, PipeStream, etc.
    api(project(":commons:protobuf"))

    // Google common protos for Status and other types
    implementation("com.google.api.grpc:proto-google-common-protos")

    // Test frameworks (versions come from BOM)
    implementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")


    // Assertions (version comes from BOM)
    implementation("org.assertj:assertj-core")

    // Commons libraries (versions come from BOM)
    implementation("org.apache.commons:commons-lang3")
    implementation("commons-io:commons-io")

    // Container testing (version comes from BOM)
    implementation("org.testcontainers:testcontainers")
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    // Ensure main sources are included in the JAR
    withSourcesJar()

    // Configure sourcesJar task to handle duplicates
    tasks.named<Jar>("sourcesJar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        dependsOn("compileQuarkusGeneratedSourcesJava")
    }
}

// Quarkus plugin added to support integration tests

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    // Increase memory for tests that load large protobuf collections
    maxHeapSize = "1g"
    jvmArgs("-XX:+UseG1GC", "-XX:MaxMetaspaceSize=512m")
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Configure jar task to include all classes
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "testing-util"
        }
    }
}
