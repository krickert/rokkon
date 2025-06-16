package com.rokkon.test.data;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.Blob;
import com.google.protobuf.ByteString;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating standardized test documents at different processing stages.
 * 
 * Document Pipeline Stages:
 * 1. RAW: Documents with blob data (need Tika processing)
 * 2. TIKA_PROCESSED: Documents with extracted text (ready for chunking) - The test documents
 * 3. CHUNKED: Documents with semantic chunks (ready for embedding)
 * 4. EMBEDDED: Documents with vector embeddings
 */
@ApplicationScoped
public class TestDocumentFactory {
    private static final Logger LOG = LoggerFactory.getLogger(TestDocumentFactory.class);
    
    
    /**
     * Loads the standard set of Tika-processed documents.
     * These documents have extracted text content and are ready for chunking.
     * 
     * @return List of PipeDoc objects with text content extracted by Tika
     */
    public List<PipeDoc> loadTikaProcessedDocuments() {
        return TestDocumentLoader.loadAllTikaTestDocuments();
    }
    
    /**
     * Gets the first N Tika-processed documents for smaller test runs.
     * 
     * @param count Number of documents to return
     * @return List of PipeDoc objects
     */
    public List<PipeDoc> getFirstTikaProcessedDocuments(int count) {
        List<PipeDoc> allDocs = loadTikaProcessedDocuments();
        return allDocs.subList(0, Math.min(count, allDocs.size()));
    }
    
    /**
     * Creates raw documents with blob data that need Tika processing.
     * These are useful for testing the full Tika parsing pipeline.
     * 
     * @return List of PipeDoc objects with blob data
     */
    public List<PipeDoc> createRawDocumentsWithBlobData() {
        return TestDocumentLoader.createTestDocumentsWithBlobData();
    }
    
    /**
     * Creates a simple test document with text content for quick testing.
     * 
     * @param id Document ID
     * @param title Document title
     * @param body Document body text
     * @return PipeDoc with the specified content
     */
    public PipeDoc createSimpleTextDocument(String id, String title, String body) {
        return PipeDoc.newBuilder()
                .setId(id)
                .setTitle(title)
                .setBody(body)
                .build();
    }
    
    /**
     * Creates a document with specific blob data for testing.
     * 
     * @param id Document ID
     * @param data Blob data
     * @param mimeType MIME type
     * @param filename Filename
     * @return PipeDoc with blob data
     */
    public PipeDoc createDocumentWithBlob(String id, byte[] data, String mimeType, String filename) {
        Blob blob = Blob.newBuilder()
                .setFilename(filename)
                .setMimeType(mimeType)
                .setData(ByteString.copyFrom(data))
                .build();
        
        return PipeDoc.newBuilder()
                .setId(id)
                .setTitle("Test Document: " + filename)
                .setBlob(blob)
                .build();
    }
    
    /**
     * Creates multiple simple test documents for batch testing.
     * 
     * @param count Number of documents to create
     * @return List of simple test documents
     */
    public List<PipeDoc> createSimpleTestDocuments(int count) {
        List<PipeDoc> documents = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            PipeDoc doc = createSimpleTextDocument(
                "simple-test-" + i,
                "Test Document " + i,
                "This is the body content for test document number " + i + ". " +
                "It contains enough text to be useful for chunking and embedding tests. " +
                "The content includes multiple sentences to test proper text processing."
            );
            documents.add(doc);
        }
        
        return documents;
    }
    
    
    private String getMimeTypeFromFilename(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            case "txt" -> "text/plain";
            case "html", "htm" -> "text/html";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "csv" -> "text/csv";
            case "rtf" -> "application/rtf";
            case "xml" -> "application/xml";
            case "json" -> "application/json";
            default -> "application/octet-stream";
        };
    }
}