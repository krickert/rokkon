package com.rokkon.modules.tika;

import com.rokkon.modules.tika.util.TestDataLoader;
import com.rokkon.modules.tika.util.DocumentTestDataCreator;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ServiceMetadata;
import io.smallrye.mutiny.Uni;
import io.quarkus.grpc.GrpcService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test to verify that document processing quality is maintained
 * during the Micronaut to Quarkus migration.
 * 
 * This test has two parts:
 * 1. Verify that the 99 reference documents maintain expected text quality
 * 2. Test actual Tika processing with real document files
 */
@QuarkusTest
public class TikaService99DocumentsQualityTest {

    @Inject
    @GrpcService
    TikaService tikaService;

    @Test
    public void verifyReference99DocumentsTextQuality() {
        System.out.println("=== Verifying 99 Reference Documents Text Quality ===\n");
        
        // Load the 99 reference documents (these already have text content)
        List<PipeDoc> documents = TestDataLoader.loadAllTikaTestDocuments();
        System.out.println("Loaded " + documents.size() + " reference documents\n");
        
        assertTrue(documents.size() >= 99, 
            "Should have at least 99 documents, but found: " + documents.size());
        
        // Analyze text quality of the reference documents
        int docsWithBody = 0;
        int docsWithoutBody = 0;
        int totalBodyChars = 0;
        int minBodyLength = Integer.MAX_VALUE;
        int maxBodyLength = 0;
        
        for (int i = 0; i < Math.min(documents.size(), 100); i++) {
            PipeDoc doc = documents.get(i);
            boolean hasBody = doc.getBody() != null && !doc.getBody().isEmpty();
            
            if (hasBody) {
                docsWithBody++;
                int bodyLength = doc.getBody().length();
                totalBodyChars += bodyLength;
                minBodyLength = Math.min(minBodyLength, bodyLength);
                maxBodyLength = Math.max(maxBodyLength, bodyLength);
            } else {
                docsWithoutBody++;
            }
            
            // Print details for first/last few documents
            if (i < 5 || i >= 95) {
                System.out.printf("Document %02d: ID=%-20s Body=%d chars%n", 
                    i,
                    doc.getId().length() > 20 ? doc.getId().substring(0, 17) + "..." : doc.getId(),
                    hasBody ? doc.getBody().length() : 0);
            } else if (i == 5) {
                System.out.println("... (documents 5-94 processed) ...");
            }
        }
        
        // Print analysis
        System.out.println("\n=== Reference Documents Quality Analysis ===");
        System.out.println("Total documents analyzed: " + Math.min(documents.size(), 100));
        System.out.println("Documents with body content: " + docsWithBody);
        System.out.println("Documents without body content: " + docsWithoutBody);
        
        if (docsWithBody > 0) {
            System.out.println("Average body length: " + (totalBodyChars / docsWithBody) + " chars");
            System.out.println("Min body length: " + (minBodyLength == Integer.MAX_VALUE ? 0 : minBodyLength) + " chars");
            System.out.println("Max body length: " + maxBodyLength + " chars");
        }
        
        // Quality expectations based on Micronaut version
        System.out.println("\n=== Quality Assertions ===");
        
        // Most documents should have body content (as they did in Micronaut version)
        double bodyContentRate = (double) docsWithBody / Math.min(documents.size(), 100);
        System.out.println("Body content rate: " + String.format("%.1f%%", bodyContentRate * 100));
        assertTrue(bodyContentRate >= 0.90, 
            "Body content rate should be >= 90% (like Micronaut version), but was: " + 
            String.format("%.1f%%", bodyContentRate * 100));
        
        // Average body length should be reasonable
        if (docsWithBody > 0) {
            int avgBodyLength = totalBodyChars / docsWithBody;
            System.out.println("Average body length: " + avgBodyLength + " chars");
            assertTrue(avgBodyLength >= 1000, 
                "Average body length should be >= 1000 chars, but was: " + avgBodyLength);
        }
        
        System.out.println("\n✅ Reference documents maintain expected text quality!");
    }

