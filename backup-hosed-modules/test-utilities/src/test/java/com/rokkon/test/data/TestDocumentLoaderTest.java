package com.rokkon.test.data;

import com.google.protobuf.ByteString;
import com.rokkon.search.model.Blob;
import com.rokkon.search.model.PipeDoc;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test the CSV-driven and directory-driven document loading approaches.
 */
@QuarkusTest
class TestDocumentLoaderTest {
    private static final Logger LOG = LoggerFactory.getLogger(TestDocumentLoaderTest.class);
    
    @Test
    void testLoadDocumentsFromMetadata() {
        LOG.info("=== Testing CSV-driven document loading (base documents only) ===");
        
        // Use base metadata without variations for testing
        List<DocumentMetadata> baseMetadata = DocumentMetadataLoader.loadAllBaseMetadata();
        List<PipeDoc> documents = new ArrayList<>();
        
        for (int i = 0; i < baseMetadata.size(); i++) {
            DocumentMetadata metadata = baseMetadata.get(i);
            String resourcePath = "test-data/source-documents/" + metadata.getFilename();
            
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (is != null) {
                    byte[] fileData = is.readAllBytes();
                    
                    Blob blob = Blob.newBuilder()
                            .setFilename(metadata.getFilename())
                            .setMimeType(metadata.getContentType())
                            .setData(ByteString.copyFrom(fileData))
                            .build();
                    
                    PipeDoc document = PipeDoc.newBuilder()
                            .setId("test-doc-" + String.format("%03d", i))
                            .setTitle(metadata.getTitle())
                            .setBlob(blob)
                            .setSourceMimeType(metadata.getContentType())
                            .addAllKeywords(metadata.getKeywordsList())
                            .build();
                    
                    documents.add(document);
                } else {
                    LOG.warn("Could not find file: {}", resourcePath);
                }
            } catch (IOException e) {
                LOG.error("Error reading file {}: {}", resourcePath, e.getMessage());
            }
        }
        
        assertFalse(documents.isEmpty(), "Should load documents from CSV metadata");
        LOG.info("Loaded {} documents from CSV metadata", documents.size());
        
