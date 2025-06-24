package com.rokkon.parser.util;

import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.PipeDoc;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.jboss.logging.Logger;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Utility class for parsing documents using Apache Tika.
 * 
 * <p>This class provides static methods to parse various document formats using Apache Tika.
 * It supports custom parser configurations, metadata extraction, and content length limits.
 * The parser can handle various document formats including PDF, Microsoft Office documents,
 * HTML, XML, and plain text files.</p>
 * 
 * <p>The parser can be configured to disable specific parsers (like EMF) for problematic
 * file types, or enable special parsers (like GeoTopicParser) for enhanced functionality.</p>
 */
public class DocumentParser {
    private static final Logger LOG = Logger.getLogger(DocumentParser.class);

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private DocumentParser() {
        // Utility class
    }

    /**
     * Parses a document and returns a PipeDoc with the parsed content.
     *
     * @param content The content of the document to parse.
     * @param configMap The configuration map for the parser.
     * @param filename Optional filename for content type detection and EMF parser logic.
     * @return A PipeDoc object containing the parsed title, body, and metadata.
     * @throws IOException if an I/O error occurs while parsing the document.
     * @throws SAXException if a SAX error occurs while parsing the document.
     * @throws TikaException if a Tika error occurs while parsing the document.
     */
    public static PipeDoc parseDocument(ByteString content, Map<String, String> configMap, String filename) 
            throws IOException, SAXException, TikaException {
        
        LOG.debugf("Parsing document with filename: %s, content size: %d bytes", 
                  filename, content.size());
        
        // Create the appropriate parser based on configuration
        Parser parser = createParser(configMap, filename);
        
        // Set up the content handler with the specified max content length
        BodyContentHandler handler = createContentHandler(configMap);
        
        // Set up metadata and parse context
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        parseContext.set(Parser.class, parser);
        
        // Parse the document
        try (InputStream stream = new ByteArrayInputStream(content.toByteArray())) {
            // Add filename to metadata if available
            if (filename != null && !filename.isEmpty()) {
                metadata.set("resourceName", filename);
            }
            
            parser.parse(stream, handler, metadata, parseContext);
        }
        
        // Extract title and body
        String title = extractTitle(metadata, handler.toString(), configMap);
        String body = extractBody(handler.toString(), metadata, content, configMap);
        
        LOG.debugf("Parsed document - title: '%s', body length: %d, content type: %s", 
                  title, body.length(), metadata.get("Content-Type"));
        
        // Build the PipeDoc
        PipeDoc.Builder docBuilder = PipeDoc.newBuilder()
                .setBody(body);
        
        if (title != null && !title.isEmpty()) {
            docBuilder.setTitle(title);
        }
        
        // Add metadata if requested
        if (getBooleanConfig(configMap, "extractMetadata", true)) {
            Map<String, String> metadataMap = MetadataMapper.toMap(metadata, configMap);
            if (!metadataMap.isEmpty()) {
                Struct.Builder structBuilder = Struct.newBuilder();
                for (Map.Entry<String, String> entry : metadataMap.entrySet()) {
                    structBuilder.putFields(entry.getKey(), Value.newBuilder().setStringValue(entry.getValue()).build());
                }
                docBuilder.setCustomData(structBuilder.build());
            }
        }
        
        PipeDoc parsedDoc = docBuilder.build();
        
        // Apply post-processing based on document type if title extraction is enabled
        if (getBooleanConfig(configMap, "enableTitleExtraction", true)) {
            parsedDoc = postProcessParsedDocument(parsedDoc, metadata, filename, configMap);
        }
        
        return parsedDoc;
    }
    
    /**
     * Convenience method that parses without filename.
     */
    public static PipeDoc parseDocument(ByteString content, Map<String, String> configMap) 
            throws IOException, SAXException, TikaException {
        return parseDocument(content, configMap, null);
    }
    
