// Root build file for composite build
// This is intentionally minimal - each included build is independent

tasks.register("buildAll") {
    description = "Builds all included projects"
    doLast {
        // First, build and publish proto-definitions as other projects depend on it
        val protoDefsBuild = gradle.includedBuilds.find { it.name == "proto-definitions" }
        if (protoDefsBuild != null) {
            exec {
                commandLine("./gradlew", "-p", protoDefsBuild.projectDir.absolutePath, "build", "publishToMavenLocal")
            }
            logger.lifecycle("✓ Proto-definitions built and published to Maven local")
        }
        
        // Then build each project sequentially to avoid port conflicts during tests
        gradle.includedBuilds.forEach { build ->
            if (build.name != "proto-definitions") { // Skip proto-definitions as we already built it
                logger.lifecycle("Building ${build.name}...")
                exec {
                    commandLine("./gradlew", "-p", build.projectDir.absolutePath, "build")
                }
                logger.lifecycle("✓ ${build.name} built successfully")
            }
        }
    }
}

tasks.register("buildAllParallel") {
    description = "Builds all included projects in parallel"
    doFirst {
        // First, build and publish proto-definitions as other projects depend on it
        val protoDefsBuild = gradle.includedBuilds.find { it.name == "proto-definitions" }
        if (protoDefsBuild != null) {
            exec {
                commandLine("./gradlew", "-p", protoDefsBuild.projectDir.absolutePath, "build", "publishToMavenLocal")
            }
            logger.lifecycle("✓ Proto-definitions built and published to Maven local")
        }
    }
    // Then build all projects in parallel
    dependsOn(gradle.includedBuilds.map { it.task(":build") })
}

tasks.register("buildAllNoTest") {
    description = "Builds all included projects without running tests"
    dependsOn(gradle.includedBuilds.map { it.task(":assemble") })
}

tasks.register("testAll") {
    description = "Runs all tests in all included projects"
    dependsOn(gradle.includedBuilds.map { it.task(":test") })
}

tasks.register("cleanAll") {
    description = "Cleans all included projects"
    dependsOn(gradle.includedBuilds.map { it.task(":clean") })
}