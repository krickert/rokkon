plugins {
    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // Use the server BOM for version management
    implementation(platform(project(":bom:server")))
    
    // Dependencies needed by the moved classes
    api(project(":commons:data-util")) // For sample data creation with protobuf
    api(project(":commons:util"))
    api(project(":testing:util")) // For lightweight test utilities
    
    // Docker and Testcontainers dependencies
    api("org.testcontainers:testcontainers")
    api("org.testcontainers:junit-jupiter")
    api("org.testcontainers:postgresql")
    api("org.testcontainers:kafka")
    api("com.github.docker-java:docker-java-api")
    api("com.github.docker-java:docker-java-transport-httpclient5")
    
    // Quarkus test utilities (without the Quarkus plugin)
    api("io.quarkus:quarkus-junit5")
    api("io.quarkus:quarkus-test-common")
    api("io.quarkus:quarkus-test-vertx")
    api("io.quarkus:quarkus-container-image-docker")
    api("io.quarkiverse.docker:quarkus-docker-client:0.0.4")
    
    // gRPC testing utilities
    api("io.quarkus:quarkus-grpc")
    api("io.grpc:grpc-testing")
    
    // Additional testing utilities
    api("org.awaitility:awaitility")
    api("org.assertj:assertj-core")
    api("org.mockito:mockito-core")
    api("org.mockito:mockito-junit-jupiter")
    
    // Commons libraries
    implementation("org.apache.commons:commons-lang3")
    implementation("commons-io:commons-io")
    implementation("com.google.guava:guava")
    
    // Logging
    implementation("org.slf4j:slf4j-api")
    
    // JUnit for internal tests
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "testing-server-util"
            
            pom {
                name.set("Rokkon Testing Server Utilities")
                description.set("Heavy Quarkus/Docker testing utilities for Rokkon server components")
            }
        }
    }
    
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/krickert/rokkon")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }
}