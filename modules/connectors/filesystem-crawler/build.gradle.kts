plugins {
    java
    id("io.quarkus")
    `maven-publish`
}

// Define the integrationTest source set
sourceSets {
    // The integrationTest source set is already defined, just update its classpath
    getByName("integrationTest") {
        compileClasspath += sourceSets.test.get().output
        runtimeClasspath += sourceSets.test.get().output
    }
}

// Configure the integrationTest task
val integrationTest = task<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"

    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter("test")

    // Use the same system properties as the test task
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")

    // Temporarily disable integration tests until the mock engine is completed
    // This is as per the issue request
    enabled = false
}

// Temporarily disable integration tests as per issue request
// Will be re-enabled when the mock engine is completed
// tasks.check { dependsOn(integrationTest) }

// Add test dependencies to integrationTest
configurations {
    "integrationTestImplementation" {
        extendsFrom(configurations.testImplementation.get())
    }
    "integrationTestRuntimeOnly" {
        extendsFrom(configurations.testRuntimeOnly.get())
    }
}

// Add specific dependencies for integrationTest
dependencies {
    "integrationTestImplementation"(project(":commons:protobuf"))
}



val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project
val swaggerUiVersion: String = project.findProperty("swagger.ui.version") as String

dependencies {
    // Module BOM provides all standard module dependencies
    implementation(platform(project(":bom:module")))
    
    // Module-specific dependencies only
    implementation("commons-io:commons-io")
    
    // Module-specific test dependencies
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("org.mockito:mockito-core")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.quarkus:quarkus-junit5") // For @QuarkusTest
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

// Temporarily disable tests until the mock engine is completed
tasks.test {
    enabled = false
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Configuration to consume the CLI jar from register-module
val cliJar by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, "cli-jar"))
    }
}

dependencies {
    cliJar(project(":cli:register-module", "cliJar"))
}

// Copy CLI jar for Docker build
tasks.register<Copy>("copyDockerAssets") {
    from(cliJar) {
        rename { "register-module-cli.jar" }
    }
    into(layout.buildDirectory.dir("docker"))
}

// Hook the copy task before Docker build
tasks.named("quarkusBuild") {
    dependsOn("copyDockerAssets")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "filesystem-crawler"
        }
    }
}