        // Verify documents have proper metadata
        for (int i = 0; i < Math.min(5, documents.size()); i++) {
            PipeDoc doc = documents.get(i);
            assertNotNull(doc.getId(), "Document should have ID");
            assertNotNull(doc.getTitle(), "Document should have title");
            assertTrue(doc.hasBlob(), "Document should have blob data");
            assertTrue(doc.getBlob().getData().size() > 1000, "Document should be substantial size");
            assertNotNull(doc.getSourceMimeType(), "Document should have MIME type");
            
            LOG.info("Document {}: {} - {} bytes ({})", 
                i, doc.getTitle(), doc.getBlob().getData().size(), doc.getSourceMimeType());
        }
    }
    
    @Test
    void testLoadSpecificDocumentTypes() {
        LOG.info("=== Testing specific document type loading ===");
        
        // Test CSV documents
        List<DocumentMetadata> csvDocs = DocumentMetadataLoader.getCsvTestDocuments();
        assertFalse(csvDocs.isEmpty(), "Should find CSV documents");
        LOG.info("Found {} CSV documents", csvDocs.size());
        
        // Test PDF documents
        List<DocumentMetadata> pdfDocs = DocumentMetadataLoader.getPdfTestDocuments();
        assertFalse(pdfDocs.isEmpty(), "Should find PDF documents");
        LOG.info("Found {} PDF documents", pdfDocs.size());
        
        // Test large documents
        List<DocumentMetadata> largeDocs = DocumentMetadataLoader.getLargeDocuments();
        assertFalse(largeDocs.isEmpty(), "Should find large documents");
        LOG.info("Found {} large documents", largeDocs.size());
        
        // Test source code documents
        List<DocumentMetadata> sourceDocs = DocumentMetadataLoader.getSourceCodeDocuments();
        assertFalse(sourceDocs.isEmpty(), "Should find source code documents");
        LOG.info("Found {} source code documents", sourceDocs.size());
        
        // Verify proper categorization
        for (DocumentMetadata doc : sourceDocs) {
            assertEquals("source-code", doc.getCategory(), "Source code docs should have correct category");
            assertTrue(doc.getFilename().startsWith("source-code/"), "Source code filename should have path");
        }
    }
    
    @Test
    void testLoadSpecificFiles() {
        LOG.info("=== Testing specific file loading ===");
        
        // Test loading specific large files
        List<PipeDoc> specificDocs = TestDocumentLoader.createTestDocumentsFromFiles(
            "test-data/source-documents/acm_3627673.3679549.pdf",
            "test-data/source-documents/sample_1mb.txt",
            "test-data/source-documents/source-code/DocumentPipelineIntegrationTests.java"
        );
        
        assertEquals(3, specificDocs.size(), "Should load exactly 3 specific documents");
        
        // Verify each document loaded correctly
        PipeDoc pdfDoc = specificDocs.get(0);
        assertTrue(pdfDoc.getTitle().contains("Acm"), "PDF should have correct title");
        assertEquals("application/pdf", pdfDoc.getSourceMimeType(), "PDF should have correct MIME type");
        assertTrue(pdfDoc.getBlob().getData().size() > 100000, "PDF should be large");
        
        PipeDoc txtDoc = specificDocs.get(1);
        assertTrue(txtDoc.getTitle().contains("Sample"), "Text should have correct title");
        assertEquals("text/plain", txtDoc.getSourceMimeType(), "Text should have correct MIME type");
        assertTrue(txtDoc.getBlob().getData().size() > 500000, "Text should be very large");
        
        PipeDoc javaDoc = specificDocs.get(2);
        assertTrue(javaDoc.getTitle().contains("DocumentPipelineIntegrationTests"), "Java should have correct title");
        assertEquals("text/x-java-source", javaDoc.getSourceMimeType(), "Java should have correct MIME type");
        
        LOG.info("Loaded specific documents: PDF({} bytes), TXT({} bytes), Java({} bytes)",
            pdfDoc.getBlob().getData().size(),
            txtDoc.getBlob().getData().size(), 
            javaDoc.getBlob().getData().size());
    }
    
    @Test
    void testDocumentMetadataFiltering() {
        LOG.info("=== Testing document metadata filtering ===");
        
        // Test content type filtering
        List<DocumentMetadata> jsonDocs = DocumentMetadataLoader.loadDocumentsByContentType("application/json");
        assertFalse(jsonDocs.isEmpty(), "Should find JSON documents");
        for (DocumentMetadata doc : jsonDocs) {
            assertEquals("application/json", doc.getContentType(), "All docs should be JSON");
        }
        
        // Test keyword filtering  
        List<DocumentMetadata> largeDocs = DocumentMetadataLoader.loadDocumentsByKeywords("large");
        assertFalse(largeDocs.isEmpty(), "Should find documents with 'large' keyword");
        for (DocumentMetadata doc : largeDocs) {
            assertTrue(doc.getKeywords().contains("large"), "All docs should contain 'large' keyword");
        }
        
        // Test category filtering
        List<DocumentMetadata> docCategory = DocumentMetadataLoader.loadDocumentsByCategory("documents");
        List<DocumentMetadata> codeCategory = DocumentMetadataLoader.loadDocumentsByCategory("source-code");
        
        assertFalse(docCategory.isEmpty(), "Should find documents category");
        assertFalse(codeCategory.isEmpty(), "Should find source-code category");
        
        LOG.info("Filtering results: {} JSON, {} large, {} documents, {} source-code",
            jsonDocs.size(), largeDocs.size(), docCategory.size(), codeCategory.size());
    }
    
    @Test
    void testDocumentSizeVariety() {
        LOG.info("=== Testing document size variety ===");
        
        // Use base metadata only for this test too
        List<DocumentMetadata> baseMetadata = DocumentMetadataLoader.loadAllBaseMetadata();
        
        long totalSize = 0;
        long minSize = Long.MAX_VALUE;
        long maxSize = 0;
        int loadedCount = 0;
        
        for (DocumentMetadata metadata : baseMetadata) {
            String resourcePath = "test-data/source-documents/" + metadata.getFilename();
            
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (is != null) {
                    byte[] fileData = is.readAllBytes();
                    long size = fileData.length;
                    totalSize += size;
                    minSize = Math.min(minSize, size);
                    maxSize = Math.max(maxSize, size);
                    loadedCount++;
                }
            } catch (IOException e) {
                LOG.warn("Failed to read file: {}", resourcePath);
            }
        }
        
        assertTrue(loadedCount > 0, "Should have loaded some documents");
        double avgSize = (double) totalSize / loadedCount;
        
        LOG.info("Document size statistics:");
        LOG.info("  Total documents: {}", loadedCount);
        LOG.info("  Total size: {} bytes ({} MB)", totalSize, totalSize / 1024 / 1024);
        LOG.info("  Average size: {:.1f} bytes ({:.1f} KB)", avgSize, avgSize / 1024);
        LOG.info("  Min size: {} bytes ({} KB)", minSize, minSize / 1024);
        LOG.info("  Max size: {} bytes ({} MB)", maxSize, maxSize / 1024 / 1024);
        
        // Verify we have substantial documents
        assertTrue(maxSize > 100000, "Should have documents larger than 100KB");
        assertTrue(avgSize > 10000, "Average document size should be substantial");
        assertTrue(totalSize > 1000000, "Total test data should be substantial (>1MB)");
        
        LOG.info("=== Document size variety test completed ===");
    }
}