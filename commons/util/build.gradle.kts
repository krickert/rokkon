plugins {
    `java-library`
    id("io.quarkus")
    `maven-publish`
    idea
}



dependencies {
    // Use library BOM for common library dependencies
    implementation(platform(project(":bom:library")))
    
    // No protobuf generation needed in util anymore

    // Core dependencies from BOM
    implementation("io.quarkus:quarkus-arc")
    

    // Jackson for ObjectMapperFactory
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    // Testing
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-core")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("com.github.marschall:memoryfilesystem") // For buffer tests
    testImplementation(project(":commons:interface")) // For JsonOrderingCustomizer in tests
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
    withJavadocJar()
}

// Fix duplicate entries in sources jar
tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Fix sourcesJar dependency on generated sources
tasks.named<Jar>("sourcesJar") {
    dependsOn("compileQuarkusGeneratedSourcesJava")
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "commons-util"
        }
    }
}

// Suppress the enforced platform validation
tasks.withType<GenerateModuleMetadata> {
    suppressedValidationErrors.add("enforced-platform")
}

// Configure idea to download sources and javadocs
idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}