package com.rokkon.test.data;

import com.rokkon.search.model.PipeDoc;
import java.io.File;
import java.io.FileInputStream;

/**
 * Simple investigation of test documents to understand their structure.
 */
public class SimpleTestDocumentInvestigator {
    
    public static void main(String[] args) {
        try {
            // Look at the first .bin file
            File directory = new File("modules/tika-parser/src/test/resources/test-data/sample-documents-pipe-docs");
            File[] binFiles = directory.listFiles((dir, name) -> name.endsWith(".bin"));
            
            if (binFiles != null && binFiles.length > 0) {
                File firstFile = binFiles[0];
                System.out.println("Analyzing: " + firstFile.getName());
                
                try (FileInputStream fis = new FileInputStream(firstFile)) {
                    PipeDoc doc = PipeDoc.parseFrom(fis);
                    
                    System.out.println("ID: " + doc.getId());
                    System.out.println("Has title: " + doc.hasTitle());
                    System.out.println("Has body: " + doc.hasBody());
                    if (doc.hasBody()) {
                        System.out.println("Body length: " + doc.getBody().length());
                        System.out.println("Body preview: " + doc.getBody().substring(0, Math.min(200, doc.getBody().length())));
                    }
                    System.out.println("Has blob: " + doc.hasBlob());
                    System.out.println("Semantic results: " + doc.getSemanticResultsCount());
                    
                    // This tells us what stage the document is in
                    if (doc.hasBlob() && (!doc.hasBody() || doc.getBody().trim().isEmpty())) {
                        System.out.println("Stage: RAW (needs Tika processing)");
                    } else if (doc.hasBody() && doc.getSemanticResultsCount() == 0) {
                        System.out.println("Stage: TIKA_PROCESSED (needs chunking)");
                    } else if (doc.getSemanticResultsCount() > 0) {
                        System.out.println("Stage: CHUNKED (ready for embedding)");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}