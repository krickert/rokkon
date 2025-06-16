package com.rokkon.modules.tika;

import com.rokkon.search.model.ParsedDocument;
import com.rokkon.search.model.ParsedDocumentReply;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple tests for DocumentParser that actually compile and run.
 */
class DocumentParserSimpleTest {

    private Map<String, String> defaultConfig;

    @BeforeEach
    void setUp() {
        defaultConfig = new HashMap<>();
        defaultConfig.put("maxContentLength", "1000000");
        defaultConfig.put("extractMetadata", "true");
    }

    @Test
    void testParseSimpleTextDocument() throws Exception {
        String content = "This is a test document with some sample content.";
        ByteString document = ByteString.copyFromUtf8(content);
        
        Map<String, String> config = new HashMap<>(defaultConfig);
        config.put("filename", "test.txt");
        
        ParsedDocumentReply reply = DocumentParser.parseDocument(document, config);
        
        assertTrue(reply.getSuccess());
        assertNotNull(reply.getParsedDocument());
        
        ParsedDocument parsedDoc = reply.getParsedDocument();
        assertEquals(content, parsedDoc.getBody());
    }

    @Test
    void testParseEmptyDocument() throws Exception {
        ByteString emptyDocument = ByteString.EMPTY;
        
        ParsedDocumentReply reply = DocumentParser.parseDocument(emptyDocument, defaultConfig);
        
        // Tika throws ZeroByteFileException for empty files, which is expected behavior
        assertFalse(reply.getSuccess());
        assertFalse(reply.getErrorMessage().isEmpty());
        assertTrue(reply.getErrorMessage().contains("InputStream must have > 0 bytes"));
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
}