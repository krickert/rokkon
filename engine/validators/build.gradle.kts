plugins {
    java
    id("io.quarkus")
    `maven-publish`
}



dependencies {
    // Library BOM provides all standard library dependencies
    implementation(platform(project(":bom:library")))
    
    // Library-specific dependencies only
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("jakarta.annotation:jakarta.annotation-api")
    
    // Project dependencies - interface is already provided by library BOM
    // but we need to explicitly declare it since we use it directly
    implementation(project(":commons:interface"))
    
    // Test dependencies
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-core")
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

