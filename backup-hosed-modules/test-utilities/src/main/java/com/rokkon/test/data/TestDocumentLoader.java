package com.rokkon.test.data;

import com.rokkon.search.model.Blob;
import com.rokkon.search.model.PipeDoc;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Utility class for loading test data from protobuf binary files and creating test documents.
 * Combines functionality for loading the 99 test documents and creating documents with blob data.
 */
public class TestDocumentLoader {
    private static final Logger LOG = LoggerFactory.getLogger(TestDocumentLoader.class);
    
    /**
     * Loads all PipeDoc test documents from the tika-pipe-docs-large directory.
     * These are the 99 documents used to verify quality and compatibility.
     * 
     * @return List of PipeDoc objects loaded from binary files
     */
    public static List<PipeDoc> loadAllTikaTestDocuments() {
        List<PipeDoc> documents = new ArrayList<>();
        
        // Known filename patterns from the actual test data
        String[] knownHashes = {
            "e15df1cf", "604f705a", "8f552dc2", "de78fd0c", "99f324f5",
            "a0a63e33", "31f0a42a", "bb188875", "8b13b505", "0fc98b19",
            "7936fc13", "c60e5b5a", "1ff58ae8", "da66ea82", "be158727",
            "db4984fc", "76f06bc7", "0c2b4c87", "b1821265", "5086b36d",
            "a3b7cc0a", "031f1056", "c241349c", "18bffb5f", "bd68dfd7",
            "15cd17e7", "abd99abc", "8650644d", "a9119b2f", "d3fb59f9",
            "35e9843d", "db9f9ec4", "3b0cfc43", "37e53d8b", "686a4d76",
            "20cc54d2", "6fa36f13", "ef7dcb28", "f8c29085", "4f190926",
            "5aca648b", "a8386257", "d89ecf60", "d1eaaa97", "dd02ea9e",
            "872f8f5a", "5ce4be16", "7321d9bb", "7e47daa0", "a4f5640a",
            "b8626f2c", "0c2db9eb", "20313086", "3be4f3bb", "8198d3af",
            "b54ab2d4", "79f942a7", "af68bbe9", "94dfa801", "e7f91de4",
            "9d5b3cba", "d518abb6", "24574444", "f82360c2", "f0fb1715",
            "7550c76c", "5a52fec3", "818653e8", "ff59a243", "1ecb7d4f",
            "9311958b", "a5d419c3", "6ef6bd95", "aa07441b", "1e97a609",
            "bf388c2a", "6f0b8635", "75c66f9e", "792a7785", "9c48ab2d",
            "de6d1878", "0e04c944", "c1aed3e9", "0f65cf07", "a081ee3a",
            "22d65251", "3b428498", "8fa04bf5", "10cf5d93", "dcb3f764",
            "d3e7d2ce", "0793b7f8", "f6d8d283", "a577959c", "3f12b3b6",
            "fc958c79", "125a07d5", "8171351b", "4f5ff2d7", "d622eaca"
        };
        
        ClassLoader classLoader = TestDocumentLoader.class.getClassLoader();
        String basePath = "test-data/tika-pipe-docs-large";
        
        // Load files using the known hash patterns
        for (int i = 0; i < knownHashes.length; i++) {
            String filename = String.format("tika_doc_%03d_doc-%03d-%s.bin", i, i, knownHashes[i]);
            String fullPath = basePath + "/" + filename;
            
            try (InputStream is = classLoader.getResourceAsStream(fullPath)) {
                if (is != null) {
                    PipeDoc doc = PipeDoc.parseFrom(is);
                    documents.add(doc);
                    LOG.debug("Loaded document {}: {} (size: {})", i, doc.getId(), 
                        doc.hasBlob() ? doc.getBlob().getData().size() : 0);
                } else {
                    LOG.warn("Could not find file: {}", fullPath);
                }
            } catch (IOException e) {
                LOG.error("Error loading document from {}: {}", filename, e.getMessage());
            }
        }
        
        LOG.info("Loaded {} test documents from {}", documents.size(), basePath);
        return documents;
    }
    
