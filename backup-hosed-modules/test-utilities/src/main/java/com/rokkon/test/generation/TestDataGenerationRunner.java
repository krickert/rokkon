package com.rokkon.test.generation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Standalone utility to generate test data files.
 * Can be run from the command line or from tests.
 */
public class TestDataGenerationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(TestDataGenerationRunner.class);
    
    public static void main(String[] args) {
        try {
            generateAllTestData();
            LOG.info("Test data generation completed successfully");
        } catch (Exception e) {
            LOG.error("Test data generation failed", e);
            System.exit(1);
        }
    }
    
    /**
     * Generates all test data and saves it to the test-utilities resources directory.
     */
    public static void generateAllTestData() throws Exception {
        LOG.info("Starting test data generation...");
        
        // Determine the base path for the test-utilities module
        String basePath = determineTestUtilitiesPath();
        LOG.info("Using base path: {}", basePath);
        
        // Generate Tika test data
        generateTikaTestData(basePath);
        
        LOG.info("Test data generation completed");
    }
    
    /**
     * Generates Tika-specific test data.
     */
    private static void generateTikaTestData(String basePath) throws Exception {
        LOG.info("Generating Tika test data...");
        
        String resourcesPath = basePath + "/src/main/resources";
        String tikaRequestsPath = resourcesPath + "/test-data/tika/requests";
        String tikaResponsesPath = resourcesPath + "/test-data/tika/responses";
        
        // Create directories
        createDirectoryIfNotExists(tikaRequestsPath);
        createDirectoryIfNotExists(tikaResponsesPath);
        
        // Clean existing files
        TestDataGenerator.cleanDirectory(tikaRequestsPath);
        TestDataGenerator.cleanDirectory(tikaResponsesPath);
        
        // Generate test data
        TikaTestDataGenerator.generateTikaRequests(tikaRequestsPath);
        TikaTestDataGenerator.generateTikaResponses(tikaResponsesPath);
        
        LOG.info("Tika test data generated successfully");
        LOG.info("Request files: {}", tikaRequestsPath);
        LOG.info("Response files: {}", tikaResponsesPath);
    }
    
    /**
     * Determines the path to the test-utilities module.
     */
    private static String determineTestUtilitiesPath() {
        // Try to find the test-utilities directory
        String currentDir = System.getProperty("user.dir");
        LOG.debug("Current directory: {}", currentDir);
        
        // Check if we're already in test-utilities
        if (currentDir.endsWith("test-utilities")) {
            return currentDir;
        }
        
        // Check if we're in the main rokkon-engine directory
        Path testUtilitiesPath = Paths.get(currentDir, "modules", "test-utilities");
        if (Files.exists(testUtilitiesPath)) {
            return testUtilitiesPath.toString();
        }
        
        // Check if we're in the modules directory
        testUtilitiesPath = Paths.get(currentDir, "test-utilities");
        if (Files.exists(testUtilitiesPath)) {
            return testUtilitiesPath.toString();
        }
        
        // Default fallback
        throw new RuntimeException("Could not determine test-utilities path from: " + currentDir);
    }
    
    /**
     * Creates a directory if it doesn't exist.
     */
    private static void createDirectoryIfNotExists(String directoryPath) throws Exception {
        Path path = Paths.get(directoryPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            LOG.info("Created directory: {}", directoryPath);
        } else {
            LOG.debug("Directory already exists: {}", directoryPath);
        }
    }
}