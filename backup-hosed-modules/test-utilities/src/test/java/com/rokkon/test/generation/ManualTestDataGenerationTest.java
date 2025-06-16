package com.rokkon.test.generation;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.test.protobuf.ProtobufUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Manual test to generate and save test data files.
 * Run this test to create the actual .bin files in the resources directory.
 */
class ManualTestDataGenerationTest {
    private static final Logger LOG = LoggerFactory.getLogger(ManualTestDataGenerationTest.class);
    
    @Test
    void generateTestDataManually() throws Exception {
        LOG.info("=== MANUALLY GENERATING TEST DATA ===");
        
        // Get the current working directory and find test-utilities
        String currentDir = System.getProperty("user.dir");
        LOG.info("Current directory: {}", currentDir);
        
        // Navigate to the correct test-utilities path
        Path testUtilitiesPath;
        if (currentDir.contains("test-utilities")) {
            testUtilitiesPath = Paths.get(currentDir);
        } else {
            testUtilitiesPath = Paths.get(currentDir).resolve("modules").resolve("test-utilities");
        }
        
        Path resourcesPath = testUtilitiesPath.resolve("src").resolve("main").resolve("resources");
        Path tikaRequestsPath = resourcesPath.resolve("test-data").resolve("tika").resolve("requests");
        Path tikaResponsesPath = resourcesPath.resolve("test-data").resolve("tika").resolve("responses");
        
        LOG.info("Target paths:");
        LOG.info("  Resources: {}", resourcesPath);
        LOG.info("  Requests: {}", tikaRequestsPath);
        LOG.info("  Responses: {}", tikaResponsesPath);
        
        // Create directories
        Files.createDirectories(tikaRequestsPath);
        Files.createDirectories(tikaResponsesPath);
        
        // Generate Tika request streams
        LOG.info("Generating Tika request streams...");
        List<PipeStream> requestStreams = TikaTestDataGenerator.createTikaRequestStreams();
        LOG.info("Generated {} request streams", requestStreams.size());
        
        if (!requestStreams.isEmpty()) {
            // Save request streams
            String requestPrefix = tikaRequestsPath.resolve("tika_request").toString();
            ProtobufUtils.saveProtobufsToDisk(requestPrefix + "_", requestStreams);
            LOG.info("Saved {} request streams to {}", requestStreams.size(), tikaRequestsPath);
        } else {
            LOG.warn("No request streams were generated - likely missing test resource files");
        }
        
        // Generate Tika response documents
        LOG.info("Generating Tika response documents...");
        List<PipeDoc> responseDocs = TikaTestDataGenerator.createTikaResponseDocs();
        
        // Save response documents
        String responsePrefix = tikaResponsesPath.resolve("tika_response").toString();
        ProtobufUtils.saveProtobufsToDisk(responsePrefix + "_", responseDocs);
        LOG.info("Saved {} response documents to {}", responseDocs.size(), tikaResponsesPath);
        
        // Verify files were created
        long requestCount = Files.list(tikaRequestsPath).filter(p -> p.toString().endsWith(".bin")).count();
        long responseCount = Files.list(tikaResponsesPath).filter(p -> p.toString().endsWith(".bin")).count();
        
        LOG.info("=== GENERATION COMPLETE ===");
        LOG.info("Created {} request files in {}", requestCount, tikaRequestsPath);
        LOG.info("Created {} response files in {}", responseCount, tikaResponsesPath);
        
        // List some sample files
        LOG.info("Sample request files:");
        Files.list(tikaRequestsPath).filter(p -> p.toString().endsWith(".bin")).limit(3)
            .forEach(p -> LOG.info("  {}", p.getFileName()));
        
        LOG.info("Sample response files:");
        Files.list(tikaResponsesPath).filter(p -> p.toString().endsWith(".bin")).limit(3)
            .forEach(p -> LOG.info("  {}", p.getFileName()));
    }
}