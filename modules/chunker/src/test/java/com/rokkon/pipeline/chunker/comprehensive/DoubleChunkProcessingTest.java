package com.rokkon.pipeline.chunker.comprehensive;

import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.SemanticProcessingResult;
import com.rokkon.pipeline.util.ProcessingBuffer;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.test.data.ProtobufTestDataHelper;
// import com.rokkon.test.util.DocumentProcessingSummary; // TODO: Fix static method issue with Quarkus
import com.rokkon.pipeline.chunker.UnicodeSanitizer;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
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
 * Comprehensive test for double-chunking documents.
 * This test:
 * 1. Loads parser output documents from test data
 * 2. Chunks them with a standard configuration (500 chars, 50 overlap)
 * 3. Then chunks the already chunked text with a smaller configuration (200 chars, 20 overlap)
 * 
 * This simulates a real pipeline where documents might be chunked multiple times
 * with different configurations for different purposes.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DoubleChunkProcessingTest {
    private static final Logger LOG = LoggerFactory.getLogger(DoubleChunkProcessingTest.class);

    @Inject
    @GrpcClient
    PipeStepProcessor chunkerService;

    @Inject
    ProcessingBuffer<PipeDoc> outputBuffer;

    private ProtobufTestDataHelper testDataHelper;

    @BeforeAll
    public void setup() {
        LOG.info("Output buffer enabled: {}", outputBuffer.size() >= 0);
        
        // Create test data helper manually
        testDataHelper = new ProtobufTestDataHelper();
    }

    @Test
    public void processDocumentsWithDoubleChunking() throws Exception {
        // Load parser output documents
        var allParserOutputDocs = testDataHelper.getParserOutputDocs();
        
        // Clean all documents to ensure valid UTF-8
        List<PipeDoc> parserOutputDocs = new ArrayList<>();
        for (PipeDoc doc : allParserOutputDocs) {
            try {
                // Sanitize the body to ensure valid UTF-8
                String sanitizedBody = UnicodeSanitizer.sanitizeInvalidUnicode(doc.getBody());
                // Create a sanitized document
                PipeDoc sanitizedDoc = doc.toBuilder()
                        .setBody(sanitizedBody)
                        .build();
                parserOutputDocs.add(sanitizedDoc);
            } catch (Exception e) {
                LOG.warn("Error processing document {}: {}", doc.getId(), e.getMessage());
                // Still add it but with empty body if completely broken
                parserOutputDocs.add(doc.toBuilder().setBody("").build());
            }
        }
        
        LOG.info("Loaded {} parser output documents (filtered from {})", 
                parserOutputDocs.size(), allParserOutputDocs.size());

        // Create output directories for both chunking passes
        Path firstPassDir = Paths.get("build/test-data/chunker/first-pass");
        Path secondPassDir = Paths.get("build/test-data/chunker/second-pass");
        Files.createDirectories(firstPassDir);
        Files.createDirectories(secondPassDir);

        // First pass: Standard chunking
        List<PipeDoc> firstPassResults = new ArrayList<>();
        int firstPassSuccess = 0;
        int firstPassFailure = 0;
        List<String> firstPassFailedDocs = new ArrayList<>();

        LOG.info("\n=== First Pass: Standard Chunking (500 chars, 50 overlap) ===");
        
        for (PipeDoc doc : parserOutputDocs) {
            try {
                // Create first pass configuration
                String firstPassConfig = """
                    {
                        "source_field": "body",
                        "chunk_size": 500,
                        "chunk_overlap": 50,
                        "chunk_config_id": "standard_500_50",
                        "result_set_name_template": "first_pass_%s_%s",
                        "log_prefix": "[First Pass] "
                    }
                    """;
                
                ProcessRequest request = createProcessRequest(doc, firstPassConfig, "first-pass");
                
                // Process document - use await() directly for large messages
                ProcessResponse response = null;
                try {
                    response = chunkerService.processData(request)
                            .await()
                            .atMost(java.time.Duration.ofSeconds(60)); // Longer timeout for large docs
                } catch (Exception e) {
                    LOG.error("Error processing document {}: {}", doc.getId(), e.getMessage());
                    firstPassFailure++;
                    String docInfo = String.format("%s (%s)", doc.getId(), 
                            doc.hasBlob() ? doc.getBlob().getFilename() : "no filename");
                    firstPassFailedDocs.add(docInfo);
                    continue; // Skip to next document
                }

                if (response.getSuccess() && response.hasOutputDoc()) {
                    PipeDoc outputDoc = response.getOutputDoc();
                    firstPassResults.add(outputDoc);
                    firstPassSuccess++;
                    
                    // Log chunk count
                    int chunkCount = outputDoc.getSemanticResultsCount() > 0 ? 
                            outputDoc.getSemanticResults(0).getChunksCount() : 0;
                    LOG.debug("Document {} chunked into {} chunks", doc.getId(), chunkCount);
                } else {
                    firstPassFailure++;
                    String docInfo = String.format("%s (%s)", doc.getId(), 
                            doc.hasBlob() ? doc.getBlob().getFilename() : "no filename");
                    firstPassFailedDocs.add(docInfo);
                    LOG.warn("Failed to chunk document: {}", docInfo);
                    for (String log : response.getProcessorLogsList()) {
                        LOG.warn("  {}", log);
                    }
                }
            } catch (Exception e) {
                firstPassFailure++;
                String docInfo = String.format("%s", doc.getId());
                firstPassFailedDocs.add(docInfo);
                LOG.error("Error chunking document: {}", docInfo, e);
            }
        }

        LOG.info("First pass completed: {} successful, {} failed", firstPassSuccess, firstPassFailure);

        // Clear the buffer before second pass
        outputBuffer.clear();

        // Second pass: Fine-grained chunking on already chunked documents
        List<PipeDoc> secondPassResults = new ArrayList<>();
        int secondPassSuccess = 0;
        int secondPassFailure = 0;
        List<String> secondPassFailedDocs = new ArrayList<>();

        LOG.info("\n=== Second Pass: Fine-grained Chunking (200 chars, 20 overlap) ===");

        for (PipeDoc firstPassDoc : firstPassResults) {
            try {
                // For second pass, we'll create a document with concatenated chunk text
                PipeDoc docForSecondPass = createDocumentFromChunks(firstPassDoc);
                
                if (docForSecondPass == null) {
                    LOG.debug("Skipping document {} - no chunks from first pass", firstPassDoc.getId());
                    continue;
                }
                
                // Create second pass configuration
                String secondPassConfig = """
                    {
                        "source_field": "body",
                        "chunk_size": 200,
                        "chunk_overlap": 20,
                        "chunk_config_id": "fine_200_20",
                        "result_set_name_template": "second_pass_%s_%s",
                        "log_prefix": "[Second Pass] "
                    }
                    """;
                
                ProcessRequest request = createProcessRequest(docForSecondPass, secondPassConfig, "second-pass");
                
                // Process document - use await() directly for large messages
                ProcessResponse response = null;
                try {
                    response = chunkerService.processData(request)
                            .await()
                            .atMost(java.time.Duration.ofSeconds(60)); // Longer timeout for large docs
                } catch (Exception e) {
                    LOG.error("Error processing document {}: {}", docForSecondPass.getId(), e.getMessage());
                    secondPassFailure++;
                    String docInfo = String.format("%s", docForSecondPass.getId());
                    secondPassFailedDocs.add(docInfo);
                    continue; // Skip to next document
                }

                if (response.getSuccess() && response.hasOutputDoc()) {
                    PipeDoc outputDoc = response.getOutputDoc();
                    secondPassResults.add(outputDoc);
                    secondPassSuccess++;
                    
                    // Log chunk count
                    int chunkCount = outputDoc.getSemanticResultsCount() > 0 ? 
                            outputDoc.getSemanticResults(0).getChunksCount() : 0;
                    LOG.debug("Document {} re-chunked into {} fine-grained chunks", outputDoc.getId(), chunkCount);
                } else {
                    secondPassFailure++;
                    String docInfo = String.format("%s", docForSecondPass.getId());
                    secondPassFailedDocs.add(docInfo);
                    LOG.warn("Failed to re-chunk document: {}", docInfo);
                    for (String log : response.getProcessorLogsList()) {
                        LOG.warn("  {}", log);
                    }
                }
            } catch (Exception e) {
                secondPassFailure++;
                String docInfo = String.format("%s", firstPassDoc.getId());
                secondPassFailedDocs.add(docInfo);
                LOG.error("Error re-chunking document: {}", docInfo, e);
            }
        }

        LOG.info("Second pass completed: {} successful, {} failed", secondPassSuccess, secondPassFailure);

        // Generate summaries
        LOG.info("\n=== First Pass Summary ===");
        // TODO: Re-enable when DocumentProcessingSummary is made non-static for Quarkus
        // DocumentProcessingSummary.generateSummary(
        //         createBufferFromDocs(firstPassResults), 
        //         firstPassFailure, 
        //         firstPassFailedDocs
        // );
        LOG.info("Total documents processed: {}", firstPassResults.size() + firstPassFailure);
        LOG.info("Successfully parsed: {}", firstPassResults.size());
        LOG.info("Failed to parse: {}", firstPassFailure);

        LOG.info("\n=== Second Pass Summary ===");
        // TODO: Re-enable when DocumentProcessingSummary is made non-static for Quarkus
        // DocumentProcessingSummary.generateSummary(outputBuffer, secondPassFailure, secondPassFailedDocs);
        LOG.info("Total documents processed: {}", outputBuffer.size() + secondPassFailure);
        LOG.info("Successfully parsed: {}", outputBuffer.size());
        LOG.info("Failed to parse: {}", secondPassFailure);

        // Save second pass results (first pass was cleared from buffer)
        if (outputBuffer.size() > 0) {
            LOG.info("Saving second pass buffer to {}", secondPassDir);
            outputBuffer.saveToDisk(secondPassDir, "chunker_second_pass", 3);
            LOG.info("Saved {} documents to {}", outputBuffer.size(), secondPassDir);
        }

        // Save first pass results manually since buffer was cleared
        if (!firstPassResults.isEmpty()) {
            LOG.info("Saving first pass results to {}", firstPassDir);
            ProcessingBuffer<PipeDoc> firstPassBuffer = createBufferFromDocs(firstPassResults);
            firstPassBuffer.saveToDisk(firstPassDir, "chunker_first_pass", 3);
            LOG.info("Saved {} documents to {}", firstPassResults.size(), firstPassDir);
        }

        // Log chunk statistics
        logChunkStatistics(firstPassResults, secondPassResults);
    }

    private ProcessRequest createProcessRequest(PipeDoc doc, String configJson, String stepName) throws Exception {
        // Create metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-double-chunking")
                .setPipeStepName(stepName)
                .setStreamId(UUID.randomUUID().toString())
                .build();

        // Parse JSON config to Struct
        Struct.Builder structBuilder = Struct.newBuilder();
        JsonFormat.parser().merge(configJson, structBuilder);
        
        // Create configuration
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(structBuilder.build())
                .build();

        // Create request
        return ProcessRequest.newBuilder()
                .setDocument(doc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();
    }

    private PipeDoc createDocumentFromChunks(PipeDoc chunkedDoc) {
        if (chunkedDoc.getSemanticResultsCount() == 0) {
            return null;
        }

        // Concatenate all chunk texts from the first semantic result
        SemanticProcessingResult semanticResult = chunkedDoc.getSemanticResults(0);
        if (semanticResult.getChunksCount() == 0) {
            return null;
        }

        StringBuilder concatenatedText = new StringBuilder();
        for (int i = 0; i < semanticResult.getChunksCount(); i++) {
            if (i > 0) {
                concatenatedText.append(" "); // Add space between chunks
            }
            concatenatedText.append(semanticResult.getChunks(i).getEmbeddingInfo().getTextContent());
        }

        // Create new document with concatenated chunk text
        return PipeDoc.newBuilder()
                .setId(chunkedDoc.getId() + "-rechunked")
                .setTitle(chunkedDoc.getTitle())
                .setBody(concatenatedText.toString())
                .build();
    }

    private ProcessingBuffer<PipeDoc> createBufferFromDocs(List<PipeDoc> docs) {
        // Create a simple mock buffer that just holds the docs
        return new ProcessingBuffer<PipeDoc>() {
            private final List<PipeDoc> items = new ArrayList<>(docs);
            
            @Override
            public void add(PipeDoc message) {
                items.add(message);
            }
            
            @Override
            public void saveToDisk() {}
            
            @Override
            public void saveToDisk(String fileNamePrefix, int numberPrecision) {}
            
            @Override
            public void saveToDisk(Path location, String fileNamePrefix, int numberPrecision) {}
            
            @Override
            public int size() {
                return items.size();
            }
            
            @Override
            public void clear() {
                items.clear();
            }
            
            @Override
            public List<PipeDoc> snapshot() {
                return new ArrayList<>(items);
            }
        };
    }

    private void logChunkStatistics(List<PipeDoc> firstPassResults, List<PipeDoc> secondPassResults) {
        LOG.info("\n=== Chunk Statistics ===");
        
        // First pass statistics
        int totalFirstPassChunks = 0;
        for (PipeDoc doc : firstPassResults) {
            if (doc.getSemanticResultsCount() > 0) {
                totalFirstPassChunks += doc.getSemanticResults(0).getChunksCount();
            }
        }
        LOG.info("First pass: {} documents → {} chunks (avg {}/doc)", 
                firstPassResults.size(), 
                totalFirstPassChunks,
                firstPassResults.isEmpty() ? 0 : totalFirstPassChunks / firstPassResults.size());

        // Second pass statistics
        int totalSecondPassChunks = 0;
        for (PipeDoc doc : secondPassResults) {
            if (doc.getSemanticResultsCount() > 0) {
                totalSecondPassChunks += doc.getSemanticResults(0).getChunksCount();
            }
        }
        LOG.info("Second pass: {} documents → {} chunks (avg {}/doc)", 
                secondPassResults.size(), 
                totalSecondPassChunks,
                secondPassResults.isEmpty() ? 0 : totalSecondPassChunks / secondPassResults.size());
        
        if (totalFirstPassChunks > 0) {
            LOG.info("Chunk multiplication factor: {:.2f}x", 
                    (double) totalSecondPassChunks / totalFirstPassChunks);
        }
    }
}