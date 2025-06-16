package com.rokkon.modules.tika;

import com.rokkon.modules.tika.util.TestDataLoader;
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
 * Comprehensive test to verify that all 99 test documents are processed correctly
 * by the Quarkus TikaService, maintaining the same quality as the Micronaut version.
 * 
 * This test ensures that document processing quality is maintained during the migration.
 */
@QuarkusTest
public class TikaService99DocumentsTest {

    @Inject
    @GrpcService
    TikaService tikaService;

    @Test
    public void testProcess99DocumentsQuality() {
        System.out.println("=== Testing 99 Documents with Quarkus TikaService ===\n");
        
        // Load all test documents
        List<PipeDoc> documents = TestDataLoader.loadAllTikaTestDocuments();
        System.out.println("Loaded " + documents.size() + " test documents\n");
        
        assertTrue(documents.size() >= 99, 
            "Should have at least 99 documents, but found: " + documents.size());
        
        // Counters for analysis
        AtomicInteger docsWithBodyBefore = new AtomicInteger(0);
        AtomicInteger docsWithBodyAfter = new AtomicInteger(0);
        AtomicInteger docsWithoutBodyAfter = new AtomicInteger(0);
        AtomicInteger successfulProcessing = new AtomicInteger(0);
        AtomicInteger failedProcessing = new AtomicInteger(0);
        AtomicInteger totalBodyCharsAfter = new AtomicInteger(0);
        AtomicInteger minBodyLength = new AtomicInteger(Integer.MAX_VALUE);
        AtomicInteger maxBodyLength = new AtomicInteger(0);
        
        // Process each document through TikaService
        for (int i = 0; i < Math.min(documents.size(), 100); i++) {
            PipeDoc originalDoc = documents.get(i);
            
            // Check if original document has body
            boolean hadBodyBefore = originalDoc.getBody() != null && !originalDoc.getBody().isEmpty();
            if (hadBodyBefore) {
                docsWithBodyBefore.incrementAndGet();
            }
            
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
            assertNotNull(response, "Response should not be null for document " + i);
            
            if (response.getSuccess()) {
                successfulProcessing.incrementAndGet();
                
                PipeDoc resultDoc = response.getOutputDoc();
                boolean hasBodyAfter = resultDoc.getBody() != null && !resultDoc.getBody().isEmpty();
                
                if (hasBodyAfter) {
                    docsWithBodyAfter.incrementAndGet();
                    int bodyLength = resultDoc.getBody().length();
                    totalBodyCharsAfter.addAndGet(bodyLength);
                    minBodyLength.set(Math.min(minBodyLength.get(), bodyLength));
                    maxBodyLength.set(Math.max(maxBodyLength.get(), bodyLength));
                } else {
                    docsWithoutBodyAfter.incrementAndGet();
                }
                
                // Print details for first/last few documents
                if (i < 5 || i >= 95) {
                    System.out.printf("Document %02d: ID=%-20s Success=%s Body=%d chars (was %d)%n", 
                        i,
                        originalDoc.getId().length() > 20 ? originalDoc.getId().substring(0, 17) + "..." : originalDoc.getId(),
                        response.getSuccess(),
                        hasBodyAfter ? resultDoc.getBody().length() : 0,
                        hadBodyBefore ? originalDoc.getBody().length() : 0);
                } else if (i == 5) {
                    System.out.println("... (documents 5-94 processing...) ...");
                }
                
            } else {
                failedProcessing.incrementAndGet();
                System.err.printf("Document %02d FAILED: %s%n", i, 
                    response.hasErrorDetails() ? response.getErrorDetails().toString() : "Unknown error");
            }
        }
        
        // Print comprehensive analysis
        System.out.println("\n=== Processing Quality Analysis ===");
        System.out.println("Total documents processed: " + Math.min(documents.size(), 100));
        System.out.println("Successful processing: " + successfulProcessing.get());
        System.out.println("Failed processing: " + failedProcessing.get());
        
        System.out.println("\n=== Body Content Analysis ===");
        System.out.println("Documents with body BEFORE: " + docsWithBodyBefore.get());
        System.out.println("Documents with body AFTER: " + docsWithBodyAfter.get());
        System.out.println("Documents without body AFTER: " + docsWithoutBodyAfter.get());
        
        if (docsWithBodyAfter.get() > 0) {
            System.out.println("Average body length: " + (totalBodyCharsAfter.get() / docsWithBodyAfter.get()) + " chars");
            System.out.println("Min body length: " + (minBodyLength.get() == Integer.MAX_VALUE ? 0 : minBodyLength.get()) + " chars");
            System.out.println("Max body length: " + maxBodyLength.get() + " chars");
        }
        
        // Quality assertions based on original Micronaut expectations
        System.out.println("\n=== Quality Assertions ===");
        
        // At least 95% processing success rate
        double successRate = (double) successfulProcessing.get() / Math.min(documents.size(), 100);
        System.out.println("Processing success rate: " + String.format("%.1f%%", successRate * 100));
        assertTrue(successRate >= 0.95, 
            "Processing success rate should be >= 95%, but was: " + String.format("%.1f%%", successRate * 100));
        
        // Most documents should have body content after processing
        double bodyContentRate = (double) docsWithBodyAfter.get() / successfulProcessing.get();
        System.out.println("Body content extraction rate: " + String.format("%.1f%%", bodyContentRate * 100));
        assertTrue(bodyContentRate >= 0.90, 
            "Body content extraction rate should be >= 90%, but was: " + String.format("%.1f%%", bodyContentRate * 100));
        
        // Verify we're not losing documents that previously had content
        // (Documents that had body before should still have body after, or at least most of them)
        if (docsWithBodyBefore.get() > 0) {
            double retentionRate = (double) docsWithBodyAfter.get() / docsWithBodyBefore.get();
            System.out.println("Content retention rate: " + String.format("%.1f%%", retentionRate * 100));
            assertTrue(retentionRate >= 0.85, 
                "Should retain content from at least 85% of documents that previously had content, but was: " + 
                String.format("%.1f%%", retentionRate * 100));
        }
        
        System.out.println("\n✅ All quality assertions passed!");
        System.out.println("✅ Quarkus TikaService maintains expected document processing quality!");
    }
}