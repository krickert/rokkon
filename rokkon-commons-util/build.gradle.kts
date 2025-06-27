plugins {
    java
    id("io.quarkus")
    `maven-publish`
    idea
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // Import the rokkon BOM
    implementation(platform(project(":rokkon-bom")))
    
    // Only depend on protobuf for sample data utilities - no longer need commons!
    implementation(project(":rokkon-protobuf")) // For SampleDataCreator/Loader
    
    // Core dependencies from BOM
    implementation("io.quarkus:quarkus-arc")
    
    // If we need protobuf support
    implementation("io.quarkus:quarkus-grpc")
    
    // Jackson for ObjectMapperFactory
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    // Testing
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-core")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("com.github.marschall:memoryfilesystem") // For buffer tests
    testImplementation(project(":rokkon-commons")) // For JsonOrderingCustomizer in tests
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

// Configure Quarkus to use Mutiny for gRPC code generation
quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    useJUnitPlatform()
}

// Exclude integration tests from regular test task
tasks.test {
    exclude("**/*IT.class")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "--enable-preview"))
    options.release = 21
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "rokkon-commons-util"
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