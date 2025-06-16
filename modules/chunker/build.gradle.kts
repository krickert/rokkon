plugins {
    java
    id("io.quarkus")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project  
val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-logging-json")
    implementation("io.grpc:grpc-services")
    implementation("io.quarkus:quarkus-jackson")
    
    // OpenNLP dependencies for chunking and NLP analysis
    implementation("org.apache.opennlp:opennlp-tools:2.3.0")
    
    // Apache Commons for string utilities
    implementation("org.apache.commons:commons-lang3:3.12.0")
    
    // Use the protobuf models from the parent engine project
    implementation(project(":"))
    
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
}

group = "com.rokkon.modules"
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