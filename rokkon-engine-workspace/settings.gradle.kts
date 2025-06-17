rootProject.name = "rokkon-engine-workspace"

// Composite builds - each is independent
includeBuild("proto-definitions")
includeBuild("test-utilities")
includeBuild("modules/echo")
includeBuild("modules/chunker")
includeBuild("modules/parser")
