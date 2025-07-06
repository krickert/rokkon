package com.rokkon.pipeline.engine.dev;

/**
 * Enum representing available pipeline modules for dev mode.
 * Each module includes its Docker image and resource requirements.
 * All modules use unified server mode with internal port 39100.
 * External ports are dynamically allocated during deployment.
 */
public enum PipelineModule {
    ECHO("echo", "pipeline/echo:latest", "Simple echo module for testing", "1G"),
    TEST("test-module", "pipeline/test-module:latest", "Test module for pipeline validation", "1G"),
    PARSER("parser", "pipeline/parser-module:latest", "Document parser module", "1G"),
    CHUNKER("chunker", "pipeline/chunker:latest", "Text chunking module with NLP", "4G"),
    EMBEDDER("embedder", "pipeline/embedder:latest", "ML embedding module", "8G");
    
    private final String moduleName;
    private final String dockerImage;
    private final String description;
    private final String defaultMemory;
    
    PipelineModule(String moduleName, String dockerImage, String description, String defaultMemory) {
        this.moduleName = moduleName;
        this.dockerImage = dockerImage;
        this.description = description;
        this.defaultMemory = defaultMemory;
    }
    
    public String getModuleName() { 
        return moduleName; 
    }
    
    public String getDockerImage() { 
        return dockerImage; 
    }
    
    public String getDescription() { 
        return description; 
    }
    
    public String getDefaultMemory() { 
        return defaultMemory; 
    }
    
    public String getContainerName() {
        return moduleName + "-module-app";
    }
    
    public String getSidecarName() {
        return "consul-agent-" + moduleName;
    }
    
    public static PipelineModule fromName(String name) {
        for (PipelineModule module : values()) {
            if (module.moduleName.equalsIgnoreCase(name)) {
                return module;
            }
        }
        throw new IllegalArgumentException("Unknown module: " + name);
    }
}