plugins {
    java
    id("io.quarkus")
    `maven-publish`
}




dependencies {
    // Use the CLI BOM - includes all common CLI dependencies:
    // - protobuf-stubs (pre-generated)
    // - quarkus-picocli
    // - quarkus-arc
    // - quarkus-config-yaml
    // - grpc-netty-shaded
    implementation(platform(project(":bom:cli")))
    
    // Test dependencies from BOM
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
    options.compilerArgs.add("-parameters")
}

// Create sourcesJar task
tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
    dependsOn("compileQuarkusGeneratedSourcesJava")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "cli-register-module"
        }
    }
}