    /**
     * Creates the appropriate Tika parser based on configuration.
     * 
     * TODO: Fix EMF parser assertion errors - Currently some documents with embedded EMF images
     * (particularly older PowerPoint files) can cause AssertionError in Apache POI's EMF parser.
     * This is a known issue in POI/Tika. For now, we catch these errors in ParserServiceImpl
     * and return graceful failures. In the future, we should:
     * 1. Update to newer Tika version when the fix is available
     * 2. Consider always disabling EMF parser for problematic document types
     * 3. Implement custom EMF parser configuration based on document analysis
     */
    private static Parser createParser(Map<String, String> configMap, String filename) {
        boolean disableEmfParser = shouldDisableEmfParserForFile(configMap, filename);
        boolean enableGeoTopicParser = getBooleanConfig(configMap, "enableGeoTopicParser", false);
        
        if (disableEmfParser) {
            LOG.infof("Creating custom parser with EMF parser disabled for file: %s", filename);
            try {
                String customConfig = createCustomParserConfig();
                try (InputStream is = new ByteArrayInputStream(customConfig.getBytes())) {
                    TikaConfig tikaConfig = new TikaConfig(is);
                    return new AutoDetectParser(tikaConfig);
                }
            } catch (Exception e) {
                LOG.error("Failed to create custom parser configuration: {}", e.getMessage(), e);
                LOG.info("Falling back to default Tika configuration");
                return new AutoDetectParser();
            }
        } else if (enableGeoTopicParser) {
            LOG.info("Creating parser with GeoTopicParser enabled");
            try {
                String geoTopicConfig = createGeoTopicParserConfig();
                try (InputStream is = new ByteArrayInputStream(geoTopicConfig.getBytes())) {
                    TikaConfig tikaConfig = new TikaConfig(is);
                    return new AutoDetectParser(tikaConfig);
                }
            } catch (Exception e) {
                LOG.error("Failed to create GeoTopicParser configuration: {}", e.getMessage(), e);
                LOG.info("Falling back to default Tika configuration");
                return new AutoDetectParser();
            }
        } else {
            LOG.debug("Using default Tika configuration");
            return new AutoDetectParser();
        }
    }
    
    /**
     * Creates a content handler with appropriate limits.
     */
    private static BodyContentHandler createContentHandler(Map<String, String> configMap) {
        int maxContentLength = getIntConfig(configMap, "maxContentLength", -1);
        
        if (maxContentLength > 0) {
            // Use WriteOutContentHandler for better memory management with large documents
            return new BodyContentHandler(new WriteOutContentHandler(maxContentLength));
        } else {
            // Use -1 for unlimited content length
            return new BodyContentHandler(-1);
        }
    }
    
    /**
     * Extracts title from metadata with fallbacks.
     */
    private static String extractTitle(Metadata metadata, String body, Map<String, String> configMap) {
        // Try various title metadata fields
        String title = cleanUpText(metadata.get("dc:title"));
        if (title == null || title.isEmpty()) {
            title = cleanUpText(metadata.get("title"));
        }
        if (title == null || title.isEmpty()) {
            title = cleanUpText(metadata.get("Title"));
        }
        
        // If still no title and body extraction is available, try to extract from first line
        if ((title == null || title.isEmpty()) && getBooleanConfig(configMap, "enableTitleExtraction", true) && body != null) {
            String[] lines = body.split("\n", 3);
            if (lines.length > 0) {
                String firstLine = cleanUpText(lines[0]);
                if (firstLine != null && firstLine.length() > 0 && firstLine.length() < 200) {
                    title = firstLine;
                }
            }
        }
        
        return title;
    }
    
    /**
     * Extracts body content with fallbacks.
     */
    private static String extractBody(String handlerContent, Metadata metadata, ByteString originalContent, Map<String, String> configMap) {
        String body = cleanUpText(handlerContent);
        
        // If body is empty, try to get content from other metadata fields
        if (body.isEmpty()) {
            String contentFromMetadata = cleanUpText(metadata.get("content"));
            if (!contentFromMetadata.isEmpty()) {
                body = contentFromMetadata;
            }
        }
        
        // If still empty and it's a text file, use the content directly
        if (body.isEmpty() && metadata.get("Content-Type") != null && 
                metadata.get("Content-Type").startsWith("text/")) {
            body = new String(originalContent.toByteArray(), StandardCharsets.UTF_8);
            body = cleanUpText(body);
        }
        
        // If body is still empty, use a default message
        if (body.isEmpty()) {
            body = "This document was processed by the parser but no text content was extracted.";
            if (getBooleanConfig(configMap, "logParsingErrors", false)) {
                LOG.warn("No text content extracted from document. Using default message.");
            }
        }
        
        return body;
    }
    
