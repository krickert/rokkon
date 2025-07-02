package com.rokkon.integration;

import com.rokkon.test.containers.EngineContainerResource;

/**
 * Engine container resource that uses local Docker images with project version.
 */
public class LocalEngineContainerResource extends EngineContainerResource {
    
    public LocalEngineContainerResource() {
        // Use the project version from system property
        super(getLocalImageName());
    }
    
    private static String getLocalImageName() {
        String projectVersion = System.getProperty("project.version", "1.0.0-SNAPSHOT");
        String imageName = "rokkon/rokkon-engine:" + projectVersion;
        System.out.println("Using local engine image: " + imageName);
        return imageName;
    }
}