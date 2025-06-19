plugins {
    java
    `maven-publish`
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