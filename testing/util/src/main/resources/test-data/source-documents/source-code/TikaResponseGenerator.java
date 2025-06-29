package com.rokkon.test.generation;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.test.protobuf.ProtobufUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates Tika response test data (PipeDocs with extracted text) from Tika request streams.
 * This simulates what the Tika parser would produce when processing documents.
 */
public class TikaResponseGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(TikaResponseGenerator.class);
    
    /**
     * Generates 99 Tika response PipeDocs with extracted text content.
     * These represent what Tika would produce when processing the input streams.
     */
    public static List<PipeDoc> createTikaResponseDocs(List<PipeStream> inputStreams) {
        List<PipeDoc> responseDocs = new ArrayList<>();
        
        for (int i = 0; i < inputStreams.size(); i++) {
            PipeStream inputStream = inputStreams.get(i);
            PipeDoc inputDoc = inputStream.getDocument();
            
            // Generate extracted text based on the original document
            String extractedText = generateExtractedText(inputDoc, i);
            
            // Create response document with extracted text
            PipeDoc responseDoc = PipeDoc.newBuilder()
                    .setId("tika-response-doc-" + String.format("%03d", i))
                    .setTitle(inputDoc.getTitle())
                    .setBody(extractedText)
                    .setSourceMimeType(inputDoc.getSourceMimeType())
                    .addAllKeywords(inputDoc.getKeywordsList())
                    .setProcessedDate(ProtobufUtils.now())
                    // Remove blob data as Tika extracts text
                    .build();
            
            responseDocs.add(responseDoc);
            
            if (i < 10 || i % 20 == 0) { // Log first 10 and every 20th
                LOG.debug("Created Tika response doc {}: {} ({} chars)", 
                    i, responseDoc.getTitle(), extractedText.length());
            }
        }
        
        LOG.info("Generated {} Tika response documents", responseDocs.size());
        return responseDocs;
    }
    
    /**
     * Generates extracted text content that simulates what Tika would extract from the document.
     */
    private static String generateExtractedText(PipeDoc inputDoc, int index) {
        String mimeType = inputDoc.getSourceMimeType();
        String title = inputDoc.getTitle();
        
        // Generate realistic extracted text based on document type
        String extractedText = switch (mimeType) {
            case "application/pdf" -> generatePdfExtractedText(title, index);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/msword" -> generateWordExtractedText(title, index);
            case "text/plain" -> generatePlainTextExtracted(title, index);
            case "text/html" -> generateHtmlExtractedText(title, index);
            case "text/csv" -> generateCsvExtractedText(title, index);
            case "application/json" -> generateJsonExtractedText(title, index);
            case "application/xml" -> generateXmlExtractedText(title, index);
            case "application/rtf" -> generateRtfExtractedText(title, index);
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/vnd.ms-excel" -> generateSpreadsheetExtractedText(title, index);
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                 "application/vnd.ms-powerpoint" -> generatePresentationExtractedText(title, index);
            default -> generateGenericExtractedText(title, index);
        };
        
        return extractedText;
    }
    
    private static String generatePdfExtractedText(String title, int index) {
        return String.format("""
            %s
            
            This is extracted text from a PDF document (index %d) that has been processed by Tika.
            All formatting and layout information has been removed, leaving only the textual content.
            
            Introduction
            This document represents a typical PDF file that would be processed by Tika.
            The content includes various formatting elements and text structures that have been extracted.
            
            Content Analysis  
            PDF documents often contain structured text, metadata, and formatting information.
            This sample demonstrates how such content would appear after extraction.
            The original formatting has been preserved as much as possible while converting to plain text.
            
            Testing Methodology
            The document includes sufficient text to enable proper chunking and embedding tests.
            Various content types and structures are represented for comprehensive testing.
            This extracted content maintains the logical flow of the original document.
            
            Key Benefits of Text Extraction:
            - Removes proprietary formatting
            - Enables full-text search capabilities
            - Facilitates content analysis and processing
            - Supports downstream NLP operations
            
            Technical Considerations
            Tika's PDF parser handles various PDF versions and formats.
            Complex layouts are flattened into readable text streams.
            Special characters and symbols are preserved where possible.
            
            Conclusion
            This completes the extracted content from PDF document %d.
            The text is now ready for further processing and analysis.
            """, title, index, index);
    }
    
    private static String generateWordExtractedText(String title, int index) {
        return String.format("""
            %s
            
            Executive Summary
            This Microsoft Word document (index %d) contains structured content typical of business documents.
            The text has been extracted while preserving paragraph structure and logical flow.
            
            Introduction
            Purpose and scope of this document
            Target audience and objectives  
            Document structure overview
            
            Main Content Sections
            Detailed analysis and findings
            Supporting data and evidence
            Recommendations and next steps
            
            The original formatting including bold, italic, and other text styling has been removed.
            Lists and bullet points are converted to simple text format.
            Headers and subheaders maintain their hierarchical relationship.
            
            Technical Specifications
            System requirements and constraints
            Implementation guidelines
            Quality assurance measures
            
            Data Analysis Results
            Statistical findings and interpretations
            Performance metrics and benchmarks
            Comparative analysis with industry standards
            
            Recommendations
            Strategic recommendations based on analysis
            Implementation timeline and milestones
            Resource allocation and budget considerations
            Risk assessment and mitigation strategies
            
            Conclusion
            Summary of key points and findings
            Future considerations and next steps
            Contact information for follow-up questions
            
            This document provides comprehensive coverage of the topic for testing document processing capabilities.
            The content demonstrates typical business document structure and complexity.
            Document ID: %d - Generated for Tika processing validation.
            """, title, index, index);
    }
    
    private static String generatePlainTextExtracted(String title, int index) {
        return String.format("""
            %s
            
            This is extracted text from a plain text document (index %d).
            Since the original was already plain text, minimal processing was required.
            
            Plain text files are among the simplest document types to process.
            They contain no formatting information, only raw textual content.
            Tika processing preserves the original line breaks and paragraph structure.
            
            Key characteristics of plain text extraction:
            No formatting or styling information to remove
            Universal compatibility across systems
            Minimal processing overhead required
            Direct content preservation
            
            Content Structure Analysis
            This sample text includes multiple paragraphs and varied content
            to ensure proper testing of text extraction and chunking algorithms.
            The paragraph breaks and text flow are maintained exactly as in the source.
            
            Processing Benefits
            The document contains sufficient content for embedding generation
            and provides a good baseline for comparing extraction results
            across different document types and formats.
            
            Quality Assurance
            Plain text extraction serves as a control case for testing
            more complex document format processing capabilities.
            The simplicity allows for easy verification of extraction accuracy.
            
            End of extracted content from document %d.
            All original text content has been preserved without modification.
            """, title, index, index);
    }
    
    private static String generateHtmlExtractedText(String title, int index) {
        return String.format("""
            %s
            
            Content Overview
            This HTML document (index %d) demonstrates typical web content structure.
            All HTML tags have been removed, leaving only the textual content.
            
            Key Features
            Structured markup with headings
            Paragraphs and text formatting
            Lists and hierarchical content
            Semantic HTML elements
            
            Testing Objectives
            Verify HTML tag removal during extraction
            Ensure proper text structure preservation
            Test handling of special characters
            Validate content hierarchy maintenance
            
            Extracted Content Analysis
            The original HTML structure has been flattened into readable text.
            List items are converted to simple paragraphs or bullet points.
            Navigation elements and non-content markup have been filtered out.
            
            Text Processing Results
            Headers maintain their hierarchical importance through text flow.
            Links are preserved as text with URL information removed.
            Tables are linearized into readable text sequences.
            Form elements and interactive content are excluded.
            
            Quality Metrics
            Character encoding is preserved correctly
            Special symbols and Unicode characters are maintained
            Line breaks and paragraph separation follow logical structure
            Content completeness is verified against source
            
            Validation Summary
            This document provides comprehensive HTML content for testing
            Tika's ability to extract clean text from web-formatted documents.
            The extraction process successfully removes all markup while preserving content.
            
            Document %d - Extraction completed successfully
            All textual content has been preserved without HTML artifacts.
            """, title, index, index);
    }
    
    private static String generateCsvExtractedText(String title, int index) {
        return String.format("""
            %s
            Document Index: %d
            
            ID Name Category Value Description
            1 Sample Item A Category 1 100.50 First test item
            2 Sample Item B Category 2 200.75 Second test item  
            3 Sample Item C Category 1 150.25 Third test item
            4 Sample Item D Category 3 300.00 Fourth test item
            5 Sample Item E Category 2 75.50 Fifth test item
            6 Sample Item F Category 1 425.75 Sixth test item
            7 Sample Item G Category 3 50.00 Seventh test item
            8 Sample Item H Category 2 275.25 Eighth test item
            9 Sample Item I Category 1 325.50 Ninth test item
            10 Sample Item J Category 3 180.75 Tenth test item
            
            Summary Statistics
            Total Items: 10
            Categories: 3
            Document: %d
            
            Data Analysis Results
            The CSV file has been processed and converted to readable text format.
            Column headers and data relationships are preserved in the extracted content.
            Tabular structure is maintained through consistent spacing and organization.
            
            Content Verification
            All data values have been successfully extracted
            Column alignment is preserved where possible
            Special characters in data fields are handled correctly
            Row and column count matches source file specifications
            """, title, index, index);
    }
    
    private static String generateJsonExtractedText(String title, int index) {
        return String.format("""
            Document: %s
            Index: %d
            Type: json_sample
            
            Created: 2023-01-01T00:00:00Z
            Author: Test Generator
            Version: 1.0
            
            Content Sections:
            
            Section 1: Introduction
            This JSON document contains structured data for testing purposes.
            
            Section 2: Data Analysis
            JSON files require special handling to extract meaningful text content.
            
            Section 3: Test Results
            The extraction process should preserve the textual information while ignoring structure.
            
            Tags: json, test, sample, document
            
            Statistics:
            Word Count: 150
            Section Count: 3
            Tag Count: 4
            
            Generated For: tika_testing
            Document ID: %d
            
            Extraction Summary:
            JSON structure has been flattened into readable text format.
            Key-value pairs are presented as structured information.
            Nested objects are linearized with appropriate hierarchy indication.
            Arrays are converted to lists with clear item separation.
            """, title, index, index);
    }
    
    private static String generateXmlExtractedText(String title, int index) {
        return String.format("""
            %s
            Index: %d
            Type: xml_sample
            
            XML Document Structure
            This XML document demonstrates hierarchical content organization typical of structured documents.
            
            Content Extraction Testing
            XML files contain both structural markup and textual content that needs to be separated during processing.
            
            Processing Features:
            Tag structure preservation
            Text content extraction
            Attribute handling
            Hierarchy maintenance
            
            Processing Considerations
            The Tika parser should extract clean text while maintaining logical document structure.
            
            XML Processing Results:
            All XML tags have been removed from the content
            Text content is preserved with proper spacing
            Attribute values are extracted where relevant
            Document hierarchy is maintained through text organization
            
            Quality Assurance:
            Character encoding handled correctly
            Special XML entities are properly decoded
            Namespace information is processed appropriately
            Content completeness verified against source
            
            Generated: true
            Purpose: tika_testing
            Document ID: %d
            
            Extraction completed successfully with full content preservation.
            """, title, index, index);
    }
    
    private static String generateRtfExtractedText(String title, int index) {
        return String.format("""
            %s
            
            This Rich Text Format document (index %d) contains formatted content with various styling elements.
            All RTF formatting codes have been removed, leaving clean text content.
            
            Key Features:
            Text formatting and styling
            Font and size specifications
            Paragraph structures
            Special character handling
            
            Processing Results:
            The RTF format allows for rich text formatting while maintaining compatibility across different systems.
            All formatting control codes have been successfully removed during extraction.
            Text content is preserved with proper paragraph breaks and structure.
            
            Content Analysis:
            Bold and italic formatting indicators removed
            Font specifications converted to plain text
            Color and styling information filtered out
            Special characters properly decoded
            
            Document Structure:
            Headers and subheaders maintained through text flow
            Paragraph breaks preserved for readability
            List structures converted to simple text format
            Table content linearized appropriately
            
            This document provides comprehensive content for testing RTF parsing and text extraction capabilities.
            The extraction process successfully removes all formatting while preserving textual content.
            
            Validation Summary:
            Document ID: %d
            Processing Status: Complete
            Content Integrity: Verified
            Format Removal: Successful
            """, title, index, index);
    }
    
    private static String generateSpreadsheetExtractedText(String title, int index) {
        return String.format("""
            SPREADSHEET: %s
            INDEX: %d
            
            Sheet 1: Data Analysis
            Item Category Value Status
            Product A Electronics 299.99 Active
            Product B Books 19.95 Active  
            Product C Electronics 599.99 Discontinued
            Product D Clothing 79.50 Active
            Product E Books 24.99 Active
            
            Sheet 2: Summary Statistics
            Category Count Total Value
            Electronics 2 899.98
            Books 2 44.94
            Clothing 1 79.50
            
            Sheet 3: Notes
            This spreadsheet contains sample data for testing Excel/ODS file processing.
            The data includes multiple sheets with different types of content.
            Text extraction should preserve the tabular structure information.
            Document %d generated for Tika testing purposes.
            
            Extraction Summary:
            Multiple worksheets processed successfully
            Cell data extracted and organized by sheet
            Formulas converted to their calculated values
            Headers and data relationships preserved
            
            Data Quality Assessment:
            All numeric values preserved accurately
            Text content extracted without corruption
            Sheet organization maintained in output
            Cell formatting removed, content preserved
            """, title, index, index);
    }
    
    private static String generatePresentationExtractedText(String title, int index) {
        return String.format("""
            PRESENTATION: %s
            Document Index: %d
            
            Slide 1: Title Slide
            %s
            Document Index: %d
            
            Slide 2: Agenda
            Introduction and Overview
            Key Concepts and Definitions
            Detailed Analysis
            Recommendations
            Questions and Discussion
            
            Slide 3: Introduction
            This presentation demonstrates content typical of PowerPoint/ODP files.
            The material covers various topics relevant to document processing testing.
            
            Slide 4: Key Concepts
            Document Structure Analysis
            Content Extraction Methodologies  
            Text Processing Techniques
            Quality Assurance Measures
            
            Slide 5: Detailed Analysis
            PowerPoint presentations often contain:
            Bullet points and structured text
            Multiple slides with varied content
            Headers, footers, and slide numbers
            Mixed content types within slides
            
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
            
            Extraction Summary:
            All slide content successfully extracted
            Speaker notes included where present
            Slide transitions and animations removed
            Text hierarchy preserved through organization
            """, title, index, title, index, index);
    }
    
    private static String generateGenericExtractedText(String title, int index) {
        return String.format("""
            %s
            
            This is extracted text from a generic document (index %d) containing sample content for testing purposes.
            
            The document includes various types of text content to ensure comprehensive
            testing of document processing capabilities across different file formats.
            
            Content Features:
            Multiple paragraphs with varied text
            Structured information layout
            Descriptive and technical content
            Sufficient length for chunking tests
            
            Processing Results:
            This generic content serves as a fallback for document types that don't
            have specific content generation patterns defined.
            
            The text is designed to be representative of real-world documents
            while providing consistent testing data for the Tika processing pipeline.
            
            Quality Assurance:
            Content length appropriate for testing
            Text complexity suitable for NLP processing
            Structure maintains logical flow
            Language patterns support embedding generation
            
            Validation Complete:
            Document identifier: %d
            Processing status: Successful
            Content integrity: Verified
            Ready for downstream processing
            """, title, index, index);
    }
}