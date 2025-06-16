// Root build file for composite build
// This is intentionally minimal - each included build is independent

tasks.register("buildAll") {
    description = "Builds all included projects"
    dependsOn(gradle.includedBuilds.map { it.task(":build") })
}

tasks.register("cleanAll") {
    description = "Cleans all included projects"
    dependsOn(gradle.includedBuilds.map { it.task(":clean") })
}