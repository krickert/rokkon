// rokkon-commons/build.gradle.kts
plugins {
    java
    id("io.quarkus")
    `maven-publish`
    idea
}

repositories {
    mavenCentral()
    mavenLocal()
}


// gRPC version for grpc-services dependency
dependencies {
    // Import the rokkon BOM which includes Quarkus BOM
    implementation(platform(project(":rokkon-bom")))

    //implementation(project(":commons:protobuf"))


    // Additional dependencies needed by this module
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.swagger.core.v3:swagger-annotations") // Version managed by BOM


    // Testing
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-core") // Version managed by BOM
    testImplementation("com.github.marschall:memoryfilesystem") // Version managed by BOM
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

// Fix duplicate entries in sources jar
tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<GenerateModuleMetadata> {
    suppressedValidationErrors.add("enforced-platform")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "rokkon-commons"
            
            pom {
                name.set("Rokkon Commons")
                description.set("Common utilities and protobuf helpers for Rokkon Engine")
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