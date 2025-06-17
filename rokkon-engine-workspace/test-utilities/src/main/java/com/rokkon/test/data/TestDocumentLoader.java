package com.rokkon.test.data;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;

import com.google.protobuf.ByteString;
import com.rokkon.search.model.Blob;
import com.rokkon.search.model.PipeDoc;
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

import io.quarkus.runtime.util.ClassPathUtils;

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
                "fc958c79", "125a07d5", "8171351b", "4f5ff2d", "d622eaca"
        };

        String basePath = "test-data/tika-pipe-docs-large";

        // Load files using the known hash patterns with Quarkus resource loader
        for (int i = 0; i < knownHashes.length; i++) {
            final int docIndex = i;
            String filename = String.format("tika_doc_%03d_doc-%03d-%s.bin", i, i, knownHashes[i]);
            String fullPath = basePath + "/" + filename;

            try {
                ClassPathUtils.consumeAsStreams(fullPath, is -> {
                    try {
                        PipeDoc doc = PipeDoc.parseFrom(is);
                        documents.add(doc);
                        LOG.debug("Loaded document {}: {} (size: {})", docIndex, doc.getId(),
                                doc.hasBlob() ? doc.getBlob().getData().size() : 0);
                    } catch (IOException e) {
                        LOG.error("Error parsing document from {}: {}", filename, e.getMessage());
                    }
                });
            } catch (IOException e) {
                LOG.debug("Could not find file: {}", fullPath);
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

        for (int i = 0; i < metadataList.size(); i++) {
            final int docIndex = i;
            DocumentMetadata metadata = metadataList.get(i);
            String resourcePath = "test-data/source-documents/" + metadata.getFilename();

            try {
                ClassPathUtils.consumeAsStreams(resourcePath, is -> {
                    try {
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
                                .setId("metadata-doc-" + String.format("%03d", docIndex))
                                .setTitle(metadata.getTitle())
                                .setBlob(blob)
                                .setSourceMimeType(metadata.getContentType())
                                .addAllKeywords(metadata.getKeywordsList())
                                .build();

                        documents.add(document);
                        LOG.debug("Created test document from metadata {}: {} ({} bytes, {})", 
                            docIndex, metadata.getTitle(), fileData.length, metadata.getContentType());
                    } catch (IOException e) {
                        LOG.error("Error parsing file for metadata {}: {}", metadata.getFilename(), e.getMessage());
                    }
                });
            } catch (IOException e) {
                LOG.debug("Could not find file for metadata: {}", resourcePath);
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
        
        // Use Quarkus ClassPathUtils to scan directory properly
        try {
            ClassPathUtils.consumeAsPaths(directoryPath, path -> {
                if (Files.isDirectory(path)) {
                    // If it's a directory, walk through all files
                    try (Stream<Path> paths = Files.walk(path)) {
                        paths.filter(Files::isRegularFile)
                            .forEach(file -> {
                                try {
                                    byte[] fileData = Files.readAllBytes(file);
                                    String fileName = file.getFileName().toString();
                                    int docIndex = documents.size();
                                    
                                    // Create document from file data
                                    PipeDoc doc = createDocumentFromStream(
                                        new ByteArrayInputStream(fileData), 
                                        fileName, 
                                        docIndex
                                    );
                                    documents.add(doc);
                                    LOG.debug("Loaded document from directory: {}", fileName);
                                } catch (IOException e) {
                                    LOG.error("Error loading file {}: {}", file, e.getMessage());
                                }
                            });
                    } catch (IOException e) {
                        LOG.error("Error walking directory {}: {}", path, e.getMessage());
                    }
                } else if (Files.isRegularFile(path)) {
                    // If it's a single file, just load it
                    try {
                        byte[] fileData = Files.readAllBytes(path);
                        String fileName = path.getFileName().toString();
                        PipeDoc doc = createDocumentFromStream(
                            new ByteArrayInputStream(fileData), 
                            fileName, 
                            documents.size()
                        );
                        documents.add(doc);
                        LOG.debug("Loaded single file: {}", fileName);
                    } catch (IOException e) {
                        LOG.error("Error loading file {}: {}", path, e.getMessage());
                    }
                }
            });
        } catch (IOException e) {
            LOG.warn("Directory not found or not accessible: {}", directoryPath);
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

        for (int i = 0; i < filePaths.length; i++) {
            final int docIndex = i;
            String filePath = filePaths[i];
            
            try {
                ClassPathUtils.consumeAsStreams(filePath, is -> {
                    try {
                        documents.add(createDocumentFromStream(is, filePath, docIndex));
                        LOG.debug("Loaded specific file: {}", filePath);
                    } catch (IOException e) {
                        LOG.error("Error parsing specific file {}: {}", filePath, e.getMessage());
                    }
                });
            } catch (IOException e) {
                LOG.debug("Could not find specific file: {}", filePath);
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
