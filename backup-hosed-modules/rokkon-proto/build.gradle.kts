import com.google.protobuf.gradle.id

plugins {
    java
    id("com.google.protobuf") version "0.9.4"
    id("com.google.osdetector") version "1.7.3"
}

val grpcVersion = "1.65.1"
val protobufVersion = "3.25.3"
val protocVersion = "3.25.3"

dependencies {
    implementation("io.grpc:grpc-protobuf:${grpcVersion}")
    implementation("io.grpc:grpc-stub:${grpcVersion}")
    implementation("com.google.protobuf:protobuf-java:${protobufVersion}")
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")
    
    // For Mutiny support in generated code
    implementation("io.smallrye.reactive:mutiny:2.6.2")
    implementation("io.quarkus:quarkus-grpc-stubs:3.15.1")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${protocVersion}"
    }
    
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${grpcVersion}"
        }
        id("quarkusGrpc") {
            artifact = "io.quarkus:quarkus-grpc-protoc-plugin:3.15.1:exe:linux-x86_64"
        }
    }
    
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
                id("quarkusGrpc")
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Make generated sources part of the main source set
sourceSets {
    main {
        java {
            srcDirs("build/generated/source/proto/main/grpc")
            srcDirs("build/generated/source/proto/main/java")
            srcDirs("build/generated/source/proto/main/quarkusGrpc")
        }
    }
}