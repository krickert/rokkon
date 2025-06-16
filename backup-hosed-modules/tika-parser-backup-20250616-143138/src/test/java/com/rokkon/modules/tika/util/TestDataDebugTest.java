package com.rokkon.modules.tika.util;

import com.rokkon.search.model.PipeDoc;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Debug test to understand the structure of the test data files.
 */
public class TestDataDebugTest {

    @Test
    public void debugFirstTestDocument() {
        ClassLoader classLoader = getClass().getClassLoader();
        String filename = "test-data/tika-pipe-docs-large/tika_doc_000_doc-000-e15df1cf.bin";
        
        try (InputStream is = classLoader.getResourceAsStream(filename)) {
            assertNotNull(is, "File should exist: " + filename);
            
            PipeDoc doc = PipeDoc.parseFrom(is);
            assertNotNull(doc, "Document should be parsed");
            
            System.out.println("=== Debug Document 000 ===");
            System.out.println("Document ID: " + doc.getId());
            System.out.println("Document Title: " + doc.getTitle());
            System.out.println("Document Body: " + (doc.getBody().isEmpty() ? "(empty)" : doc.getBody().substring(0, Math.min(100, doc.getBody().length()))));
            System.out.println("Has Blob: " + doc.hasBlob());
            
            if (doc.hasBlob()) {
                System.out.println("Blob filename: " + doc.getBlob().getFilename());
                System.out.println("Blob mime type: " + doc.getBlob().getMimeType());
                System.out.println("Blob data size: " + doc.getBlob().getData().size());
                System.out.println("Blob metadata count: " + doc.getBlob().getMetadataCount());
            }
            
            System.out.println("Has Semantic Results: " + (doc.getSemanticResultsCount() > 0));
            System.out.println("Semantic Results Count: " + doc.getSemanticResultsCount());
            
        } catch (Exception e) {
            fail("Failed to load test document: " + e.getMessage());
        }
    }
    
    @Test
    public void debugTestDataLoader() {
        List<PipeDoc> documents = TestDataLoader.loadAllTikaTestDocuments();
        
        System.out.println("=== TestDataLoader Results ===");
        System.out.println("Total documents loaded: " + documents.size());
        
        if (!documents.isEmpty()) {
            PipeDoc firstDoc = documents.get(0);
            System.out.println("First document ID: " + firstDoc.getId());
            System.out.println("First document has blob: " + firstDoc.hasBlob());
            if (firstDoc.hasBlob()) {
                System.out.println("First document blob size: " + firstDoc.getBlob().getData().size());
            }
            System.out.println("First document body: " + (firstDoc.getBody().isEmpty() ? "(empty)" : "Has content (" + firstDoc.getBody().length() + " chars)"));
        }
    }
    
    @Test
    public void debugSampleDocuments() {
        ClassLoader classLoader = getClass().getClassLoader();
        String filename = "test-data/sample-documents-pipe-docs/pipe_doc_000_doc-e67ad3e6-0d2d-34f4-86cd-ce553364693b.bin";
        
        try (InputStream is = classLoader.getResourceAsStream(filename)) {
            assertNotNull(is, "Sample document file should exist: " + filename);
            
            PipeDoc doc = PipeDoc.parseFrom(is);
            assertNotNull(doc, "Sample document should be parsed");
            
            System.out.println("=== Sample Document Analysis ===");
            System.out.println("Document ID: " + doc.getId());
            System.out.println("Document Title: " + doc.getTitle());
            System.out.println("Document Body: " + (doc.getBody().isEmpty() ? "(empty)" : doc.getBody().substring(0, Math.min(100, doc.getBody().length()))));
            System.out.println("Has Blob: " + doc.hasBlob());
            
            if (doc.hasBlob()) {
                System.out.println("Blob filename: " + doc.getBlob().getFilename());
                System.out.println("Blob mime type: " + doc.getBlob().getMimeType());
                System.out.println("Blob data size: " + doc.getBlob().getData().size());
                System.out.println("Blob metadata count: " + doc.getBlob().getMetadataCount());
            }
            
        } catch (Exception e) {
            fail("Failed to load sample document: " + e.getMessage());
        }
    }
}