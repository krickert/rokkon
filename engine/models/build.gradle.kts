plugins {
    java
    id("io.quarkus")
    `maven-publish`
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project
val swaggerUiVersion: String = project.findProperty("swagger.ui.version") as String

dependencies {
    // Import the rokkon BOM which includes Quarkus BOM
    implementation(platform(project(":rokkon-bom")))
    
    // Don't need to declare Quarkus BOM again - it comes from rokkon-bom
    
    // Core dependencies (arc, grpc, protobuf, commons) come from BOM automatically
    
    // Additional dependencies specific to this module
    implementation("io.quarkiverse.config:quarkus-config-consul")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.swagger.core.v3:swagger-annotations:${swaggerUiVersion}")
    implementation("io.quarkus:quarkus-hibernate-validator")
    
    // Testing
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-core") // Version managed by BOM
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

// Exclude integration tests from regular test task
tasks.test {
    exclude("**/*IT.class")
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "engine-models"
        }
    }
}
