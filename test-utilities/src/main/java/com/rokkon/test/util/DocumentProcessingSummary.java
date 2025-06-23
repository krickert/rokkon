package com.rokkon.test.util;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.pipeline.utils.ProcessingBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for generating document processing summaries from test runs.
 * This helps analyze test results and identify patterns in document processing.
 */
public class DocumentProcessingSummary {
    private static final Logger LOG = LoggerFactory.getLogger(DocumentProcessingSummary.class);
    
    /**
     * Generates a comprehensive summary of document processing results.
     * 
     * @param outputBuffer The buffer containing successfully processed documents
     * @param failureCount The number of documents that failed to process
     * @param failedDocuments List of failed document identifiers and filenames
     */
    public static void generateSummary(ProcessingBuffer<PipeDoc> outputBuffer, 
                                     int failureCount, 
                                     List<String> failedDocuments) {
        LOG.info("\n=== Document Processing Summary ===");
        LOG.info("Total documents processed: {}", outputBuffer.size() + failureCount);
        LOG.info("Successfully parsed: {}", outputBuffer.size());
        LOG.info("Failed to parse: {}", failureCount);
        
        if (failureCount > 0) {
            LOG.info("\n--- Failed Documents ---");
            for (String failedDoc : failedDocuments) {
                LOG.info("  {}", failedDoc);
            }
        }
        
        if (outputBuffer.size() > 0) {
            analyzeDocumentMetadata(outputBuffer);
        }
        
        LOG.info("\n=== End Summary ===\n");
    }
    
    /**
     * Analyzes metadata from successfully processed documents.
     */
    private static void analyzeDocumentMetadata(ProcessingBuffer<PipeDoc> outputBuffer) {
        Map<String, Integer> contentTypes = new HashMap<>();
        Map<String, Integer> fileTypes = new HashMap<>();
        long totalBodyLength = 0;
        int documentsWithTitle = 0;
        int documentsWithMetadata = 0;
        
        for (PipeDoc doc : outputBuffer.snapshot()) {
            // Body length
            totalBodyLength += doc.getBody().length();
            
            // Title
            if (!doc.getTitle().isEmpty()) {
                documentsWithTitle++;
            }
            
            // Metadata analysis
            if (doc.hasCustomData() && doc.getCustomData().getFieldsCount() > 0) {
                documentsWithMetadata++;
                
                // Content type
                if (doc.getCustomData().getFieldsMap().containsKey("Content-Type")) {
                    String contentType = doc.getCustomData().getFieldsMap().get("Content-Type").getStringValue();
                    contentTypes.merge(contentType, 1, Integer::sum);
                }
                
                // File extension from resourceName
                if (doc.getCustomData().getFieldsMap().containsKey("resourceName")) {
                    String filename = doc.getCustomData().getFieldsMap().get("resourceName").getStringValue();
                    if (filename.contains(".")) {
                        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
                        fileTypes.merge(ext, 1, Integer::sum);
                    }
                }
            }
        }
        
        LOG.info("\n--- Document Statistics ---");
        LOG.info("Documents with title: {} ({}%)", documentsWithTitle, 
                (documentsWithTitle * 100) / outputBuffer.size());
        LOG.info("Documents with metadata: {} ({}%)", documentsWithMetadata,
                (documentsWithMetadata * 100) / outputBuffer.size());
        LOG.info("Average body length: {} characters", totalBodyLength / outputBuffer.size());
        
        LOG.info("\n--- Content Types ---");
        contentTypes.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> LOG.info("  {}: {}", entry.getKey(), entry.getValue()));
        
        LOG.info("\n--- File Types ---");
        fileTypes.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> LOG.info("  .{}: {}", entry.getKey(), entry.getValue()));
    }
    
    /**
     * Generates a summary without failure details (for successful runs).
     */
    public static void generateSummary(ProcessingBuffer<PipeDoc> outputBuffer) {
        generateSummary(outputBuffer, 0, List.of());
    }
}