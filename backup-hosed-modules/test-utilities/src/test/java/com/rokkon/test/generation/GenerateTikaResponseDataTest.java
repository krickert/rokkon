package com.rokkon.test.generation;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.test.data.TikaTestDataHelper;
import com.rokkon.test.protobuf.ProtobufUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to generate Tika response data (extracted text) from the input streams.
 */
@QuarkusTest
class GenerateTikaResponseDataTest {
    private static final Logger LOG = LoggerFactory.getLogger(GenerateTikaResponseDataTest.class);
    
    @Inject
    TikaTestDataHelper tikaTestDataHelper;
    
    @Test
    void generateTikaResponseData() throws IOException {
        LOG.info("=== Generating Tika Response Data ===");
        
        // Load the input streams
        List<PipeStream> inputStreams = tikaTestDataHelper.getTikaRequestStreams();
        assertTrue(inputStreams.size() > 0, "Should have input streams available");
        LOG.info("Loaded {} Tika request streams", inputStreams.size());
        
        // Generate response documents with extracted text
        List<PipeDoc> responseDocs = TikaResponseGenerator.createTikaResponseDocs(inputStreams);
        assertEquals(inputStreams.size(), responseDocs.size(), "Should generate response documents for all input streams");
        LOG.info("Generated {} Tika response documents", responseDocs.size());
        
        // Verify response document structure
        for (int i = 0; i < Math.min(5, responseDocs.size()); i++) {
            PipeDoc doc = responseDocs.get(i);
            
            assertNotNull(doc.getId(), "Document should have an ID");
            assertNotNull(doc.getTitle(), "Document should have a title");
            assertNotNull(doc.getBody(), "Document should have extracted text body");
            assertFalse(doc.getBody().isEmpty(), "Body should not be empty");
            assertTrue(doc.getBody().length() > 100, "Body should have substantial content");
            assertNotNull(doc.getSourceMimeType(), "Document should have source MIME type");
            assertFalse(doc.hasBlob(), "Response document should not have blob data");
            
            LOG.info("Response doc {}: {} - {} chars extracted", 
                i, doc.getTitle(), doc.getBody().length());
        }
        
        // Save response documents to resources directory
        Path currentDir = Paths.get("").toAbsolutePath();
        Path outputPath = currentDir.resolve("src/main/resources/test-data/tika/responses");
        
        // Create directory if it doesn't exist
        Files.createDirectories(outputPath);
        
        // Clean up any existing .bin files from previous runs
        try {
            Files.list(outputPath)
                .filter(path -> path.toString().endsWith(".bin"))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (Exception e) {
                        LOG.warn("Failed to delete existing file: {}", path);
                    }
                });
        } catch (Exception e) {
            LOG.warn("Failed to clean up existing files in {}: {}", outputPath, e.getMessage());
        }
        
        LOG.info("Saving Tika response data to: {}", outputPath.toAbsolutePath());
        
        // Save each response document as a .bin file
        for (int i = 0; i < responseDocs.size(); i++) {
            PipeDoc doc = responseDocs.get(i);
            String filename = String.format("tika_response_%03d.bin", i);
            String filePath = outputPath.resolve(filename).toString();
            
            ProtobufUtils.saveProtobufToDisk(filePath, doc);
            
            if (i < 10 || i % 20 == 0) {
                LOG.info("Saved response doc {}: {}", i, filename);
            }
        }
        
        LOG.info("=== Successfully saved {} Tika response documents ===", responseDocs.size());
        
        // Verify files were created
        long fileCount = Files.list(outputPath)
            .filter(path -> path.toString().endsWith(".bin"))
            .count();
        
        assertEquals(responseDocs.size(), fileCount, "Should have created .bin files for all response documents");
        LOG.info("Verified {} .bin files created in output directory", fileCount);
        
        // Test loading the files back
        List<PipeDoc> loadedDocs = ProtobufUtils.loadPipeDocsFromDirectory("/test-data/tika/responses", ".bin");
        // Note: This might be empty if running outside JAR context, but the files should exist on disk
        LOG.info("Loaded {} documents from classpath (may be 0 if not in JAR)", loadedDocs.size());
        
        LOG.info("=== Tika Response Data Generation Complete ===");
    }
}