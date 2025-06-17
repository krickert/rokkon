package com.rokkon.test.generation;

import com.rokkon.search.model.Blob;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.test.data.TestDocumentLoader;
import com.rokkon.test.data.DocumentMetadata;
import com.rokkon.test.data.DocumentMetadataLoader;
import com.rokkon.test.protobuf.ProtobufUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates test data specifically for Tika processing.
 * Creates both request data (PipeStreams with blobs) and response data (PipeDocs with extracted text).
 */
public class TikaTestDataGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(TikaTestDataGenerator.class);
    
    /**
     * Generates Tika request test data (PipeStreams with document blobs).
     * These represent the input to Tika processing.
     */
    public static void generateTikaRequests(String outputDirectory) throws IOException {
        LOG.info("Generating Tika request test data...");
        
        List<PipeStream> requestStreams = createTikaRequestStreams();
        
        String prefix = "tika_request";
        TestDataGenerator.savePipeStreamsToDirectory(requestStreams, outputDirectory, prefix);
        
        LOG.info("Generated {} Tika request PipeStreams", requestStreams.size());
    }
    
    /**
     * Generates Tika response test data (PipeDocs with extracted text).
     * These represent the output of Tika processing.
     */
    public static void generateTikaResponses(String outputDirectory) throws IOException {
        LOG.info("Generating Tika response test data...");
        
        // Use existing processed documents as basis for responses
        List<PipeDoc> existingDocs = TestDocumentLoader.loadAllTikaTestDocuments();
        List<PipeDoc> responseDocs = new ArrayList<>();
        
        // If we have existing docs, use them as the basis
        if (!existingDocs.isEmpty()) {
            LOG.info("Using {} existing Tika-processed documents as response data", existingDocs.size());
            responseDocs.addAll(existingDocs);
        } else {
            // Generate new response documents from raw documents
            LOG.info("No existing processed documents found, generating new response data");
            responseDocs.addAll(createTikaResponseDocs());
        }
        
        String prefix = "tika_response";
        TestDataGenerator.savePipeDocsToDirectory(responseDocs, outputDirectory, prefix);
        
        LOG.info("Generated {} Tika response PipeDocs", responseDocs.size());
    }
    
    /**
     * Creates 99+ PipeStream objects with actual document blobs for Tika input testing.
     * Uses real documents from CSV metadata with variations for comprehensive testing.
     */
    public static List<PipeStream> createTikaRequestStreams() {
        List<PipeStream> streams = new ArrayList<>();
        
        // Load 99 document metadata entries from CSV (with variations)
        List<DocumentMetadata> metadataList = DocumentMetadataLoader.load99DocumentMetadata();
        ClassLoader classLoader = TikaTestDataGenerator.class.getClassLoader();
        
        Timestamp currentTime = ProtobufUtils.now();
        
        for (int i = 0; i < metadataList.size(); i++) {
            DocumentMetadata metadata = metadataList.get(i);
            
            try {
                // Load actual document content instead of generating fake content
                byte[] documentContent = loadActualDocumentContent(metadata, classLoader, i);
                
                if (documentContent != null && documentContent.length > 0) {
                    String streamId = "tika-input-stream-" + String.format("%03d", i);
                    
                    // Create PipeDoc with real blob data
                    Blob blob = Blob.newBuilder()
                            .setFilename(metadata.getFilename())
                            .setMimeType(metadata.getContentType())
                            .setData(ByteString.copyFrom(documentContent))
                            .build();
                    
                    PipeDoc inputDoc = PipeDoc.newBuilder()
                            .setId("tika-input-doc-" + String.format("%03d", i))
                            .setTitle(metadata.getTitle())
                            .setBlob(blob)
                            .addAllKeywords(metadata.getKeywordsList())
                            .setSourceMimeType(metadata.getContentType())
                            .build();
                    
                    // Create PipeStream containing the document
                    PipeStream stream = PipeStream.newBuilder()
                            .setStreamId(streamId)
                            .setDocument(inputDoc)
                            .setCurrentPipelineName("test-tika-pipeline")
                            .setTargetStepName("tika-parser")
                            .setCurrentHopNumber(1)
                            .build();
                    
                    streams.add(stream);
                    LOG.debug("Created Tika request stream {}: {} ({} bytes) - {}", 
                        i, metadata.getFilename(), documentContent.length, metadata.getContentType());
                }
            } catch (Exception e) {
                LOG.error("Error creating stream for document {}: {}", metadata.getFilename(), e.getMessage());
            }
        }
        
        LOG.info("Created {} Tika request streams from metadata", streams.size());
        return streams;
    }
    
    /**
     * Creates PipeDoc objects with extracted text content (simulating Tika output).
     */
    public static List<PipeDoc> createTikaResponseDocs() {
        List<PipeDoc> docs = new ArrayList<>();
        
        // Sample extracted text content for different document types
        String[] sampleTexts = {
            "This is a PDF document with extracted text content. It contains multiple paragraphs and demonstrates how Tika processes PDF files.",
            "Microsoft Word document content extracted successfully. This includes formatting information and text structure.",
            "Legacy Word document (.doc) content. Tika can handle older Microsoft Office formats effectively.",
            "Plain text file content. This is the simplest format for text extraction and processing.",
            "CSV data extracted as text: Header1,Header2,Header3\\nValue1,Value2,Value3\\nData1,Data2,Data3",
            "HTML content extracted: Title Content, Main body text without HTML tags, properly formatted for processing.",
            "Rich Text Format content extracted. RTF documents contain formatting information that Tika processes.",
            "HTML sample content with various elements. Links, headings, and paragraphs are extracted as plain text.",
            "JSON content extracted: Contains structured data that has been converted to readable text format.",
            "Excel spreadsheet content extracted. Multiple sheets and cell data converted to text format.",
            "XML document content extracted. Structured markup converted to readable text while preserving information hierarchy."
        };
        
        Timestamp currentTime = ProtobufUtils.now();
        
        for (int i = 0; i < sampleTexts.length; i++) {
            String docId = "tika-output-doc-" + String.format("%03d", i);
            
            PipeDoc doc = PipeDoc.newBuilder()
                    .setId(docId)
                    .setTitle("Tika Processed Document " + i)
                    .setBody(sampleTexts[i])
                    .setProcessedDate(currentTime)
                    .build();
            
            docs.add(doc);
        }
        
        return docs;
    }
    
    /**
     * Generates sample content for a document based on its type and metadata.
     */
    private static byte[] generateSampleContentForType(DocumentMetadata metadata, int index) {
        String contentType = metadata.getContentType();
        String title = metadata.getTitle();
        String description = metadata.getDescription();
        
        // Generate content based on MIME type
        String content = switch (contentType) {
            case "application/pdf" -> generatePdfLikeContent(title, description, index);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/msword" -> generateWordLikeContent(title, description, index);
            case "text/plain" -> generatePlainTextContent(title, description, index);
            case "text/html" -> generateHtmlContent(title, description, index);
            case "text/csv" -> generateCsvContent(title, description, index);
            case "application/json" -> generateJsonContent(title, description, index);
            case "application/xml" -> generateXmlContent(title, description, index);
            case "application/rtf" -> generateRtfContent(title, description, index);
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/vnd.ms-excel" -> generateSpreadsheetContent(title, description, index);
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                 "application/vnd.ms-powerpoint" -> generatePresentationContent(title, description, index);
            default -> generateGenericContent(title, description, index);
        };
        
        return content.getBytes();
    }
    
    private static String generatePdfLikeContent(String title, String description, int index) {
        return String.format("""
            %%PDF-1.4
            1 0 obj
            <<
            /Type /Catalog
            /Pages 2 0 R
            >>
            endobj
            
            DOCUMENT TITLE: %s
            DESCRIPTION: %s
            
            This is a sample PDF document (index %d) generated for testing purposes.
            It contains multiple paragraphs to simulate realistic PDF content.
            
            Chapter 1: Introduction
            This document represents a typical PDF file that would be processed by Tika.
            The content includes various formatting elements and text structures.
            
            Chapter 2: Content Analysis  
            PDF documents often contain structured text, metadata, and formatting information.
            This sample demonstrates how such content would appear after extraction.
            
            Chapter 3: Testing Methodology
            The document includes sufficient text to enable proper chunking and embedding tests.
            Various content types and structures are represented for comprehensive testing.
            
            Conclusion:
            This completes the sample PDF content for document %d.
            """, title, description, index, index);
    }
    
    private static String generateWordLikeContent(String title, String description, int index) {
        return String.format("""
            DOCUMENT: %s
            
            %s
            
            Executive Summary:
            This Microsoft Word document (index %d) contains structured content typical of business documents.
            
            1. Introduction
               - Purpose and scope of this document
               - Target audience and objectives
               - Document structure overview
            
            2. Main Content Sections
               - Detailed analysis and findings
               - Supporting data and evidence
               - Recommendations and next steps
            
            3. Technical Specifications
               - System requirements and constraints
               - Implementation guidelines
               - Quality assurance measures
            
            4. Conclusion
               - Summary of key points
               - Future considerations
               - Contact information
            
            This document provides comprehensive coverage of the topic for testing document processing capabilities.
            The content is designed to be representative of real-world Word documents.
            
            Document ID: %d
            Generated for Tika processing tests.
            """, title, description, index, index);
    }
    
    private static String generatePlainTextContent(String title, String description, int index) {
        return String.format("""
            %s
            ===============================
            
            %s
            
            This is a plain text document (index %d) containing unformatted content.
            
            Plain text files are among the simplest document types to process.
            They contain no formatting information, only raw textual content.
            
            Key characteristics of plain text:
            - No formatting or styling
            - Universal compatibility
            - Small file size
            - Easy to process and analyze
            
            This sample text includes multiple paragraphs and varied content
            to ensure proper testing of text extraction and chunking algorithms.
            
            The document contains sufficient content for embedding generation
            and provides a good baseline for comparing extraction results
            across different document types.
            
            End of document %d.
            """, title, description, index, index);
    }
    
    private static String generateHtmlContent(String title, String description, int index) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <title>%s</title>
                <meta charset="UTF-8">
            </head>
            <body>
                <h1>%s</h1>
                <p>%s</p>
                
                <h2>Content Overview</h2>
                <p>This HTML document (index %d) demonstrates typical web content structure.</p>
                
                <h3>Key Features</h3>
                <ul>
                    <li>Structured markup with headings</li>
                    <li>Paragraphs and text formatting</li>
                    <li>Lists and hierarchical content</li>
                    <li>Semantic HTML elements</li>
                </ul>
                
                <h3>Testing Objectives</h3>
                <ol>
                    <li>Verify HTML tag removal during extraction</li>
                    <li>Ensure proper text structure preservation</li>
                    <li>Test handling of special characters</li>
                    <li>Validate content hierarchy maintenance</li>
                </ol>
                
                <p>This document provides comprehensive HTML content for testing
                Tika's ability to extract clean text from web-formatted documents.</p>
                
                <footer>
                    <p>Document %d - Generated for testing purposes</p>
                </footer>
            </body>
            </html>
            """, title, title, description, index, index);
    }
    
    private static String generateCsvContent(String title, String description, int index) {
        return String.format("""
            # %s
            # %s
            # Document Index: %d
            
            ID,Name,Category,Value,Description
            1,Sample Item A,Category 1,100.50,First test item
            2,Sample Item B,Category 2,200.75,Second test item  
            3,Sample Item C,Category 1,150.25,Third test item
            4,Sample Item D,Category 3,300.00,Fourth test item
            5,Sample Item E,Category 2,75.50,Fifth test item
            6,Sample Item F,Category 1,425.75,Sixth test item
            7,Sample Item G,Category 3,50.00,Seventh test item
            8,Sample Item H,Category 2,275.25,Eighth test item
            9,Sample Item I,Category 1,325.50,Ninth test item
            10,Sample Item J,Category 3,180.75,Tenth test item
            
            # Summary Statistics
            # Total Items: 10
            # Categories: 3
            # Document: %d
            """, title, description, index, index);
    }
    
    private static String generateJsonContent(String title, String description, int index) {
        return String.format("""
            {
              "document": {
                "title": "%s",
                "description": "%s",
                "index": %d,
                "type": "json_sample"
              },
              "metadata": {
                "created": "2023-01-01T00:00:00Z",
                "author": "Test Generator",
                "version": "1.0"
              },
              "content": {
                "sections": [
                  {
                    "id": 1,
                    "title": "Introduction",
                    "text": "This JSON document contains structured data for testing purposes."
                  },
                  {
                    "id": 2,
                    "title": "Data Analysis",
                    "text": "JSON files require special handling to extract meaningful text content."
                  },
                  {
                    "id": 3,
                    "title": "Test Results",
                    "text": "The extraction process should preserve the textual information while ignoring structure."
                  }
                ],
                "tags": ["json", "test", "sample", "document"],
                "statistics": {
                  "word_count": 150,
                  "section_count": 3,
                  "tag_count": 4
                }
              },
              "footer": {
                "generated_for": "tika_testing",
                "document_id": %d
              }
            }
            """, title, description, index, index);
    }
    
    private static String generateXmlContent(String title, String description, int index) {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <metadata>
                    <title>%s</title>
                    <description>%s</description>
                    <index>%d</index>
                    <type>xml_sample</type>
                </metadata>
                
                <content>
                    <section id="1">
                        <heading>XML Document Structure</heading>
                        <paragraph>This XML document demonstrates hierarchical content organization typical of structured documents.</paragraph>
                    </section>
                    
                    <section id="2">
                        <heading>Content Extraction Testing</heading>
                        <paragraph>XML files contain both structural markup and textual content that needs to be separated during processing.</paragraph>
                        <list>
                            <item>Tag structure preservation</item>
                            <item>Text content extraction</item>
                            <item>Attribute handling</item>
                            <item>Hierarchy maintenance</item>
                        </list>
                    </section>
                    
                    <section id="3">
                        <heading>Processing Considerations</heading>
                        <paragraph>The Tika parser should extract clean text while maintaining logical document structure.</paragraph>
                    </section>
                </content>
                
                <footer>
                    <generated>true</generated>
                    <purpose>tika_testing</purpose>
                    <document_id>%d</document_id>
                </footer>
            </document>
            """, title, description, index, index);
    }
    
    private static String generateRtfContent(String title, String description, int index) {
        return String.format("""
            {\\rtf1\\ansi\\deff0 {\\fonttbl {\\f0 Times New Roman;}}
            \\f0\\fs24 
            \\b %s \\b0
            \\par
            \\par
            %s
            \\par
            \\par
            This Rich Text Format document (index %d) contains formatted content with various styling elements.
            \\par
            \\par
            \\b Key Features: \\b0
            \\par
            \\bullet Text formatting and styling
            \\par  
            \\bullet Font and size specifications
            \\par
            \\bullet Paragraph structures
            \\par
            \\bullet Special character handling
            \\par
            \\par
            \\i The RTF format allows for rich text formatting while maintaining compatibility across different systems. \\i0
            \\par
            \\par
            This document provides comprehensive content for testing RTF parsing and text extraction capabilities.
            \\par
            \\par
            Document ID: %d
            \\par
            }
            """, title, description, index, index);
    }
    
    private static String generateSpreadsheetContent(String title, String description, int index) {
        return String.format("""
            SPREADSHEET: %s
            DESCRIPTION: %s
            INDEX: %d
            
            Sheet 1: Data Analysis
            A1: Item | B1: Category | C1: Value | D1: Status
            A2: Product A | B2: Electronics | C2: 299.99 | D2: Active
            A3: Product B | B3: Books | C3: 19.95 | D3: Active  
            A4: Product C | B4: Electronics | C4: 599.99 | D4: Discontinued
            A5: Product D | B5: Clothing | C5: 79.50 | D5: Active
            A6: Product E | B6: Books | C6: 24.99 | D6: Active
            
            Sheet 2: Summary Statistics
            A1: Category | B1: Count | C1: Total Value
            A2: Electronics | B2: 2 | C2: 899.98
            A3: Books | B3: 2 | C3: 44.94
            A4: Clothing | B4: 1 | C4: 79.50
            
            Sheet 3: Notes
            A1: This spreadsheet contains sample data for testing Excel/ODS file processing.
            A2: The data includes multiple sheets with different types of content.
            A3: Text extraction should preserve the tabular structure information.
            A4: Document %d generated for Tika testing purposes.
            """, title, description, index, index);
    }
    
    private static String generatePresentationContent(String title, String description, int index) {
        return String.format("""
            PRESENTATION: %s
            
            %s
            
            Slide 1: Title Slide
            %s
            Document Index: %d
            
            Slide 2: Agenda
            - Introduction and Overview
            - Key Concepts and Definitions
            - Detailed Analysis
            - Recommendations
            - Questions and Discussion
            
            Slide 3: Introduction
            This presentation demonstrates content typical of PowerPoint/ODP files.
            The material covers various topics relevant to document processing testing.
            
            Slide 4: Key Concepts
            • Document Structure Analysis
            • Content Extraction Methodologies  
            • Text Processing Techniques
            • Quality Assurance Measures
            
            Slide 5: Detailed Analysis
            PowerPoint presentations often contain:
            - Bullet points and structured text
            - Multiple slides with varied content
            - Headers, footers, and slide numbers
            - Mixed content types within slides
            
            Slide 6: Recommendations
            For optimal text extraction:
            1. Preserve slide structure information
            2. Maintain text hierarchy and formatting
            3. Handle special characters appropriately
            4. Ensure complete content coverage
            
            Slide 7: Conclusion
            This presentation provides comprehensive content for testing
            presentation file parsing and text extraction capabilities.
            
            Document %d - End of Presentation
            """, title, title, description, index, index);
    }
    
    private static String generateGenericContent(String title, String description, int index) {
        return String.format("""
            %s
            
            %s
            
            This is a generic document (index %d) containing sample content for testing purposes.
            
            The document includes various types of text content to ensure comprehensive
            testing of document processing capabilities across different file formats.
            
            Content Features:
            - Multiple paragraphs with varied text
            - Structured information layout
            - Descriptive and technical content
            - Sufficient length for chunking tests
            
            This generic content serves as a fallback for document types that don't
            have specific content generation patterns defined.
            
            The text is designed to be representative of real-world documents
            while providing consistent testing data for the Tika processing pipeline.
            
            Document identifier: %d
            """, title, description, index, index);
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
    
    /**
     * Gets MIME type for a given index (matching the order of test files).
     */
    private static String getMimeTypeForIndex(int index) {
        String[] mimeTypes = {
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "text/plain",
            "text/csv",
            "text/html",
            "application/rtf",
            "text/html",
            "application/json",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/xml"
        };
        
        return index < mimeTypes.length ? mimeTypes[index] : "application/octet-stream";
    }
    
    /**
     * Loads actual document content from source-documents directory.
     * For variations, creates modified content based on the base document.
     */
    private static byte[] loadActualDocumentContent(DocumentMetadata metadata, ClassLoader classLoader, int index) {
        String filename = metadata.getFilename();
        
        // For base documents (no variation), load actual file content
        if (!filename.contains("_v")) {
            return loadRealDocumentFile(filename, classLoader);
        }
        
        // For variations, load base document and create variation
        String baseFilename = extractBaseFilename(filename);
        byte[] baseContent = loadRealDocumentFile(baseFilename, classLoader);
        
        if (baseContent != null && baseContent.length > 0) {
            return createVariationContent(baseContent, metadata, index);
        }
        
        return null;
    }
    
    /**
     * Loads a real document file from the source-documents directory.
     */
    private static byte[] loadRealDocumentFile(String filename, ClassLoader classLoader) {
        String resourcePath = "test-data/source-documents/" + filename;
        
        try (InputStream is = classLoader.getResourceAsStream(resourcePath)) {
            if (is != null) {
                return is.readAllBytes();
            } else {
                LOG.warn("Could not find real document: {}", resourcePath);
                return null;
            }
        } catch (IOException e) {
            LOG.error("Error reading real document {}: {}", resourcePath, e.getMessage());
            return null;
        }
    }
    
    /**
     * Extracts the base filename from a variation filename.
     * E.g., "sample_1mb_v1.txt" -> "sample_1mb.txt"
     */
    private static String extractBaseFilename(String variationFilename) {
        return variationFilename.replaceAll("_v\\d+", "");
    }
    
    /**
     * Creates variation content by modifying the base document content.
     * For text-based formats, adds variation markers.
     * For binary formats, returns original content (variations are metadata-only).
     */
    private static byte[] createVariationContent(byte[] baseContent, DocumentMetadata metadata, int index) {
        if (baseContent == null || baseContent.length == 0) {
            return baseContent;
        }
        
        String mimeType = metadata.getContentType();
        
        // For text-based formats, add variation markers
        if (mimeType.startsWith("text/") || 
            mimeType.equals("application/json") ||
            mimeType.equals("application/xml") ||
            mimeType.contains("java-source") ||
            mimeType.contains("kotlin") ||
            mimeType.contains("gradle")) {
            
            try {
                String originalText = new String(baseContent, "UTF-8");
                String variationText = addTextVariationMarkers(originalText, metadata, index);
                return variationText.getBytes("UTF-8");
            } catch (Exception e) {
                LOG.warn("Failed to create text variation for {}: {}", metadata.getFilename(), e.getMessage());
                return baseContent;
            }
        }
        
        // For binary formats (PDF, Office docs, etc.), return original content
        // Variations for these formats are primarily metadata-based
        return baseContent;
    }
    
    /**
     * Adds variation markers to text content.
     */
    private static String addTextVariationMarkers(String originalText, DocumentMetadata metadata, int index) {
        StringBuilder varied = new StringBuilder();
        
        // Add variation header
        varied.append("// VARIATION ").append(index).append(" of ").append(metadata.getTitle()).append("\n");
        varied.append("// Generated on: ").append(java.time.Instant.now()).append("\n");
        varied.append("// Base file: ").append(extractBaseFilename(metadata.getFilename())).append("\n\n");
        
        // Add original content
        varied.append(originalText);
        
        // Add variation footer
        varied.append("\n\n// END VARIATION ").append(index);
        varied.append("\n// Total size: ").append(varied.length()).append(" characters");
        
        return varied.toString();
    }
}