package com.rokkon.parser.comprehensive;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.test.data.TestDocumentFactory;
import com.rokkon.test.data.TikaTestDataHelper;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive test to verify that all available test documents are processed correctly
 * by the Quarkus ParserService, maintaining the same quality as the original implementation.
 * 
 * This test ensures that document processing quality is maintained during the migration.
 */
@QuarkusTest
public class ParserServiceComprehensiveTest {

    private static final Logger LOG = Logger.getLogger(ParserServiceComprehensiveTest.class);

    @GrpcClient
    PipeStepProcessor parserService;

    // Use direct instantiation pattern like chunker module (works better than CDI injection)
    private final TikaTestDataHelper tikaTestDataHelper = new TikaTestDataHelper();
    private final TestDocumentFactory documentFactory = new TestDocumentFactory();

    @Test
    public void testProcessAllAvailableDocumentsQuality() {
        LOG.info("=== Testing All Available Documents with Quarkus ParserService ===");

        // Load test documents from test-utilities
        List<PipeDoc> testDocs = documentFactory.loadTikaProcessedDocuments();
        LOG.info("Loaded " + testDocs.size() + " test documents for comprehensive testing");

        // Process configuration
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "1000000")
                .build();

        // Track success and failure counts
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Process each document
        for (PipeDoc testDoc : testDocs) {
            try {
                // Create unique metadata for each document
                ServiceMetadata metadata = ServiceMetadata.newBuilder()
                        .setPipelineName("comprehensive-test-pipeline")
                        .setPipeStepName("parser-comprehensive-test")
                        .setStreamId(UUID.randomUUID().toString())
                        .setCurrentHopNumber(1)
                        .build();

                ProcessRequest request = ProcessRequest.newBuilder()
                        .setDocument(testDoc)
                        .setConfig(config)
                        .setMetadata(metadata)
                        .build();

                // Process through ParserService
                ProcessResponse response = parserService.processData(request)
                        .subscribe().withSubscriber(UniAssertSubscriber.create())
                        .awaitItem()
                        .getItem();

                // Verify response
                if (response.getSuccess() && response.hasOutputDoc()) {
                    successCount.incrementAndGet();

                    // Verify document ID is preserved
                    PipeDoc resultDoc = response.getOutputDoc();
                    assertThat(resultDoc.getId()).isEqualTo(testDoc.getId());

                    // Verify document has content
                    assertThat(resultDoc.getBody()).isNotEmpty();

                    // Log progress every 10 documents
                    if (successCount.get() % 10 == 0) {
                        LOG.info("Successfully processed " + successCount.get() + " documents so far");
                    }
                } else {
                    failureCount.incrementAndGet();
                    LOG.warn("Failed to process document " + testDoc.getId() + ": " + 
                            response.getProcessorLogsList());
                }
            } catch (Exception e) {
                failureCount.incrementAndGet();
                LOG.error("Error processing document " + testDoc.getId() + ": " + e.getMessage());
            }
        }

        // Log final results
        LOG.info("✅ Comprehensive testing complete!");
        LOG.info("Total documents processed: " + testDocs.size());
        LOG.info("Successful: " + successCount.get());
        LOG.info("Failed: " + failureCount.get());

        // Assert high success rate (at least 90%)
        double successRate = (double) successCount.get() / testDocs.size();
        LOG.infof("Success rate: %.2f%%", successRate * 100);

        assertThat(successRate).isGreaterThanOrEqualTo(0.9)
                .withFailMessage("Expected success rate of at least 90%, but got %.2f%%", successRate * 100);
    }
}
