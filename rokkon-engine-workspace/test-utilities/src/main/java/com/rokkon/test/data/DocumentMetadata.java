package com.rokkon.test.data;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Represents metadata for a test document from the CSV file.
 */
public class DocumentMetadata {
    private final String filename;
    private final String title;
    private final String description;
    private final String author;
    private final LocalDate creationDate;
    private final LocalDate lastModifiedDate;
    private final String contentType;
    private final String language;
    private final List<String> keywords;
    private final String category;
    
    // Constructor with category
    public DocumentMetadata(String filename, String title, String description, String author, 
                           LocalDate creationDate, LocalDate lastModifiedDate, String contentType, 
                           String language, String keywords, String category) {
        this.filename = filename;
        this.title = title;
        this.description = description;
        this.author = author;
        this.creationDate = creationDate;
        this.lastModifiedDate = lastModifiedDate;
        this.contentType = contentType;
        this.language = language;
        this.keywords = Arrays.stream(keywords.split(","))
                .map(String::trim)
                .toList();
        this.category = category;
    }
    
    // Backward compatibility constructor (defaults category to "unknown")
    public DocumentMetadata(String filename, String title, String description, String author, 
                           LocalDate creationDate, LocalDate lastModifiedDate, String contentType, 
                           String language, String keywords) {
        this(filename, title, description, author, creationDate, lastModifiedDate, 
             contentType, language, keywords, "unknown");
    }
    
    public String getFilename() { return filename; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
    public LocalDate getCreationDate() { return creationDate; }
    public LocalDate getLastModifiedDate() { return lastModifiedDate; }
    public String getContentType() { return contentType; }
    public String getLanguage() { return language; }
    public String getCategory() { return category; }
    
    public List<String> getKeywordsList() { return keywords; }
    
    public String getKeywords() {
        return String.join(",", keywords);
    }
    
    /**
     * Creates a variation of this document metadata with modified fields for test diversity.
     */
    public DocumentMetadata createVariation(int variationNumber) {
        String variantFilename = filename.replaceFirst("\\.", "_v" + variationNumber + ".");
        String variantTitle = title + " (Variant " + variationNumber + ")";
        String variantDescription = description + " - Test variation " + variationNumber;
        
        // Vary the creation date slightly
        LocalDate variantCreationDate = creationDate.plusDays(variationNumber);
        LocalDate variantModifiedDate = lastModifiedDate.plusDays(variationNumber);
        
        return new DocumentMetadata(
            variantFilename, variantTitle, variantDescription, author,
            variantCreationDate, variantModifiedDate, contentType, language,
            String.join(",", keywords), category
        );
    }
}