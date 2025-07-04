package com.rokkon.pipeline.engine.dev;

/**
 * Enum representing available pipeline modules for dev mode.
 * Each module includes its Docker image, port configuration, and resource requirements.
 * Modules use unified server mode (single port for both HTTP and gRPC).
 */
public enum PipelineModule {
    ECHO("echo", "pipeline/echo-module:latest", 39100, "Simple echo module for testing", "1G"),
    TEST("test", "pipeline/test-module:latest", 39101, "Test module for pipeline validation", "1G"),
    PARSER("parser", "pipeline/parser-module:latest", 39102, "Document parser module", "1G"),
    CHUNKER("chunker", "pipeline/chunker:latest", 39103, "Text chunking module with NLP", "4G"),
    EMBEDDER("embedder", "pipeline/embedder:latest", 39104, "ML embedding module", "8G");
    
    private final String moduleName;
    private final String dockerImage;
    private final int unifiedPort;
    private final String description;
    private final String defaultMemory;
    
    PipelineModule(String moduleName, String dockerImage, int unifiedPort, String description, String defaultMemory) {
        this.moduleName = moduleName;
        this.dockerImage = dockerImage;
        this.unifiedPort = unifiedPort;
        this.description = description;
        this.defaultMemory = defaultMemory;
    }
    
    public String getModuleName() { 
        return moduleName; 
    }
    
    public String getDockerImage() { 
        return dockerImage; 
    }
    
    public int getUnifiedPort() {
        return unifiedPort;
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