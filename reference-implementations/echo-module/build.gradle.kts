plugins {
    java
    alias(libs.plugins.quarkus)
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation(libs.quarkus.grpc)
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-arc")
    
    // Proto definitions from our proto project
    implementation("com.rokkon:proto-definitions:1.0.0-SNAPSHOT")
    
    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.assertj)
    testImplementation("io.rest-assured:rest-assured")
}

// Configure Quarkus to use Mutiny for gRPC code generation
quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}

group = "com.rokkon"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

// Exclude integration tests from the regular test task
tasks.test {
    exclude("**/*IT.class")
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}
