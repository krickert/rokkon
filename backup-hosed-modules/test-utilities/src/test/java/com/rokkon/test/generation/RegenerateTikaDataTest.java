package com.rokkon.test.generation;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.test.protobuf.ProtobufUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to regenerate Tika input and response data with realistic large documents.
 * This is controlled by system property: -Dgenerate.tika.data=true
 * 
 * Usage: ./gradlew test -Dgenerate.tika.data=true --tests "RegenerateTikaDataTest"
 */
@QuarkusTest
@EnabledIfSystemProperty(named = "generate.tika.data", matches = "true")
class RegenerateTikaDataTest {
    private static final Logger LOG = LoggerFactory.getLogger(RegenerateTikaDataTest.class);
    
    @Test
    void regenerateTikaInputStreams() throws IOException {
        LOG.info("=== Regenerating Tika Input Streams with Real Documents ===");
        
        // Generate new input streams with actual document content
        List<PipeStream> inputStreams = TikaTestDataGenerator.createTikaRequestStreams();
        
        assertFalse(inputStreams.isEmpty(), "Should generate input streams");
        assertTrue(inputStreams.size() >= 21, "Should have at least 21 base documents");
        LOG.info("Generated {} Tika input streams", inputStreams.size());
        
        // Calculate total size and verify we have substantial documents
        long totalSize = 0;
        long maxSize = 0;
        long minSize = Long.MAX_VALUE;
        
        for (int i = 0; i < Math.min(10, inputStreams.size()); i++) {
            PipeStream stream = inputStreams.get(i);
            assertTrue(stream.hasDocument(), "Stream should contain document");
            assertTrue(stream.getDocument().hasBlob(), "Document should have blob data");
            
            long size = stream.getDocument().getBlob().getData().size();
            totalSize += size;
            maxSize = Math.max(maxSize, size);
            minSize = Math.min(minSize, size);
            
            LOG.info("Stream {}: {} - {} bytes ({})", 
                i, stream.getDocument().getTitle(), size, stream.getDocument().getSourceMimeType());
        }
        
        double avgSize = (double) totalSize / Math.min(10, inputStreams.size());
        
        LOG.info("Document size statistics (first 10):");
        LOG.info("  Average size: {:.1f} bytes ({:.1f} KB)", avgSize, avgSize / 1024);
        LOG.info("  Min size: {} bytes ({} KB)", minSize, minSize / 1024);
        LOG.info("  Max size: {} bytes ({} MB)", maxSize, maxSize / 1024 / 1024);
        
        // Verify we have much larger documents than before
        assertTrue(maxSize > 100000, "Should have documents larger than 100KB");
        assertTrue(avgSize > 10000, "Average size should be substantial");
        
        // Save new input streams
        String outputDir = "src/main/resources/test-data/tika/requests";
        Path outputPath = Paths.get(outputDir);
        
        // Clean existing files
        if (Files.exists(outputPath)) {
            Files.list(outputPath)
                .filter(path -> path.toString().endsWith(".bin"))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        LOG.warn("Failed to delete old file: {}", path);
                    }
                });
        } else {
            Files.createDirectories(outputPath);
        }
        
        // Save new input streams
        for (int i = 0; i < inputStreams.size(); i++) {
            PipeStream stream = inputStreams.get(i);
            String filename = String.format("tika_request_%03d.bin", i);
            String filePath = outputPath.resolve(filename).toString();
            
            ProtobufUtils.saveProtobufToDisk(filePath, stream);
            
            if (i < 10 || i % 20 == 0) {
                LOG.info("Saved input stream {}: {}", i, filename);
            }
        }
        
        LOG.info("=== Successfully regenerated {} Tika input streams ===", inputStreams.size());
        
        // Verify files were created
        long fileCount = Files.list(outputPath)
            .filter(path -> path.toString().endsWith(".bin"))
            .count();
        
        assertEquals(inputStreams.size(), fileCount, "Should have created all .bin files");
        LOG.info("Verified {} .bin files created", fileCount);
    }
    
    @Test
    void regenerateTikaResponseDocs() throws IOException {
        LOG.info("=== Regenerating Tika Response Documents ===");
        
        // First load the input streams to get the same document count
        List<PipeStream> inputStreams = TikaTestDataGenerator.createTikaRequestStreams();
        
        // Generate realistic response documents with extracted text
        List<PipeDoc> responseDocs = TikaResponseGenerator.createTikaResponseDocs(inputStreams);
        
        assertEquals(inputStreams.size(), responseDocs.size(), "Should have matching response docs");
        LOG.info("Generated {} Tika response documents", responseDocs.size());
        
        // Verify response documents have substantial extracted text
        for (int i = 0; i < Math.min(5, responseDocs.size()); i++) {
            PipeDoc doc = responseDocs.get(i);
            assertNotNull(doc.getId(), "Document should have ID");
            assertNotNull(doc.getTitle(), "Document should have title");
            assertNotNull(doc.getBody(), "Document should have extracted text");
            assertFalse(doc.hasBlob(), "Response doc should not have blob");
            assertTrue(doc.getBody().length() > 500, "Should have substantial extracted text");
            
            LOG.info("Response doc {}: {} - {} chars extracted", 
                i, doc.getTitle(), doc.getBody().length());
        }
        
        // Save response documents
        String outputDir = "src/main/resources/test-data/tika/responses";
        Path outputPath = Paths.get(outputDir);
        
        // Clean existing files
        if (Files.exists(outputPath)) {
            Files.list(outputPath)
                .filter(path -> path.toString().endsWith(".bin"))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        LOG.warn("Failed to delete old response file: {}", path);
                    }
                });
        } else {
            Files.createDirectories(outputPath);
        }
        
        // Save new response documents
        for (int i = 0; i < responseDocs.size(); i++) {
            PipeDoc doc = responseDocs.get(i);
            String filename = String.format("tika_response_%03d.bin", i);
            String filePath = outputPath.resolve(filename).toString();
            
            ProtobufUtils.saveProtobufToDisk(filePath, doc);
            
            if (i < 10 || i % 20 == 0) {
                LOG.info("Saved response doc {}: {}", i, filename);
            }
        }
        
        LOG.info("=== Successfully regenerated {} Tika response documents ===", responseDocs.size());
        
        // Verify files were created
        long fileCount = Files.list(outputPath)
            .filter(path -> path.toString().endsWith(".bin"))
            .count();
        
        assertEquals(responseDocs.size(), fileCount, "Should have created all response .bin files");
        LOG.info("Verified {} response .bin files created", fileCount);
    }
}