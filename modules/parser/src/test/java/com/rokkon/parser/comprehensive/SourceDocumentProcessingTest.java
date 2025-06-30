package com.rokkon.parser.comprehensive;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.pipeline.util.ProcessingBuffer;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.test.data.ProtobufTestDataHelper;
// import com.rokkon.test.util.DocumentProcessingSummary; // TODO: Fix static method issue with Quarkus
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Test for processing source documents through the parser.
 * This test loads the 126 source documents, processes them through the parser service,
 * and captures both input and output in separate buffers.
 * 
 * To run this test and generate test data:
 * ./gradlew test -Dtest=SourceDocumentProcessingTest
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SourceDocumentProcessingTest {
    private static final Logger LOG = LoggerFactory.getLogger(SourceDocumentProcessingTest.class);

    @Inject
    @GrpcClient
    PipeStepProcessor parserService;

    @Inject
    @Named("outputBuffer")
    ProcessingBuffer<PipeDoc> outputBuffer;

    @Inject
    @Named("inputBuffer")
    ProcessingBuffer<PipeStream> inputBuffer;

    private ProtobufTestDataHelper testDataHelper;

    @BeforeAll
    public void setup() {
        LOG.info("Output buffer enabled: {}", outputBuffer.size() >= 0);
        LOG.info("Input buffer enabled: {}", inputBuffer.size() >= 0);
        
        // Create test data helper manually
        testDataHelper = new ProtobufTestDataHelper();
    }

    @Test
    public void processSourceDocumentsAndGenerateTestData() throws Exception {
        // Get the Tika request streams (these contain the original source documents as blobs)
        var tikaRequestStreams = testDataHelper.getTikaRequestStreams();
        LOG.info("Loaded {} Tika request streams with source documents", tikaRequestStreams.size());

        // Create output directories
        Path inputDir = Paths.get("build/test-data/parser/input");
        Path outputDir = Paths.get("build/test-data/parser/output");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        // Process each stream
        int successCount = 0;
        int failureCount = 0;
        List<String> failedDocuments = new ArrayList<>();

        for (PipeStream requestStream : tikaRequestStreams) {
            try {
                // Add the input stream to the input buffer
                inputBuffer.add(requestStream);
                
                if (requestStream.hasDocument()) {
                    PipeDoc requestDoc = requestStream.getDocument();
                    
                    // Create request
                    ProcessRequest request = createProcessRequest(requestDoc);

                    // Process document
                    UniAssertSubscriber<ProcessResponse> subscriber = parserService.processData(request)
                            .subscribe().withSubscriber(UniAssertSubscriber.create());
                    
                    ProcessResponse response = subscriber
                            .awaitItem()
                            .assertCompleted()
                            .getItem();

                    // Check if processing was successful
                    if (response.getSuccess() && response.hasOutputDoc()) {
                        successCount++;
                        LOG.debug("Successfully processed document: {}", requestDoc.getId());
                    } else {
                        failureCount++;
                        String docInfo = String.format("%s (%s)", requestDoc.getId(), 
                                requestDoc.hasBlob() ? requestDoc.getBlob().getFilename() : "no filename");
                        failedDocuments.add(docInfo);
                        LOG.warn("Failed to process document: {}", docInfo);
                        for (String log : response.getProcessorLogsList()) {
                            LOG.warn("  {}", log);
                        }
                    }
                } else {
                    LOG.warn("Stream {} has no document", requestStream.getStreamId());
                }
            } catch (Exception e) {
                failureCount++;
                if (requestStream.hasDocument()) {
                    PipeDoc doc = requestStream.getDocument();
                    String docInfo = String.format("%s (%s)", doc.getId(), 
                            doc.hasBlob() ? doc.getBlob().getFilename() : "no filename");
                    failedDocuments.add(docInfo);
                    LOG.error("Error processing document: {}", docInfo, e);
                } else {
                    LOG.error("Error processing stream: {}", requestStream.getStreamId(), e);
                }
            }
        }

        // Log results
        LOG.info("Processed {} streams: {} successful, {} failed", 
                tikaRequestStreams.size(), successCount, failureCount);
        LOG.info("Captured {} input streams in buffer", inputBuffer.size());
        LOG.info("Captured {} output documents in buffer", outputBuffer.size());
        
        // Generate metadata summary
        // TODO: Re-enable when DocumentProcessingSummary is made non-static for Quarkus
        // DocumentProcessingSummary.generateSummary(outputBuffer, failureCount, failedDocuments);
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

        // Save buffers to disk
        if (inputBuffer.size() > 0) {
            LOG.info("Saving input buffer to {}", inputDir);
            inputBuffer.saveToDisk(inputDir, "parser_input", 3);
            LOG.info("Saved {} input streams to {}", inputBuffer.size(), inputDir);
        }

        if (outputBuffer.size() > 0) {
            LOG.info("Saving output buffer to {}", outputDir);
            outputBuffer.saveToDisk(outputDir, "parser_output", 3);
            LOG.info("Saved {} output documents to {}", outputBuffer.size(), outputDir);
        }

        // Log instructions for copying the files
        LOG.info("\n=== Test Data Generation Complete ===");
        LOG.info("To update the test data:");
        LOG.info("1. Copy input files from {} to test-utilities/src/main/resources/test-data/parser/input/", 
                inputDir.toAbsolutePath());
        LOG.info("2. Copy output files from {} to test-utilities/src/main/resources/test-data/parser/output/", 
                outputDir.toAbsolutePath());
    }

    private ProcessRequest createProcessRequest(PipeDoc doc) {
        // Create metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-data-generation")
                .setPipeStepName("parser-step")
                .setStreamId(UUID.randomUUID().toString())
                .build();

        // Create configuration
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "10000000")  // 10MB limit
                .build();

        // Create request
        return ProcessRequest.newBuilder()
                .setDocument(doc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();
    }
}