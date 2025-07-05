import org.gradle.api.Task
import org.gradle.kotlin.dsl.*

// Custom plugin to optimize Quarkus gRPC code generation
// This configures the quarkusGenerateCode task to be cacheable and incremental

afterEvaluate {
    // Configure quarkusGenerateCode task for better caching
    tasks.findByName("quarkusGenerateCode")?.let { task ->
        task.apply {
            // Mark outputs as cacheable
            outputs.cacheIf { true }
            
            // Add proto files as inputs for proper up-to-date checking
            val protoFiles = fileTree("src/main/proto") {
                include("**/*.proto")
            }
            
            if (!protoFiles.isEmpty) {
                inputs.files(protoFiles)
                    .withPropertyName("protoFiles")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
            }
            
            // Add generated sources directory as output
            val generatedSourcesDir = layout.buildDirectory.dir("classes/java/quarkus-generated-sources")
            outputs.dir(generatedSourcesDir)
                .withPropertyName("generatedSources")
            
            // Configure task to skip when proto files haven't changed
            onlyIf {
                val hasProtoChanges = protoFiles.any { file ->
                    file.lastModified() > outputs.files.singleFile.lastModified()
                }
                hasProtoChanges || !generatedSourcesDir.get().asFile.exists()
            }
            
            doFirst {
                logger.lifecycle("Running protobuf code generation for ${project.name}")
            }
        }
    }
    
    // Same for dev mode
    tasks.findByName("quarkusGenerateCodeDev")?.let { task ->
        task.apply {
            outputs.cacheIf { true }
            
            val protoFiles = fileTree("src/main/proto") {
                include("**/*.proto")
            }
            
            if (!protoFiles.isEmpty) {
                inputs.files(protoFiles)
                    .withPropertyName("protoFiles")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
            }
            
            val generatedSourcesDir = layout.buildDirectory.dir("classes/java/quarkus-generated-sources")
            outputs.dir(generatedSourcesDir)
                .withPropertyName("generatedSources")
        }
    }
    
    // Configure compile tasks to depend on code generation properly
    tasks.withType<JavaCompile>().configureEach {
        mustRunAfter("quarkusGenerateCode", "quarkusGenerateCodeDev")
    }
}

// Extension function to check if a FileTree is empty
fun FileTree.isEmpty(): Boolean = this.files.isEmpty()