    /**
     * Simple method to count available test documents without loading them all.
     */
    public static int countAvailableTestDocuments() {
        return loadAllTikaTestDocuments().size();
    }
    
    /**
     * Creates test documents with actual blob data using CSV metadata.
     * This uses the document-metadata.csv file for comprehensive testing.
     * 
     * @return List of PipeDoc objects with blob data for processing
     */
    public static List<PipeDoc> createTestDocumentsWithBlobData() {
        return createTestDocumentsFromMetadata();
    }
    
    /**
     * Creates test documents from CSV metadata (CSV-driven approach).
     * Uses document-metadata.csv to load documents with full metadata.
     * 
     * @return List of PipeDoc objects with blob data for processing
     */
    public static List<PipeDoc> createTestDocumentsFromMetadata() {
        List<PipeDoc> documents = new ArrayList<>();
        List<DocumentMetadata> metadataList = DocumentMetadataLoader.load99DocumentMetadata();
        
        ClassLoader classLoader = TestDocumentLoader.class.getClassLoader();
        
        for (int i = 0; i < metadataList.size(); i++) {
            DocumentMetadata metadata = metadataList.get(i);
            String resourcePath = "test-data/source-documents/" + metadata.getFilename();
            
            try (InputStream is = classLoader.getResourceAsStream(resourcePath)) {
                if (is != null) {
                    // Read file data
                    byte[] fileData = is.readAllBytes();
                    
                    // Create blob with file data
                    Blob blob = Blob.newBuilder()
                            .setFilename(metadata.getFilename())
                            .setMimeType(metadata.getContentType())
                            .setData(ByteString.copyFrom(fileData))
                            .build();
                    
                    // Create document with full metadata
                    PipeDoc document = PipeDoc.newBuilder()
                            .setId("metadata-doc-" + String.format("%03d", i))
                            .setTitle(metadata.getTitle())
                            .setBlob(blob)
                            .setSourceMimeType(metadata.getContentType())
                            .addAllKeywords(metadata.getKeywordsList())
                            .build();
                    
                    documents.add(document);
                    LOG.debug("Created test document from metadata {}: {} ({} bytes, {})", 
                        i, metadata.getTitle(), fileData.length, metadata.getContentType());
                        
                } else {
                    LOG.warn("Could not find file for metadata: {}", resourcePath);
                }
            } catch (IOException e) {
                LOG.error("Error reading file for metadata {}: {}", metadata.getFilename(), e.getMessage());
            }
        }
        
        LOG.info("Created {} test documents from CSV metadata", documents.size());
        return documents;
    }
    
    /**
     * Creates test documents from a directory (directory-driven approach).
     * Scans the specified directory and loads all documents found.
     * 
     * @param directoryPath Path to directory containing documents (e.g., "test-data/source-documents")
     * @return List of PipeDoc objects with blob data for processing
     */
    public static List<PipeDoc> createTestDocumentsFromDirectory(String directoryPath) {
        List<PipeDoc> documents = new ArrayList<>();
        ClassLoader classLoader = TestDocumentLoader.class.getClassLoader();
        
        // Try to read directory from classpath
        try (InputStream dirStream = classLoader.getResourceAsStream(directoryPath)) {
            if (dirStream == null) {
                LOG.warn("Directory not found in classpath: {}", directoryPath);
                return documents;
            }
        } catch (IOException e) {
            LOG.warn("Error accessing directory: {}", directoryPath, e);
            return documents;
        }
        
        // For directory scanning in JAR, we need to try known file patterns
        // This is a limitation of JAR-based resource loading
        String[] commonExtensions = {".pdf", ".doc", ".docx", ".odt", ".rtf", ".ppt", ".pptx", ".odp", 
                                    ".xls", ".xlsx", ".ods", ".csv", ".txt", ".html", ".xml", ".json",
                                    ".java", ".kt", ".gradle", ".kts", ".py", ".js", ".ts"};
        
        // Try to find files with common patterns
        int docIndex = 0;
        for (String extension : commonExtensions) {
            // Try various naming patterns
            for (int i = 0; i < 20; i++) { // Reasonable limit
                String[] patterns = {
                    directoryPath + "/sample" + i + extension,
                    directoryPath + "/test" + i + extension,
                    directoryPath + "/file" + i + extension,
                    directoryPath + "/document" + i + extension
                };
                
                for (String pattern : patterns) {
                    try (InputStream is = classLoader.getResourceAsStream(pattern)) {
                        if (is != null) {
                            documents.add(createDocumentFromStream(is, pattern, docIndex++));
                        }
                    } catch (IOException e) {
                        // Ignore missing files
                    }
                }
            }
        }
        
        LOG.info("Created {} test documents from directory: {}", documents.size(), directoryPath);
        return documents;
    }
    
