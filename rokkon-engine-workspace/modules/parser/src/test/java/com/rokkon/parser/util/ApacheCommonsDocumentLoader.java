package com.rokkon.parser.util;

import com.rokkon.search.model.PipeDoc;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Test document loader that can handle both JAR files and directories.
 * Uses NIO filesystem to properly handle resources in JARs.
 */
public class ApacheCommonsDocumentLoader {
    private static final Logger LOG = Logger.getLogger(ApacheCommonsDocumentLoader.class);
    
    /**
     * Load all .bin test documents from the classpath.
     */
    public static List<PipeDoc> loadAllTestDocuments() {
        List<PipeDoc> documents = new ArrayList<>();
        
        // Try different known test data directories
        String[] testDataPaths = {
            "/test-data/tika-pipe-docs-large",
            "/test-data/parser-test-documents",
            "/test-data/simple-documents"
        };
        
        for (String directory : testDataPaths) {
            try {
                documents.addAll(loadDocumentsFromDirectory(directory));
            } catch (Exception e) {
                LOG.debugf("Could not load from %s: %s", directory, e.getMessage());
            }
        }
        
        LOG.infof("Loaded %d test documents total", documents.size());
        return documents;
    }
    
    /**
     * Load documents from a directory path that may be in a JAR or filesystem.
     */
    private static List<PipeDoc> loadDocumentsFromDirectory(String directory) {
        List<PipeDoc> documents = new ArrayList<>();
        
        try {
            Stream<Path> paths = getPathsFromDirectory(directory);
            paths.forEach(path -> {
                try {
                    String filename = path.getFileName().toString();
                    if (filename.endsWith(".bin") && !filename.equals(directory)) {
                        PipeDoc doc = PipeDoc.parseFrom(Files.newInputStream(path));
                        documents.add(doc);
                        LOG.debugf("Loaded document: %s", filename);
                    }
                } catch (IOException e) {
                    LOG.errorf(e, "Error loading document from path: %s", path);
                }
            });
            paths.close();
        } catch (Exception e) {
            LOG.debugf("Could not access directory %s: %s", directory, e.getMessage());
        }
        
        return documents;
    }
    
    /**
     * Get paths from a directory, handling both JAR and filesystem resources.
     */
    private static Stream<Path> getPathsFromDirectory(String directory) {
        URI uri;
        try {
            URL resource = ApacheCommonsDocumentLoader.class.getResource(directory);
            if (resource == null) {
                throw new RuntimeException("Directory not found: " + directory);
            }
            uri = resource.toURI();
        } catch (URISyntaxException | NullPointerException e) {
            throw new RuntimeException("Error getting URI for directory: " + directory, e);
        }
        
        Path myPath;
        if (uri.getScheme().equals("jar")) {
            FileSystem fileSystem = null;
            try {
                fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
                myPath = fileSystem.getPath(directory);
            } catch (IOException e) {
                throw new RuntimeException("Error creating filesystem for JAR", e);
            } catch (FileSystemAlreadyExistsException e) {
                fileSystem = FileSystems.getFileSystem(uri);
                myPath = fileSystem.getPath(directory);
            }
        } else {
            myPath = Paths.get(uri);
        }
        
        try {
            return Files.walk(myPath, 1);
        } catch (IOException e) {
            throw new RuntimeException("Error walking directory: " + directory, e);
        }
    }
}