rootProject.name = "rokkon-engine-workspace"

// Composite builds - each is independent
includeBuild("proto-definitions")
includeBuild("test-utilities")
includeBuild("engine-models")
includeBuild("engine-validators")
includeBuild("engine-registration")
includeBuild("engine-consul")
includeBuild("modules/echo")
includeBuild("modules/chunker")
includeBuild("modules/parser")
includeBuild("modules/embedder")
includeBuild("modules/test-module")
includeBuild("rokkon-engine")
