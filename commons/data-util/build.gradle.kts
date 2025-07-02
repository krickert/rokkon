plugins {
    `java-library`
    id("io.quarkus")
    `maven-publish`
}

dependencies {
    // Use library BOM for common library dependencies
    implementation(platform(project(":bom:library")))
    
    // Proto files for code generation
    implementation(project(":commons:protobuf"))
    
    // Core dependencies from BOM
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-grpc") // For code generation
    
    // Protobuf utilities
    implementation("com.google.protobuf:protobuf-java")
    implementation("com.google.protobuf:protobuf-java-util")
    implementation("com.google.api.grpc:proto-google-common-protos") // For google.rpc.Status
    
    // Commons libraries
    implementation("org.apache.commons:commons-lang3")
    implementation("commons-io:commons-io")
    
    // Logging
    implementation("org.slf4j:slf4j-api")
    
    // Testing
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-core")
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
    withJavadocJar()
}

// Configure Quarkus to generate code from proto files
// This is a data utility library that needs generated protobuf classes

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Fix duplicate entries in JAR tasks
tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Fix sourcesJar dependency on generated sources
tasks.named<Jar>("sourcesJar") {
    dependsOn("compileQuarkusGeneratedSourcesJava")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "data-util"
        }
    }
}