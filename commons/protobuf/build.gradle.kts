plugins {
    java
    `java-library`
    `maven-publish`
    idea
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"



java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // Use core BOM for minimal dependencies
    implementation(platform(project(":bom:base")))
    
    // No runtime dependencies needed - this is just a proto files jar
}

// No tests needed for proto resource jar

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
            artifactId = "rokkon-protobuf"

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
