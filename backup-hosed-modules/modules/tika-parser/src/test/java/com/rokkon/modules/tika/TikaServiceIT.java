package com.rokkon.modules.tika;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.MutinyPipeStepProcessorGrpc;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.test.data.TestDocumentFactory;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tier 2: Correctly implemented integration tests for Tika service.
 *
 * This test demonstrates the correct pattern for @QuarkusIntegrationTest:
 * 1. The @QuarkusTestResource annotation hooks into the test lifecycle.
 * 2. The GrpcPortResource starts, waits for the application to start,
 * and then injects the application's random gRPC port into our test.
 * 3. The test uses this injected port to create a gRPC client and connect.
 */
@QuarkusTestResource(GrpcPortResource.class) // Use our custom "bridge" to get the port
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TikaServiceIT {

    private static final Logger LOG = LoggerFactory.getLogger(TikaServiceIT.class);

    // This field will be populated by our GrpcPortResource before any tests run.
    @InjectGrpcPort
    static int grpcPort;

    static ManagedChannel channel;
    static MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub tikaClient;

    // In integration tests, we can't use @Inject, so we create instances directly.
    static final TestDocumentFactory documentFactory = new TestDocumentFactory();

    @BeforeAll
    static void setup() {
        // By the time @BeforeAll runs, 'grpcPort' has its correct value.
        LOG.info("Integration test starting. Will connect to gRPC server on port: {}", grpcPort);

        channel = ManagedChannelBuilder.forAddress("localhost", grpcPort)
                .usePlaintext()
                .build();

        tikaClient = MutinyPipeStepProcessorGrpc.newMutinyStub(channel);
    }

    @AfterAll
    static void cleanup() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @Order(1)
    @DisplayName("Basic Connectivity - Should connect to Tika service")
    void testBasicConnectivity() {
        LOG.info("Testing basic connectivity to Tika service on port {}", grpcPort);

        PipeDoc simpleDoc = documentFactory.createSimpleTextDocument("connectivity-test", "Connectivity Test", "Simple test content");
        ProcessRequest request = createBasicProcessRequest(simpleDoc);

        ProcessResponse response = tikaClient.processData(request)
                .await().atMost(Duration.ofSeconds(15));

        assertNotNull(response, "Should receive a response from Tika service");
        LOG.info("✅ Basic connectivity test passed");
    }

    @Test
    @Order(2)
    @DisplayName("Tika Text Extraction - Should extract text from documents with blobs")
    void testTikaTextExtraction() {
        LOG.info("Testing Tika-specific text extraction capabilities");

        String sampleText = "This is a sample document for Tika text extraction. " +
                "It contains multiple sentences and should be processed by Tika to extract text content.";
        PipeDoc docWithBlob = documentFactory.createDocumentWithBlob(
                "tika-extraction-test",
                sampleText.getBytes(),
                "text/plain",
                "test.txt"
        );

        ProcessRequest request = createBasicProcessRequest(docWithBlob);
        ProcessResponse response = tikaClient.processData(request)
                .await().atMost(Duration.ofSeconds(30));

        assertTrue(response.getSuccess(), "Tika text extraction should succeed");
        assertNotNull(response.getOutputDoc().getBody(), "Extracted text should be present");
        assertTrue(response.getOutputDoc().getBody().contains("sample document"),
                "Extracted text should contain original content");
        assertTrue(response.getOutputDoc().hasBlob(), "Original blob should be preserved");

        LOG.info("✅ Tika text extraction test completed");
    }

    @Test
    @Order(3)
    @DisplayName("Multiple Document Formats - Should handle various file types")
    void testMultipleDocumentFormats() {
        LOG.info("Testing multiple document formats with blob data");

        List<PipeDoc> rawDocuments = documentFactory.createRawDocumentsWithBlobData();
        assertTrue(rawDocuments.size() > 0, "Should have raw documents with blob data");

        int successCount = 0;
        int maxTestDocs = Math.min(5, rawDocuments.size()); // Fewer docs for integration test

        for (int i = 0; i < maxTestDocs; i++) {
            PipeDoc rawDoc = rawDocuments.get(i);

            ProcessConfiguration config = ProcessConfiguration.newBuilder()
                    .putConfigParams("extractMetadata", "true")
                    .build();

            ProcessRequest request = createProcessRequest("tika-format-" + i, rawDoc, config);

            try {
                ProcessResponse response = tikaClient.processData(request)
                        .await().atMost(Duration.ofSeconds(30));

                if (response.getSuccess()) {
                    successCount++;
                } else {
                    LOG.warn("⚠️ Failed to process document: {}", rawDoc.getId());
                }
            } catch (Exception e) {
                LOG.warn("Document processing failed for {}: {}", rawDoc.getId(), e.getMessage());
            }
        }

        double successRate = (double) successCount / maxTestDocs;
        LOG.info("Text extraction success rate: {}/{} ({:.1f}%)",
                successCount, maxTestDocs, successRate * 100);

        assertTrue(successRate >= 0.5,
                "Should successfully extract text from at least 50% of documents");

        LOG.info("✅ Multiple document formats test completed");
    }

    @Test
    @Order(4)
    @DisplayName("Large Document Processing - Should handle large documents efficiently")
    void testLargeDocumentProcessing() {
        LOG.info("Testing Tika with large document content");

        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 500; i++) { // Smaller for integration test
            largeContent.append("Large document test paragraph ").append(i).append(". ");
        }

        PipeDoc largeDoc = documentFactory.createDocumentWithBlob(
                "large-doc-test",
                largeContent.toString().getBytes(),
                "text/plain",
                "large-test.txt"
        );

        ProcessRequest request = createProcessRequest("tika-large", largeDoc, ProcessConfiguration.getDefaultInstance());

        long startTime = System.currentTimeMillis();
        ProcessResponse response = tikaClient.processData(request)
                .await().atMost(Duration.ofSeconds(60));
        long processingTime = System.currentTimeMillis() - startTime;

        assertTrue(response.getSuccess(), "Large document processing should succeed");
        assertNotNull(response.getOutputDoc().getBody(), "Large document should have extracted content");
        assertTrue(response.getOutputDoc().getBody().length() > 5000, "Should extract substantial content");

        LOG.info("✅ Large document processed successfully in {}ms", processingTime);
    }

    // Helper methods to create requests
    private ProcessRequest createBasicProcessRequest(PipeDoc doc) {
        return ProcessRequest.newBuilder()
                .setDocument(doc)
                .build();
    }

    private ProcessRequest createProcessRequest(String docId, PipeDoc doc, ProcessConfiguration config) {
        PipeDoc docWithId = doc.toBuilder().setId(docId).build();
        return ProcessRequest.newBuilder()
                .setDocument(docWithId)
                .setConfig(config)
                .build();
    }
}