    @Test
    public void testActualTikaProcessingWithRealDocuments() {
        System.out.println("=== Testing Actual Tika Processing with Real Documents ===\n");
        
        // Create test documents with actual blob data
        List<PipeDoc> documents = DocumentTestDataCreator.createTestDocumentsWithBlobData();
        System.out.println("Created " + documents.size() + " test documents with blob data\n");
        
        assertTrue(documents.size() >= 5, 
            "Should have at least 5 test documents, but found: " + documents.size());
        
        // Counters for analysis
        AtomicInteger successfulProcessing = new AtomicInteger(0);
        AtomicInteger failedProcessing = new AtomicInteger(0);
        AtomicInteger docsWithExtractedText = new AtomicInteger(0);
        AtomicInteger totalExtractedChars = new AtomicInteger(0);
        
        // Process each document through TikaService
        for (int i = 0; i < documents.size(); i++) {
            PipeDoc originalDoc = documents.get(i);
            
            System.out.printf("Processing document %d: %s (%d bytes)%n", 
                i, originalDoc.getBlob().getFilename(), originalDoc.getBlob().getData().size());
            
            // Create process request
            ProcessConfiguration config = ProcessConfiguration.newBuilder()
                    .putConfigParams("extractMetadata", "true")
                    .putConfigParams("maxContentLength", "1000000")
                    .build();
            
            ServiceMetadata metadata = ServiceMetadata.newBuilder()
                    .setPipelineName("quality-test-pipeline")
                    .setPipeStepName("tika-quality-test")
                    .setStreamId("quality-test-stream-" + i)
                    .setCurrentHopNumber(1)
                    .build();
            
            ProcessRequest request = ProcessRequest.newBuilder()
                    .setDocument(originalDoc)
                    .setConfig(config)
                    .setMetadata(metadata)
                    .build();
            
            // Process through TikaService using reactive pattern
            Uni<ProcessResponse> responseUni = tikaService.processData(request);
            ProcessResponse response = responseUni.await().indefinitely();
            
            if (response != null && response.getSuccess()) {
                successfulProcessing.incrementAndGet();
                
                PipeDoc resultDoc = response.getOutputDoc();
                boolean hasExtractedText = resultDoc.getBody() != null && !resultDoc.getBody().isEmpty();
                
                if (hasExtractedText) {
                    docsWithExtractedText.incrementAndGet();
                    totalExtractedChars.addAndGet(resultDoc.getBody().length());
                    
                    System.out.printf("  ✅ SUCCESS: Extracted %d chars%n", resultDoc.getBody().length());
                } else {
                    System.out.printf("  ⚠️ SUCCESS but no text extracted%n");
                }
                
            } else {
                failedProcessing.incrementAndGet();
                String errorMsg = response != null && response.hasErrorDetails() ? response.getErrorDetails().toString() : "Unknown error";
                System.out.printf("  ❌ FAILED: %s%n", errorMsg);
            }
        }
        
        // Print comprehensive analysis
        System.out.println("\n=== Tika Processing Quality Analysis ===");
        System.out.println("Total documents processed: " + documents.size());
        System.out.println("Successful processing: " + successfulProcessing.get());
        System.out.println("Failed processing: " + failedProcessing.get());
        System.out.println("Documents with extracted text: " + docsWithExtractedText.get());
        
        if (docsWithExtractedText.get() > 0) {
            System.out.println("Average extracted text length: " + 
                (totalExtractedChars.get() / docsWithExtractedText.get()) + " chars");
        }
        
        // Quality assertions for actual Tika processing
        System.out.println("\n=== Tika Processing Quality Assertions ===");
        
        // At least 80% processing success rate
        double successRate = (double) successfulProcessing.get() / documents.size();
        System.out.println("Processing success rate: " + String.format("%.1f%%", successRate * 100));
        assertTrue(successRate >= 0.80, 
            "Processing success rate should be >= 80%, but was: " + String.format("%.1f%%", successRate * 100));
        
        // Most successful documents should have extracted text
        if (successfulProcessing.get() > 0) {
            double textExtractionRate = (double) docsWithExtractedText.get() / successfulProcessing.get();
            System.out.println("Text extraction rate: " + String.format("%.1f%%", textExtractionRate * 100));
            assertTrue(textExtractionRate >= 0.70, 
                "Text extraction rate should be >= 70%, but was: " + String.format("%.1f%%", textExtractionRate * 100));
        }
        
        System.out.println("\n✅ Quarkus TikaService successfully processes real documents!");
        System.out.println("✅ Document processing quality is maintained during migration!");
    }
}