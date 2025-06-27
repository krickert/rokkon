plugins {
    java
    id("io.quarkus")
    `maven-publish`
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // Import the rokkon BOM which includes Quarkus BOM
    implementation(platform(project(":rokkon-bom")))
    
    // Quarkus dependencies come from BOM
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-hibernate-validator")
    
    // Depend on rokkon-commons which includes protobuf definitions
    implementation(project(":rokkon-commons"))
    // Depend on engine-models for the data structures
    implementation(project(":engine:models"))
    
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-core") // Version managed by BOM
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Configure test tasks
tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    useJUnitPlatform()
}

// Exclude integration tests from regular test task (Quarkus handles this)
tasks.test {
    exclude("**/*IT.class")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "--enable-preview"))
}

tasks.withType<JavaExec> {
    jvmArgs("--enable-preview")
}

quarkus {
    buildForkOptions {
        jvmArgs("--enable-preview")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

// Suppress the enforced platform validation
tasks.withType<GenerateModuleMetadata> {
    suppressedValidationErrors.add("enforced-platform")
}
