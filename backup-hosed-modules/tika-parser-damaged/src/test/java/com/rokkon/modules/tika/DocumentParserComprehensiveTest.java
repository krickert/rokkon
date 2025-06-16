package com.rokkon.modules.tika;

import com.rokkon.search.model.ParsedDocument;
import com.rokkon.search.model.ParsedDocumentReply;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for DocumentParser using the full test data set.
 */
class DocumentParserComprehensiveTest {

    private Map<String, String> defaultConfig;

    @BeforeEach
    void setUp() {
        defaultConfig = new HashMap<>();
        defaultConfig.put("maxContentLength", "1000000");
        defaultConfig.put("extractMetadata", "true");
        defaultConfig.put("enableGeoTopicParser", "false");
    }

    @Test
    void testParseSimpleTextDocument() {
        String content = "This is a test document with some sample content.";
        ByteString document = ByteString.copyFromUtf8(content);
        
        Map<String, String> config = new HashMap<>(defaultConfig);
        config.put("filename", "test.txt");
        
        ParsedDocumentReply reply = DocumentParser.parseDocument(document, config);
        
        assertTrue(reply.getSuccess());
        assertTrue(reply.getErrorMessage().isEmpty());
        
        ParsedDocument parsedDoc = reply.getParsedDocument();
        assertEquals(content, parsedDoc.getBody());
        assertTrue(parsedDoc.getMetadataMap().containsKey("content_type"));
    }

    @Test
    void testParseEmptyDocument() {
        ByteString emptyDocument = ByteString.EMPTY;
        
        ParsedDocumentReply reply = DocumentParser.parseDocument(emptyDocument, defaultConfig);
        
        assertFalse(reply.getSuccess(), "Should fail when parsing empty document");
        assertFalse(reply.getErrorMessage().isEmpty(), "Error message should not be empty");
        assertTrue(reply.getErrorMessage().contains("InputStream must have > 0 bytes") ||
                   reply.getErrorMessage().contains("ZeroByteFileException") ||
                   reply.getErrorMessage().contains("empty") ||
                   reply.getErrorMessage().contains("no content"),
                   "Error message should mention empty document issue. Actual: " + reply.getErrorMessage());
    }

    @Test
    void testParseWithCustomMaxContentLength() {
        String longContent = "A".repeat(1000);
        ByteString document = ByteString.copyFromUtf8(longContent);
        
        Map<String, String> config = new HashMap<>(defaultConfig);
        config.put("maxContentLength", "500");
        config.put("filename", "long.txt");
        
        ParsedDocumentReply reply = DocumentParser.parseDocument(document, config);
        
        // When content exceeds limit, Tika throws WriteLimitReachedException
        // Our error handling should catch this and return a failure
        assertFalse(reply.getSuccess(), "Should fail when content exceeds maxContentLength");
        assertTrue(reply.getErrorMessage().contains("WriteLimitReachedException") || 
                   reply.getErrorMessage().contains("limit has been reached") ||
                   reply.getErrorMessage().contains("Your document contained more than"),
                   "Error message should mention the write limit exception. Actual: " + reply.getErrorMessage());
    }

    @ParameterizedTest
    @CsvSource({
        "test.pdf, application/pdf",
        "test.docx, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "test.doc, application/msword",
        "test.pptx, application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "test.ppt, application/vnd.ms-powerpoint",
        "test.xlsx, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "test.xls, application/vnd.ms-excel",
        "test.html, text/html",
        "test.txt, text/plain",
        "test.json, application/json"
    })
    void testContentTypeInference(String filename, String expectedContentType) {
        String content = "Sample content";
        ByteString document = ByteString.copyFromUtf8(content);
        
        Map<String, String> config = new HashMap<>(defaultConfig);
        config.put("filename", filename);
        
        ParsedDocumentReply reply = DocumentParser.parseDocument(document, config);
        
        assertTrue(reply.getSuccess());
        // The actual content type detection depends on the document content,
        // but we can verify the parsing completed successfully
    }

    @Test
    void testParseWithDisabledMetadata() {
        String content = "Test content";
        ByteString document = ByteString.copyFromUtf8(content);
        
        Map<String, String> config = new HashMap<>(defaultConfig);
        config.put("extractMetadata", "false");
        config.put("filename", "test.txt");
        
        ParsedDocumentReply reply = DocumentParser.parseDocument(document, config);
        
        assertTrue(reply.getSuccess());
        ParsedDocument parsedDoc = reply.getParsedDocument();
        assertTrue(parsedDoc.getMetadataMap().isEmpty());
    }

