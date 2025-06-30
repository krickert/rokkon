plugins {
    `java-platform`
    `maven-publish`
}

javaPlatform {
    allowDependencies()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project
val assertjVersion = project.findProperty("assertj.version") as String? ?: "3.27.3"

dependencies {
    // Import Quarkus BOM for version alignment - this manages MOST versions
    api(platform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    
    constraints {
        // Only add versions for things NOT managed by Quarkus BOM
        // Quarkus already manages: slf4j, jakarta, protobuf, junit, mockito, jackson, etc.
        
        // Additional testing libraries not in Quarkus BOM
        api("org.assertj:assertj-core:${assertjVersion}")
        api("com.github.marschall:memoryfilesystem:2.8.0")
        
        // Docker/container related (if not in Quarkus BOM)
        api("com.github.docker-java:docker-java-api:3.3.6")
        api("com.github.docker-java:docker-java-transport-httpclient5:3.3.6")
        
        // Any other dependencies we use that Quarkus doesn't manage
        api("io.quarkiverse.docker:quarkus-docker-client:0.0.4")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
            artifactId = "bom-base"
        }
    }
}