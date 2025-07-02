// rokkon-commons/build.gradle.kts
plugins {
    `java-library`
    id("io.quarkus") version "3.24.1"
    `maven-publish`
    idea
}




// gRPC version for grpc-services dependency
dependencies {
    // Use library BOM for common library dependencies
    implementation(platform(project(":bom:library")))

    // Additional dependencies needed by this module (not in library BOM)
    implementation("io.quarkus:quarkus-jackson") // For ObjectMapperCustomizer
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.swagger.core.v3:swagger-annotations") // version from BOM constraints

    // Testing
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-core")
    testImplementation("com.github.marschall:memoryfilesystem")
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

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
    options.compilerArgs.addAll(listOf("-parameters", "--enable-preview"))
    options.release = 21
}

// Fix duplicate entries in sources jar and exclude application.yml
tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Exclude application.yml from JAR - it's only needed for proto generation
    exclude("application.yml", "application.properties")
}

tasks.withType<GenerateModuleMetadata> {
    suppressedValidationErrors.add("enforced-platform")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "interface"
            
            pom {
                name.set("Rokkon Commons Interface")
                description.set("Common interfaces and models for Rokkon Engine")
            }
        }
    }

    repositories {
        mavenLocal()
    }
}


// Fix Quarkus task ordering (minimal fix)
tasks.named("quarkusGenerateTestAppModel") {
    dependsOn("jar")
}
tasks.named("quarkusGenerateCodeTests") {
    dependsOn("jar")
}
tasks.named("compileTestJava") {
    dependsOn("jar")
}
tasks.named("quarkusBuildAppModel") {
    dependsOn("jar")
}

// Configure idea to download sources and javadocs
idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}