plugins {
    java
    `java-library`
    `maven-publish`
    idea
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // Import the BOM for version management
    implementation(platform("com.rokkon.pipeline:rokkon-bom:${project.version}"))

    // Google common protos for Status and other types - exposed as transitive dependency
    api("com.google.api.grpc:proto-google-common-protos")

    // Explicitly include protobuf dependencies
    implementation("com.google.protobuf:protobuf-java")
    implementation("io.grpc:grpc-protobuf")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core")
}

tasks.withType<Test> {
    useJUnitPlatform()
    dependsOn(tasks.jar)
}

// Create a source jar that includes proto files
tasks.register<Jar>("sourcesJar") {
    from(sourceSets.main.get().resources)
    archiveClassifier.set("sources")
}

// Configure jar to include proto files in resources
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            artifact(tasks["sourcesJar"])

            pom {
                name.set("Rokkon Protocol Buffers")
                description.set("Protocol Buffer definitions for Rokkon Engine")
            }
        }
    }

    repositories {
        mavenLocal()
    }
}
