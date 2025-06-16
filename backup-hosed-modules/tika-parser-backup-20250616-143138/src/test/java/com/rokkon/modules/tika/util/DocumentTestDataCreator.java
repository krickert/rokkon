package com.rokkon.modules.tika.util;

import com.rokkon.search.model.Blob;
import com.rokkon.search.model.PipeDoc;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates test documents with actual blob data for Tika processing.
 * Uses real document files to create proper input documents.
 */
public class DocumentTestDataCreator {
    private static final Logger LOG = LoggerFactory.getLogger(DocumentTestDataCreator.class);
    
    /**
     * Creates test documents with actual blob data from real document files.
     * These documents can be processed by TikaService to extract text content.
     * 
     * @return List of PipeDoc objects with blob data for processing
     */
    public static List<PipeDoc> createTestDocumentsWithBlobData() {
        List<PipeDoc> documents = new ArrayList<>();
        
        // Document files to use for testing
        String[] testFiles = {
            "test-data/412KB.pdf",
            "test-data/file-sample_100kB.docx", 
            "test-data/file-sample_500kB.doc",
            "test-data/TXT.txt",
            "test-data/CSV.csv",
            "test-data/HTML_3KB.html",
            "test-data/Rich Text Format.rtf",
            "test-data/sample.html",
            "test-data/sample.json",
            "test-data/sample.xlsx",
            "test-data/sample.xml"
        };
        
        ClassLoader classLoader = DocumentTestDataCreator.class.getClassLoader();
        
        for (int i = 0; i < testFiles.length; i++) {
            String filename = testFiles[i];
            try (InputStream is = classLoader.getResourceAsStream(filename)) {
                if (is != null) {
                    // Read file data
                    byte[] fileData = is.readAllBytes();
                    
                    // Determine MIME type based on file extension
                    String mimeType = getMimeTypeFromFilename(filename);
                    
                    // Create blob with file data
                    Blob blob = Blob.newBuilder()
                            .setFilename(filename.substring(filename.lastIndexOf('/') + 1))
                            .setMimeType(mimeType)
                            .setData(ByteString.copyFrom(fileData))
                            .build();
                    
                    // Create document with blob data
                    PipeDoc document = PipeDoc.newBuilder()
                            .setId("test-doc-" + String.format("%03d", i))
                            .setTitle("Test Document " + i + " - " + blob.getFilename())
                            .setBlob(blob)
                            .build();
                    
                    documents.add(document);
                    LOG.debug("Created test document {}: {} ({} bytes)", 
                        i, filename, fileData.length);
                        
                } else {
                    LOG.warn("Could not find test file: {}", filename);
                }
            } catch (IOException e) {
                LOG.error("Error reading test file {}: {}", filename, e.getMessage());
            }
        }
        
        LOG.info("Created {} test documents with blob data", documents.size());
        return documents;
    }
    
    /**
     * Determines MIME type based on file extension.
     */
    private static String getMimeTypeFromFilename(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            case "txt" -> "text/plain";
            case "csv" -> "text/csv";
            case "html", "htm" -> "text/html";
            case "rtf" -> "application/rtf";
            case "json" -> "application/json";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xml" -> "application/xml";
            default -> "application/octet-stream";
        };
    }
}