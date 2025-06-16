package com.rokkon.test.generation;

import com.rokkon.test.config.TestDataGenerationConfig;
import com.rokkon.test.data.TikaTestDataHelper;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for generating and validating test data.
 * 
 * To regenerate test data, run with:
 * ./gradlew test -Drokkon.test.data.regenerate=true
 */
@QuarkusTest
class TestDataGenerationTest {
    private static final Logger LOG = LoggerFactory.getLogger(TestDataGenerationTest.class);
    
    @Inject
    TikaTestDataHelper tikaTestDataHelper;
    
    @Test
    @EnabledIfSystemProperty(named = "rokkon.test.data.regenerate", matches = "true")
    void testGenerateAllTestData() throws Exception {
        LOG.info("=== REGENERATING ALL TEST DATA ===");
        
        // Clear any existing cached data
        tikaTestDataHelper.clearCache();
        
        // Generate all test data
        TestDataGenerator.generateAllTestDataIfEnabled();
        
        // Verify the data was generated correctly
        verifyTikaTestData();
        
        LOG.info("=== TEST DATA GENERATION COMPLETE ===");
    }
    
    @Test
    void testLoadExistingTikaTestData() {
        LOG.info("Testing loading of existing Tika test data");
        
        // Load Tika request streams
        List<PipeStream> requestStreams = tikaTestDataHelper.getTikaRequestStreams();
        LOG.info("Loaded {} Tika request streams", requestStreams.size());
        
        // Load Tika response documents
        List<PipeDoc> responseDocs = tikaTestDataHelper.getTikaResponseDocs();
        LOG.info("Loaded {} Tika response documents", responseDocs.size());
        
        // Verify basic structure
        if (!requestStreams.isEmpty()) {
            PipeStream firstStream = requestStreams.get(0);
            assertNotNull(firstStream.getStreamId());
            assertTrue(firstStream.hasDocument(), "Stream should contain a document");
            LOG.info("First stream: {} with document {}", 
                firstStream.getStreamId(), firstStream.getDocument().getId());
        }
        
        if (!responseDocs.isEmpty()) {
            PipeDoc firstDoc = responseDocs.get(0);
            assertNotNull(firstDoc.getId());
            assertFalse(firstDoc.getBody().isEmpty());
            LOG.info("First response doc: {} with body length {}", 
                firstDoc.getId(), firstDoc.getBody().length());
        }
    }
    
    @Test
    void testTikaTestDataHelperMethods() {
        LOG.info("Testing TikaTestDataHelper methods");
        
        // Test count methods
        int streamCount = tikaTestDataHelper.getTikaRequestStreamCount();
        int docCount = tikaTestDataHelper.getTikaResponseDocCount();
        
        LOG.info("Stream count: {}, Doc count: {}", streamCount, docCount);
        
        // Test getting first N items
        if (streamCount > 0) {
            List<PipeStream> firstStreams = tikaTestDataHelper.getFirstTikaRequestStreams(3);
            assertTrue(firstStreams.size() <= 3);
            assertTrue(firstStreams.size() <= streamCount);
        }
        
        if (docCount > 0) {
            List<PipeDoc> firstDocs = tikaTestDataHelper.getFirstTikaResponseDocs(3);
            assertTrue(firstDocs.size() <= 3);
            assertTrue(firstDocs.size() <= docCount);
        }
        
        // Test map access
        var streamsMap = tikaTestDataHelper.getTikaRequestStreamsMap();
        var docsMap = tikaTestDataHelper.getTikaResponseDocsMap();
        
        assertEquals(streamCount, streamsMap.size());
        assertEquals(docCount, docsMap.size());
    }
    
    private void verifyTikaTestData() {
        LOG.info("Verifying generated Tika test data...");
        
        // Clear cache to force reload of new data
        tikaTestDataHelper.clearCache();
        
        // Verify request streams
        List<PipeStream> requestStreams = tikaTestDataHelper.getTikaRequestStreams();
        assertTrue(requestStreams.size() > 0, "Should have generated Tika request streams");
        
        for (PipeStream stream : requestStreams) {
            assertNotNull(stream.getStreamId(), "Stream should have an ID");
            assertTrue(stream.hasDocument(), "Stream should contain a document");
            
            PipeDoc doc = stream.getDocument();
            assertTrue(doc.hasBlob(), "Request documents should have blob data");
            assertFalse(doc.getBlob().getData().isEmpty(), "Blob should have data");
            assertNotNull(doc.getBlob().getMimeType(), "Blob should have MIME type");
        }
        
        // Verify response documents
        List<PipeDoc> responseDocs = tikaTestDataHelper.getTikaResponseDocs();
        assertTrue(responseDocs.size() > 0, "Should have generated Tika response documents");
        
        for (PipeDoc doc : responseDocs) {
            assertNotNull(doc.getId(), "Document should have an ID");
            assertFalse(doc.getBody().isEmpty(), "Document should have extracted text");
            assertTrue(doc.hasProcessedDate(), "Document should have processed date");
        }
        
        LOG.info("Verification complete: {} request streams, {} response docs", 
            requestStreams.size(), responseDocs.size());
    }
}