    /**
     * Post-processes a parsed document based on its content type.
     */
    private static PipeDoc postProcessParsedDocument(PipeDoc parsedDoc, Metadata metadata, String filename, Map<String, String> configMap) {
        // If both title and body are non-empty, minimal post-processing needed
        if (!parsedDoc.getTitle().isEmpty() && !parsedDoc.getBody().isEmpty()) {
            return parsedDoc;
        }
        
        // Get the content type from metadata
        String contentType = metadata.get("Content-Type");
        if (contentType == null || contentType.isEmpty()) {
            // Try to infer content type from filename if available
            if (filename != null && getBooleanConfig(configMap, "fallbackToFilename", true)) {
                contentType = inferContentTypeFromFilename(filename);
            }
        }
        
        if (contentType != null) {
            LOG.debugf("Post-processing document with content type: %s", contentType);
            
            // Apply document type-specific processing
            PipeDoc.Builder builder = parsedDoc.toBuilder();
            
            if (contentType.startsWith("application/pdf")) {
                processPdfDocument(parsedDoc, builder, metadata);
            } else if (contentType.contains("presentation")) {
                processPresentationDocument(parsedDoc, builder, metadata);
            } else if (contentType.contains("wordprocessing") || contentType.contains("msword")) {
                processWordDocument(parsedDoc, builder, metadata);
            } else if (contentType.contains("spreadsheet") || contentType.contains("excel")) {
                processSpreadsheetDocument(parsedDoc, builder, metadata);
            } else if (contentType.startsWith("text/html")) {
                processHtmlDocument(parsedDoc, builder, metadata);
            }
            
            return builder.build();
        }
        
        return parsedDoc;
    }
    
    /**
     * Creates a custom Tika configuration XML that disables problematic parsers.
     */
    private static String createCustomParserConfig() 
            throws ParserConfigurationException, TransformerException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        // Root element
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("properties");
        doc.appendChild(rootElement);

        // Add parser options
        Element parsers = doc.createElement("parsers");
        rootElement.appendChild(parsers);

        // Disable EMF Parser
        Element emfParser = doc.createElement("parser");
        emfParser.setAttribute("class", "org.apache.tika.parser.microsoft.EMFParser");
        emfParser.setAttribute("enabled", "false");
        parsers.appendChild(emfParser);

