package com.rokkon.test.generation;

import com.rokkon.search.model.PipeStream;
import com.rokkon.test.data.TikaTestDataHelper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that the generated input streams are properly created and can be loaded.
 */
@QuarkusTest
class VerifyGeneratedDataTest {
    private static final Logger LOG = LoggerFactory.getLogger(VerifyGeneratedDataTest.class);
    
    @Inject
    TikaTestDataHelper tikaTestDataHelper;
    
    @Test
    void testLoadTikaInputStreams() {
        LOG.info("Testing loading of Tika input streams...");
        
        // Load the input streams
        List<PipeStream> inputStreams = tikaTestDataHelper.getTikaRequestStreams();
        LOG.info("Loaded {} Tika input streams", inputStreams.size());
        
        // Verify we have input streams available
        assertTrue(inputStreams.size() > 0, "Should have input streams available");
        
        // Verify structure of first few streams
        for (int i = 0; i < Math.min(5, inputStreams.size()); i++) {
            PipeStream stream = inputStreams.get(i);
            
            // Verify stream structure
            assertNotNull(stream.getStreamId(), "Stream should have an ID");
            assertTrue(stream.hasDocument(), "Stream should contain a document");
            assertEquals("test-tika-pipeline", stream.getCurrentPipelineName(), "Pipeline name should be set");
            assertEquals("tika-parser", stream.getTargetStepName(), "Target step should be tika-parser");
            
            // Verify document structure
            var doc = stream.getDocument();
            assertNotNull(doc.getId(), "Document should have an ID");
            assertNotNull(doc.getTitle(), "Document should have a title");
            assertTrue(doc.hasBlob(), "Document should have blob data");
            assertFalse(doc.getBlob().getData().isEmpty(), "Blob should contain data");
            assertNotNull(doc.getBlob().getMimeType(), "Blob should have MIME type");
            assertNotNull(doc.getBlob().getFilename(), "Blob should have filename");
            
            LOG.info("Stream {}: {} - {} ({} bytes)", 
                i, doc.getTitle(), doc.getBlob().getFilename(), doc.getBlob().getData().size());
        }
        
        // Verify we have variety in document types
        long distinctMimeTypes = inputStreams.stream()
            .map(stream -> stream.getDocument().getBlob().getMimeType())
            .distinct()
            .count();
        
        assertTrue(distinctMimeTypes > 5, "Should have multiple different MIME types");
        LOG.info("Found {} distinct MIME types", distinctMimeTypes);
        
        // Test accessing by helper methods
        assertEquals(inputStreams.size(), tikaTestDataHelper.getTikaRequestStreamCount(), "Count method should return correct count");
        
        List<PipeStream> first10 = tikaTestDataHelper.getFirstTikaRequestStreams(10);
        assertEquals(10, first10.size(), "Should return first 10 streams");
        
        // Test map access
        var streamsMap = tikaTestDataHelper.getTikaRequestStreamsMap();
        assertEquals(inputStreams.size(), streamsMap.size(), "Map should contain all streams");
        
        // Verify a specific stream can be retrieved by ID
        String firstStreamId = inputStreams.get(0).getStreamId();
        PipeStream retrievedStream = tikaTestDataHelper.getTikaRequestStreamById(firstStreamId);
        assertNotNull(retrievedStream, "Should be able to retrieve stream by ID");
        assertEquals(firstStreamId, retrievedStream.getStreamId(), "Retrieved stream should match");
        
        LOG.info("=== All {} Tika input streams verified successfully! ===", inputStreams.size());
    }
}