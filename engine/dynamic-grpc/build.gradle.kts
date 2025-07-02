plugins {
    java
    id("io.quarkus")
}

dependencies {
    // Library BOM provides gRPC code generation without server components
    implementation(platform(project(":bom:library")))
    
    // --- Core dependencies ---
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.grpc:grpc-services")
    
    // --- Service Discovery ---
    implementation("io.quarkus:quarkus-smallrye-stork")
    implementation("io.smallrye.stork:stork-service-discovery-consul")
    implementation("io.smallrye.stork:stork-configuration-generator")
    
    // --- Consul Client ---
    implementation("io.vertx:vertx-consul-client")
    implementation("io.smallrye.reactive:smallrye-mutiny-vertx-consul-client")
    
    // --- Caching ---
    implementation("io.quarkus:quarkus-cache")
    
    // --- Engine dependencies ---
    implementation(project(":engine:consul"))
    
    // --- Commons dependencies ---
    implementation(project(":commons:interface"))
    
    // --- Testing ---
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.awaitility:awaitility")
    testImplementation("org.testcontainers:consul")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation(project(":testing:util"))
}

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

// Quarkus configuration
quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}

// Configure integration test task
tasks.register<Test>("runIntegrationTests") {
    description = "Runs integration tests"
    group = "verification"
    
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    
    useJUnitPlatform()
    
    // Only run IT tests
    include("**/*IT.class")
    
    // Test configuration
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}