        return transformDocumentToString(doc);
    }
    
    /**
     * Creates a Tika configuration XML with GeoTopicParser enabled.
     */
    private static String createGeoTopicParserConfig() 
            throws ParserConfigurationException, TransformerException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        // Root element
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("properties");
        doc.appendChild(rootElement);

        // Add parser options
        Element parsers = doc.createElement("parsers");
        rootElement.appendChild(parsers);

        // Add GeoTopicParser
        Element geoTopicParser = doc.createElement("parser");
        geoTopicParser.setAttribute("class", "org.apache.tika.parser.geo.topic.GeoTopicParser");
        geoTopicParser.setAttribute("enabled", "true");
        parsers.appendChild(geoTopicParser);

        return transformDocumentToString(doc);
    }
    
    /**
     * Transforms an XML Document to a string.
     */
    private static String transformDocumentToString(Document doc) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
    
    // Document type-specific processing methods (simplified versions)
    private static void processPdfDocument(PipeDoc parsedDoc, PipeDoc.Builder builder, Metadata metadata) {
        if (parsedDoc.getTitle().isEmpty()) {
            String title = metadata.get("pdf:docinfo:title");
            if (title != null && !title.isEmpty()) {
                builder.setTitle(cleanUpText(title));
            }
        }
    }
    
    private static void processPresentationDocument(PipeDoc parsedDoc, PipeDoc.Builder builder, Metadata metadata) {
        if (parsedDoc.getTitle().isEmpty()) {
            String title = metadata.get("dc:title");
            if (title == null || title.isEmpty()) {
                title = metadata.get("title");
            }
            if (title != null && !title.isEmpty()) {
                builder.setTitle(cleanUpText(title));
            }
        }
    }
    
    private static void processWordDocument(PipeDoc parsedDoc, PipeDoc.Builder builder, Metadata metadata) {
        if (parsedDoc.getTitle().isEmpty()) {
            String title = metadata.get("dc:title");
            if (title == null || title.isEmpty()) {
                title = metadata.get("title");
            }
            if (title != null && !title.isEmpty()) {
                builder.setTitle(cleanUpText(title));
            }
        }
    }
    
    private static void processSpreadsheetDocument(PipeDoc parsedDoc, PipeDoc.Builder builder, Metadata metadata) {
        if (parsedDoc.getTitle().isEmpty()) {
            String title = metadata.get("dc:title");
            if (title != null && !title.isEmpty()) {
                builder.setTitle(cleanUpText(title));
            }
        }
    }
    
    private static void processHtmlDocument(PipeDoc parsedDoc, PipeDoc.Builder builder, Metadata metadata) {
        if (parsedDoc.getTitle().isEmpty()) {
            String title = metadata.get("dc:title");
            if (title == null || title.isEmpty()) {
                title = metadata.get("title");
            }
            if (title != null && !title.isEmpty()) {
                builder.setTitle(cleanUpText(title));
            }
        }
    }
    
    /**
     * Infers content type from filename extension.
     */
    private static String inferContentTypeFromFilename(String filename) {
        if (filename == null) return "";
        
        String lowerFilename = filename.toLowerCase();
        if (lowerFilename.endsWith(".pdf")) return "application/pdf";
        if (lowerFilename.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lowerFilename.endsWith(".doc")) return "application/msword";
        if (lowerFilename.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (lowerFilename.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (lowerFilename.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lowerFilename.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lowerFilename.endsWith(".txt")) return "text/plain";
        if (lowerFilename.endsWith(".html") || lowerFilename.endsWith(".htm")) return "text/html";
        if (lowerFilename.endsWith(".json")) return "application/json";
        if (lowerFilename.endsWith(".xml")) return "application/xml";
        
        return "";
    }
    
    /**
     * Cleans up extracted text by trimming whitespace and normalizing line breaks.
     */
    private static String cleanUpText(String text) {
        if (text == null) {
            return "";
        }
        
        return text.trim()
                   .replaceAll("\\s+", " ")  // Replace multiple whitespace with single space
                   .replaceAll("\\n\\s*\\n", "\n\n");  // Normalize double line breaks
    }
    
    // Helper methods for configuration extraction
    
    /**
     * Gets an integer configuration value with a default.
     */
    private static int getIntConfig(Map<String, String> configMap, String key, int defaultValue) {
        String value = configMap.get(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOG.warnf("Invalid integer value for config key '%s': %s, using default: %d", key, value, defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * Gets a boolean configuration value with a default.
     */
    private static boolean getBooleanConfig(Map<String, String> configMap, String key, boolean defaultValue) {
        String value = configMap.get(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Determines if EMF parser should be disabled for a specific file.
     */
    private static boolean shouldDisableEmfParserForFile(Map<String, String> configMap, String filename) {
        // Check if EMF parser is explicitly disabled
        if (getBooleanConfig(configMap, "disableEmfParser", false)) {
            return true;
        }
        
        // Check if this file type should disable EMF parser
        if (filename != null) {
            String lowerFilename = filename.toLowerCase();
            
            // Disable EMF parser for problematic file types
            if (lowerFilename.endsWith(".emf") || 
                lowerFilename.endsWith(".wmf") ||
                lowerFilename.contains("corrupted")) {
                return true;
            }
        }
        
        return false;
    }
}