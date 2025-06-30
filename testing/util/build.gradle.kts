plugins {
    `java-library`
    id("io.quarkus")
    `maven-publish`
}

dependencies {
    // Use library BOM for test utilities
    implementation(platform(project(":bom:library")))
    
    // Protobuf will be available through library BOM
    
    // Add commons:util for ProcessingBuffer
    implementation(project(":commons:util"))

    // Google common protos for Status and other types  
    implementation("com.google.api.grpc:proto-google-common-protos")
    
    // quarkus-grpc comes from library BOM for code generation

    // Test frameworks (versions come from BOM)
    implementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")

    // Assertions (version comes from BOM)
    implementation("org.assertj:assertj-core")

    // Commons libraries (versions come from BOM)
    implementation("org.apache.commons:commons-lang3")
    implementation("commons-io:commons-io")
    
    // Testcontainers - needed for ModuleContainerResource
    implementation("org.testcontainers:testcontainers")
    implementation("org.testcontainers:junit-jupiter")
    
    // Quarkus test support
    implementation("io.quarkus:quarkus-junit5")
    
    // Self-dependency for tests that need the test utilities
    testImplementation(project(":testing:server-util")) // For ProtobufTestDataHelper
    testImplementation("io.quarkus:quarkus-junit5")
}

group = "com.rokkon.testing"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "testing-util"
        }
    }
}