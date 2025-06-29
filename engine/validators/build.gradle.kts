plugins {
    java
    id("io.quarkus")
    `maven-publish`
}



dependencies {
    // Import the rokkon BOM which includes Quarkus BOM
    implementation(platform(project(":rokkon-bom")))
    
    // Quarkus dependencies come from BOM
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-hibernate-validator")
    
    // Depend on rokkon-commons which includes protobuf definitions
    implementation(project(":commons:interface"))
    
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
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