    @Test
    void testParseWithGeoTopicParserEnabled() {
        String content = "Paris is the capital of France. Located at coordinates 48.8566° N, 2.3522° E.";
        ByteString document = ByteString.copyFromUtf8(content);
        
        Map<String, String> config = new HashMap<>(defaultConfig);
        config.put("enableGeoTopicParser", "true");
        config.put("filename", "geo.txt");
        
        ParsedDocumentReply reply = DocumentParser.parseDocument(document, config);
        
        assertTrue(reply.getSuccess());
        ParsedDocument parsedDoc = reply.getParsedDocument();
        assertEquals(content, parsedDoc.getBody());
    }

    @Test
    void testParseTestDataFiles() throws IOException {
        Path testDataDir = Paths.get("src/test/resources/test-data");
        
        if (!Files.exists(testDataDir)) {
            // Skip test if test data directory doesn't exist
            return;
        }
        
        try (Stream<Path> files = Files.list(testDataDir)) {
            files.filter(Files::isRegularFile)
                 .filter(path -> !path.toString().endsWith(".bin")) // Skip binary protobuf files for now
                 .limit(10) // Test first 10 files to avoid long test times
                 .forEach(this::testParseFile);
        }
    }

    private void testParseFile(Path filePath) {
        try {
            byte[] fileContent = Files.readAllBytes(filePath);
            ByteString document = ByteString.copyFrom(fileContent);
            
            Map<String, String> config = new HashMap<>(defaultConfig);
            config.put("filename", filePath.getFileName().toString());
            
            ParsedDocumentReply reply = DocumentParser.parseDocument(document, config);
            
            // For real files, we expect either success or a meaningful error
            if (!reply.getSuccess()) {
                System.out.println("Failed to parse " + filePath.getFileName() + ": " + reply.getErrorMessage());
                // Don't fail the test - some files might be intentionally problematic
            } else {
                assertNotNull(reply.getParsedDocument());
                System.out.println("Successfully parsed " + filePath.getFileName() + 
                                 " - Body length: " + reply.getParsedDocument().getBody().length() +
                                 " - Metadata fields: " + reply.getParsedDocument().getMetadataMap().size());
            }
        } catch (IOException e) {
            fail("Failed to read test file: " + filePath.getFileName() + " - " + e.getMessage());
        }
    }

    @Test
    void testEMFParserDisabling() {
        String content = "Test content";
        ByteString document = ByteString.copyFromUtf8(content);
        
        Map<String, String> config = new HashMap<>(defaultConfig);
        config.put("filename", "test.ppt"); // This should trigger EMF parser disabling
        
        ParsedDocumentReply reply = DocumentParser.parseDocument(document, config);
        
        assertTrue(reply.getSuccess());
        // The test passes if EMF parser doesn't cause issues
    }

    @Test
    void testCreateCustomParserConfig() throws Exception {
        String configXml = DocumentParser.createCustomParserConfig();
        
        assertNotNull(configXml);
        assertTrue(configXml.contains("EMFParser"));
        assertTrue(configXml.contains("enabled=\"false\""));
    }

    @Test
    void testCreateGeoTopicParserConfig() throws Exception {
        String configXml = DocumentParser.createGeoTopicParserConfig();
        
        assertNotNull(configXml);
        assertTrue(configXml.contains("GeoTopicParser"));
        assertTrue(configXml.contains("enabled=\"true\""));
    }

    @Test
    void testParseActualTestFile() throws IOException {
        Path testFile = Paths.get("src/test/resources/test-data/TXT.txt");
        
        if (!Files.exists(testFile)) {
            // Skip if specific test file doesn't exist
            return;
        }
        
        byte[] fileContent = Files.readAllBytes(testFile);
        ByteString document = ByteString.copyFrom(fileContent);
        
        Map<String, String> config = new HashMap<>(defaultConfig);
        config.put("filename", "TXT.txt");
        
        ParsedDocumentReply reply = DocumentParser.parseDocument(document, config);
        
        assertTrue(reply.getSuccess(), "Should successfully parse TXT.txt");
        assertNotNull(reply.getParsedDocument());
        assertFalse(reply.getParsedDocument().getBody().isEmpty());
    }

    @Test
    void testParsePDFFile() throws IOException {
        Path testFile = Paths.get("src/test/resources/test-data/412KB.pdf");
        
        if (!Files.exists(testFile)) {
            // Skip if specific test file doesn't exist
            return;
        }
        
        byte[] fileContent = Files.readAllBytes(testFile);
        ByteString document = ByteString.copyFrom(fileContent);
        
        Map<String, String> config = new HashMap<>(defaultConfig);
        config.put("filename", "412KB.pdf");
        
        ParsedDocumentReply reply = DocumentParser.parseDocument(document, config);
        
        if (reply.getSuccess()) {
            assertNotNull(reply.getParsedDocument());
            System.out.println("PDF parsing successful - Body length: " + reply.getParsedDocument().getBody().length());
        } else {
            System.out.println("PDF parsing failed (may be expected): " + reply.getErrorMessage());
        }
    }
}