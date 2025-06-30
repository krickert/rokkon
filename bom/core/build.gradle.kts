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
    // Import Quarkus BOM for version alignment
    api(platform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    
    constraints {
        // Core shared dependencies - minimal set that ALL projects need
        api("org.slf4j:slf4j-api")
        api("jakarta.annotation:jakarta.annotation-api")
        api("javax.annotation:javax.annotation-api")
        
        // Protobuf core
        api("com.google.protobuf:protobuf-java")
        api("com.google.protobuf:protobuf-java-util")
        
        // Testing basics - available to all
        api("org.junit.jupiter:junit-jupiter")
        api("org.assertj:assertj-core:${assertjVersion}")
        api("org.mockito:mockito-core")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
            artifactId = "bom-core"
        }
    }
}