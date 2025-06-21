// Root build file for multi-module project
// Each module manages its own dependencies and build configuration

allprojects {
    group = "com.rokkon.pipeline"
    version = "1.0.0-SNAPSHOT"
    
    repositories {
        mavenLocal()
        mavenCentral()
    }
}