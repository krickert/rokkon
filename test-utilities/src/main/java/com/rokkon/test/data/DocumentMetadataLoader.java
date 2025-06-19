package com.rokkon.test.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads document metadata from CSV file and generates variations to create 99 test documents.
 */
public class DocumentMetadataLoader {
    private static final Logger LOG = LoggerFactory.getLogger(DocumentMetadataLoader.class);
    
    private static final String METADATA_CSV_PATH = "/test-data/document-metadata.csv";
    
    /**
     * Loads document metadata from CSV and generates many entries by creating variations.
     * With our current set of documents, this will generate 100+ documents.
     */
    public static List<DocumentMetadata> load99DocumentMetadata() {
        return loadExpandedMetadata(5); // 5 variations per base document
    }
    
    /**
     * Loads document metadata with specified number of variations per base document.
     * This generates: baseCount * (1 + variationsPerDocument) total documents.
     */
    public static List<DocumentMetadata> loadExpandedMetadata(int variationsPerDocument) {
        List<DocumentMetadata> baseMetadata = loadBaseMetadata();
        List<DocumentMetadata> expanded = new ArrayList<>();
        
        // Add all base documents first
        expanded.addAll(baseMetadata);
        
        // Generate variations for each base document
        for (int variationNum = 1; variationNum <= variationsPerDocument; variationNum++) {
            for (DocumentMetadata base : baseMetadata) {
                DocumentMetadata variation = base.createVariation(variationNum);
                expanded.add(variation);
            }
        }
        
        LOG.info("Generated {} document metadata entries from {} base documents ({} variations each)", 
            expanded.size(), baseMetadata.size(), variationsPerDocument);
        return expanded;
    }
    
    /**
     * Loads the base document metadata from the CSV file.
     */
    private static List<DocumentMetadata> loadBaseMetadata() {
        List<DocumentMetadata> metadata = new ArrayList<>();
        
        try (InputStream is = DocumentMetadataLoader.class.getResourceAsStream(METADATA_CSV_PATH);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            
            String line;
            boolean isHeader = true;
            
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue; // Skip header row
                }
                
                DocumentMetadata doc = parseMetadataLine(line);
                if (doc != null) {
                    metadata.add(doc);
                }
            }
            
        } catch (IOException e) {
            LOG.error("Failed to load document metadata from {}", METADATA_CSV_PATH, e);
            throw new RuntimeException("Could not load document metadata", e);
        } catch (Exception e) {
            LOG.error("Error parsing document metadata", e);
            throw new RuntimeException("Could not parse document metadata", e);
        }
        
        LOG.info("Loaded {} base document metadata entries", metadata.size());
        return metadata;
    }
    
    /**
     * Loads all base document metadata without variations.
     */
    public static List<DocumentMetadata> loadAllBaseMetadata() {
        return loadBaseMetadata();
    }
    
    /**
     * Loads documents filtered by category.
     */
    public static List<DocumentMetadata> loadDocumentsByCategory(String category) {
        return loadBaseMetadata().stream()
            .filter(doc -> category.equals(doc.getCategory()))
            .toList();
    }
    
    /**
     * Loads documents filtered by content type (MIME type).
     */
    public static List<DocumentMetadata> loadDocumentsByContentType(String contentType) {
        return loadBaseMetadata().stream()
            .filter(doc -> contentType.equals(doc.getContentType()))
            .toList();
    }
    
    /**
     * Loads documents filtered by keywords (contains any of the specified keywords).
     */
    public static List<DocumentMetadata> loadDocumentsByKeywords(String... keywords) {
        return loadBaseMetadata().stream()
            .filter(doc -> {
                String docKeywords = doc.getKeywords().toLowerCase();
                for (String keyword : keywords) {
                    if (docKeywords.contains(keyword.toLowerCase())) {
                        return true;
                    }
                }
                return false;
            })
            .toList();
    }
    
    /**
     * Helper methods for common document types
     */
    public static List<DocumentMetadata> getCsvTestDocuments() {
        return loadDocumentsByContentType("text/csv");
    }
    
    public static List<DocumentMetadata> getExcelTestDocuments() {
        return loadDocumentsByKeywords("excel", "xls", "xlsx");
    }
    
    public static List<DocumentMetadata> getPdfTestDocuments() {
        return loadDocumentsByContentType("application/pdf");
    }
    
    public static List<DocumentMetadata> getWordTestDocuments() {
        return loadDocumentsByKeywords("doc", "docx", "word");
    }
    
    public static List<DocumentMetadata> getPowerPointTestDocuments() {
        return loadDocumentsByKeywords("ppt", "pptx", "presentation");
    }
    
    public static List<DocumentMetadata> getJavaSourceCodeDocuments() {
        return loadDocumentsByContentType("text/x-java-source");
    }
    
    public static List<DocumentMetadata> getSourceCodeDocuments() {
        return loadDocumentsByCategory("source-code");
    }
    
    public static List<DocumentMetadata> getStandardDocuments() {
        return loadDocumentsByCategory("documents");
    }
    
    public static List<DocumentMetadata> getLargeDocuments() {
        return loadDocumentsByKeywords("large");
    }
    
    public static List<DocumentMetadata> getMediumDocuments() {
        return loadDocumentsByKeywords("medium");
    }
    
    public static List<DocumentMetadata> getSmallDocuments() {
        return loadDocumentsByKeywords("small");
    }
    
    /**
     * Parses a single line of CSV data into DocumentMetadata.
     */
    private static DocumentMetadata parseMetadataLine(String line) {
        try {
            // Use proper CSV parsing that handles quoted fields
            List<String> parts = parseCsvLine(line);
            
            if (parts.size() < 10) { // Updated to expect 10 fields including category
                LOG.warn("Skipping malformed CSV line: {}", line);
                return null;
            }
            
            String filename = parts.get(0).trim();
            String title = parts.get(1).trim();
            String description = parts.get(2).trim();
            String author = parts.get(3).trim();
            LocalDate creationDate = LocalDate.parse(parts.get(4).trim());
            LocalDate lastModifiedDate = LocalDate.parse(parts.get(5).trim());
            String contentType = parts.get(6).trim();
            String language = parts.get(7).trim();
            String keywords = parts.get(8).trim();
            String category = parts.size() > 9 ? parts.get(9).trim() : "unknown"; // Default category
            
            return new DocumentMetadata(filename, title, description, author,
                creationDate, lastModifiedDate, contentType, language, keywords, category);
                
        } catch (Exception e) {
            LOG.warn("Failed to parse CSV line: {} - {}", line, e.getMessage());
            return null;
        }
    }
    
    /**
     * Simple CSV line parser that handles quoted fields containing commas.
     */
    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes; // Toggle quote state
            } else if (c == ',' && !inQuotes) {
                result.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        // Add the last field
        result.add(currentField.toString());
        
        return result;
    }
}