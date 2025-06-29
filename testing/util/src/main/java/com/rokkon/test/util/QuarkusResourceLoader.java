package com.rokkon.test.util;

import io.quarkus.runtime.util.ClassPathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Utility class for loading resources in a Quarkus-compatible way.
 * This handles the differences between:
 * - Development mode (files on filesystem)
 * - Test mode (classpath resources)
 * - Production mode (resources in JAR)
 * - Native mode (resources compiled into binary)
 */
public class QuarkusResourceLoader {
    private static final Logger LOG = LoggerFactory.getLogger(QuarkusResourceLoader.class);
    
    /**
     * Load a resource as an InputStream using Quarkus-compatible methods.
     * 
     * @param resourcePath The path to the resource (should start with /)
     * @return InputStream or null if not found
     */
    public static InputStream loadResource(String resourcePath) {
        // First try the Quarkus way
        try {
            URL[] urls = new URL[1];
            ClassPathUtils.consumeAsPaths(resourcePath, path -> {
                try {
                    urls[0] = path.toUri().toURL();
                } catch (Exception e) {
                    LOG.debug("Failed to load resource via ClassPathUtils: {}", resourcePath);
                }
            });
            
            if (urls[0] != null) {
                return urls[0].openStream();
            }
        } catch (Exception e) {
            LOG.debug("ClassPathUtils failed for {}: {}", resourcePath, e.getMessage());
        }
        
        // Fallback to traditional classloader
        InputStream stream = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream(resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath);
        
        if (stream == null) {
            // Try without leading slash
            stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath.startsWith("/") ? resourcePath : resourcePath.substring(1));
        }
        
        if (stream == null) {
            LOG.warn("Failed to load resource: {}", resourcePath);
        }
        
        return stream;
    }
    
    /**
     * List files in a resource directory. This is challenging in Quarkus/JAR environments.
     * 
     * @param directoryPath The directory path in resources
     * @param fileExtension File extension to filter (without dot)
     * @return List of resource paths
     */
    public static List<String> listResourceFiles(String directoryPath, String fileExtension) {
        List<String> resources = new ArrayList<>();
        
        // Remove leading/trailing slashes
        String cleanPath = directoryPath.replaceAll("^/+|/+$", "");
        
        // Try filesystem first (development mode)
        try {
            Path devPath = Paths.get("src/main/resources", cleanPath);
            if (Files.exists(devPath) && Files.isDirectory(devPath)) {
                try (Stream<Path> paths = Files.walk(devPath)) {
                    paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith("." + fileExtension))
                        .forEach(p -> {
                            // Convert to resource path
                            String resourcePath = "/" + cleanPath + "/" + devPath.relativize(p).toString().replace('\\', '/');
                            resources.add(resourcePath);
                        });
                }
                if (!resources.isEmpty()) {
                    LOG.debug("Found {} resources in filesystem: {}", resources.size(), devPath);
                    return resources;
                }
            }
        } catch (IOException e) {
            LOG.debug("Filesystem search failed: {}", e.getMessage());
        }
        
        // For JAR/production mode, we need to use known patterns
        // This is because you cannot list files in a JAR without special handling
        LOG.debug("Falling back to pattern-based resource loading for: {}", directoryPath);
        
        // Return empty list - the caller should use pattern-based loading
        // or maintain a manifest of known resources
        return resources;
    }
    
    /**
     * Check if we're running in development mode
     */
    public static boolean isDevMode() {
        // Check if we can access src/main/resources directly
        return Files.exists(Paths.get("src/main/resources"));
    }
    
    /**
     * Load a resource with multiple fallback locations.
     * Useful when resources might be in different locations in different environments.
     * 
     * @param possiblePaths Array of possible resource paths to try
     * @return InputStream of the first found resource, or null
     */
    public static InputStream loadResourceWithFallbacks(String... possiblePaths) {
        for (String path : possiblePaths) {
            InputStream stream = loadResource(path);
            if (stream != null) {
                LOG.debug("Successfully loaded resource from: {}", path);
                return stream;
            }
        }
        LOG.warn("Failed to load resource from any of the paths: {}", (Object[]) possiblePaths);
        return null;
    }
}