    /**
     * Creates test documents from specific file paths.
     * Useful for individual tests that need specific documents.
     * 
     * @param filePaths Array of file paths to load
     * @return List of PipeDoc objects with blob data for processing
     */
    public static List<PipeDoc> createTestDocumentsFromFiles(String... filePaths) {
        List<PipeDoc> documents = new ArrayList<>();
        ClassLoader classLoader = TestDocumentLoader.class.getClassLoader();
        
        for (int i = 0; i < filePaths.length; i++) {
            String filePath = filePaths[i];
            try (InputStream is = classLoader.getResourceAsStream(filePath)) {
                if (is != null) {
                    documents.add(createDocumentFromStream(is, filePath, i));
                } else {
                    LOG.warn("Could not find specific file: {}", filePath);
                }
            } catch (IOException e) {
                LOG.error("Error reading specific file {}: {}", filePath, e.getMessage());
            }
        }
        
        LOG.info("Created {} test documents from specific files", documents.size());
        return documents;
    }
    
    /**
     * Helper method to create a PipeDoc from an InputStream.
     */
    private static PipeDoc createDocumentFromStream(InputStream is, String filePath, int index) throws IOException {
        byte[] fileData = is.readAllBytes();
        String filename = filePath.substring(filePath.lastIndexOf('/') + 1);
        String mimeType = getMimeTypeFromFilename(filePath);
        String title = createTitleFromFilename(filename);
        
        Blob blob = Blob.newBuilder()
                .setFilename(filename)
                .setMimeType(mimeType)
                .setData(ByteString.copyFrom(fileData))
                .build();
        
        PipeDoc document = PipeDoc.newBuilder()
                .setId("file-doc-" + String.format("%03d", index))
                .setTitle(title)
                .setBlob(blob)
                .setSourceMimeType(mimeType)
                .build();
        
        LOG.debug("Created document from stream: {} ({} bytes, {})", title, fileData.length, mimeType);
        return document;
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
            case "odt" -> "application/vnd.oasis.opendocument.text";
            case "rtf" -> "application/rtf";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "odp" -> "application/vnd.oasis.opendocument.presentation";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ods" -> "application/vnd.oasis.opendocument.spreadsheet";
            case "csv" -> "text/csv";
            case "txt" -> "text/plain";
            case "html", "htm" -> "text/html";
            case "xml" -> "application/xml";
            case "json" -> "application/json";
            case "java" -> "text/x-java-source";
            case "gradle" -> "text/x-gradle";
            case "kts", "kt" -> "text/x-kotlin";
            case "py" -> "text/x-python";
            case "js" -> "application/javascript";
            case "ts" -> "application/typescript";
            default -> "application/octet-stream";
        };
    }
    
    /**
     * Creates a human-readable title from filename.
     */
    private static String createTitleFromFilename(String filename) {
        // Remove extension
        String name = filename;
        if (name.contains(".")) {
            name = name.substring(0, name.lastIndexOf("."));
        }
        
        // Convert underscores and hyphens to spaces
        name = name.replace("_", " ").replace("-", " ");
        
        // Capitalize first letter of each word
        String[] words = name.split("\\s+");
        StringBuilder title = new StringBuilder();
        for (String word : words) {
            if (title.length() > 0) title.append(" ");
            if (word.length() > 0) {
                title.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    title.append(word.substring(1));
                }
            }
        }
        
        return title.toString();
    }
}