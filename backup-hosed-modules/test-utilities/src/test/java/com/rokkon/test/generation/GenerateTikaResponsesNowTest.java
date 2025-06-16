package com.rokkon.test.generation;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.test.protobuf.ProtobufUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * One-time test to generate Tika response documents from the 126 input streams.
 * DISABLED - This has been run successfully and generated all 126 response documents.
 * Re-enable only if you need to regenerate the response data.
 */
@QuarkusTest
@Disabled("Already completed - 126 response documents generated successfully")
class GenerateTikaResponsesNowTest {
    private static final Logger LOG = LoggerFactory.getLogger(GenerateTikaResponsesNowTest.class);
    
    @Test
    void generateTikaResponses() throws IOException {
        LOG.info("=== Generating Tika Response Documents from 126 Input Streams ===");
        
        // Load all 126 input streams
        List<PipeStream> inputStreams = loadTikaInputStreams();
        LOG.info("Loaded {} input streams", inputStreams.size());
        
        assertEquals(126, inputStreams.size(), "Should have 126 input streams");
        
        // Generate response documents using the existing generator
        List<PipeDoc> responseDocs = TikaResponseGenerator.createTikaResponseDocs(inputStreams);
        LOG.info("Generated {} response documents", responseDocs.size());
        
        assertEquals(126, responseDocs.size(), "Should have 126 response documents");
        
        // Save response documents
        String outputDir = "src/main/resources/test-data/tika/responses";
        Path outputPath = Paths.get(outputDir);
        
        // Clean existing response files
        if (Files.exists(outputPath)) {
            LOG.info("Cleaning existing Tika response files");
            Files.list(outputPath)
                .filter(path -> path.toString().endsWith(".bin"))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        LOG.debug("Deleted old response file: {}", path.getFileName());
                    } catch (IOException e) {
                        LOG.warn("Failed to delete old response file: {}", path.getFileName());
                    }
                });
        } else {
            Files.createDirectories(outputPath);
            LOG.info("Created response directory: {}", outputPath);
        }
        
        // Save new response documents
        for (int i = 0; i < responseDocs.size(); i++) {
            PipeDoc doc = responseDocs.get(i);
            String filename = String.format("tika_response_%03d.bin", i);
            String filePath = outputPath.resolve(filename).toString();
            
            ProtobufUtils.saveProtobufToDisk(filePath, doc);
            
            if (i < 10 || i % 25 == 0) {
                LOG.info("Saved response doc {}: {} ({} chars extracted)", 
                    i, filename, doc.getBody().length());
            }
        }
        
        LOG.info("=== Successfully saved {} Tika response documents ===", responseDocs.size());
        
        // Verify files were created
        long fileCount = Files.list(outputPath)
            .filter(path -> path.toString().endsWith(".bin"))
            .count();
        
        assertEquals(responseDocs.size(), fileCount, "Should have created all response .bin files");
        LOG.info("Verified {} response .bin files created", fileCount);
        
        // Sample some generated content to verify quality
        for (int i = 0; i < Math.min(3, responseDocs.size()); i++) {
            PipeDoc doc = responseDocs.get(i);
            LOG.info("Sample response doc {}: Title='{}', MimeType='{}', Body length={}", 
                i, doc.getTitle(), doc.getSourceMimeType(), doc.getBody().length());
        }
    }
    
    private List<PipeStream> loadTikaInputStreams() throws IOException {
        String inputDir = "src/main/resources/test-data/tika/requests";
        Path inputPath = Paths.get(inputDir);
        
        if (!Files.exists(inputPath)) {
            throw new IllegalStateException("Tika input directory does not exist: " + inputPath);
        }
        
        List<PipeStream> streams = new ArrayList<>();
        
        // Load all .bin files in numerical order
        List<Path> binFiles = Files.list(inputPath)
            .filter(path -> path.toString().endsWith(".bin"))
            .sorted()
            .toList();
        
        for (Path binFile : binFiles) {
            try {
                PipeStream stream = ProtobufUtils.loadPipeStreamFromDisk(binFile.toString());
                streams.add(stream);
                LOG.debug("Loaded input stream: {}", binFile.getFileName());
            } catch (Exception e) {
                LOG.error("Failed to load input stream: {} - {}", binFile.getFileName(), e.getMessage());
                throw new RuntimeException("Failed to load input stream: " + binFile.getFileName(), e);
            }
        }
        
        return streams;
    }
}