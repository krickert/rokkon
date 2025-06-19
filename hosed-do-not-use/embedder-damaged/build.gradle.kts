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
    
    // DJL (Deep Java Library) for ML inference
    implementation("ai.djl.huggingface:tokenizers:0.33.0")
    implementation("ai.djl.pytorch:pytorch-model-zoo:0.33.0")
    implementation("ai.djl.pytorch:pytorch-jni:2.5.1-0.33.0")
    
    // CUDA support for GPU acceleration (user has RTX 4080 Super)
    if (System.getProperty("os.arch") == "amd64") {
        implementation("ai.djl.pytorch:pytorch-native-cu124:2.5.1")
    }
    
    // Apache Commons for utilities
    implementation("org.apache.commons:commons-lang3:3.12.0")
    
    // Use the protobuf models from the parent engine project
    implementation(project(":"))
    
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation(project(":modules:test-utilities"))
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