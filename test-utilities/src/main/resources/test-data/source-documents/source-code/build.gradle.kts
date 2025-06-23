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
    implementation("io.quarkiverse.config:quarkus-config-consul")
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
    implementation("io.quarkus:quarkus-logging-json")
    implementation("io.quarkus:quarkus-websockets")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.swagger.core.v3:swagger-annotations:2.2.8")
    implementation("io.quarkus:quarkus-smallrye-stork")
    implementation("io.quarkus:quarkus-smallrye-jwt")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-scheduler")
    implementation("io.quarkus:quarkus-kafka-client")
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.grpc:grpc-services") // Includes gRPC health service
    implementation("io.quarkus:quarkus-rest-client-jackson")
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-arc")
    
    // JSON Schema validation
    implementation("com.networknt:json-schema-validator:1.5.2")
    
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    implementation("io.smallrye.stork:stork-service-discovery-consul")
    implementation("io.smallrye.stork:stork-service-registration-consul")
    testImplementation("org.testcontainers:consul:1.21.1")
    testImplementation("org.testcontainers:junit-jupiter:1.21.1")
    // Add echo-service as test dependency for integration tests
    testImplementation(project(":modules:echo-service"))
    // https://mvnrepository.com/artifact/org.apache.commons/commons-lang3
    implementation("org.apache.commons:commons-lang3:3.17.0")
}

group = "com.krickert.rokkon"
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
