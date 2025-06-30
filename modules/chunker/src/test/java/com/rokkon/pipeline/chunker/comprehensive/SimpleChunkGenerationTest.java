package com.rokkon.pipeline.chunker.comprehensive;

import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.pipeline.util.ProcessingBuffer;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.test.data.ProtobufTestDataHelper;
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
 * Simple test to generate chunked documents for testing.
 * This test processes documents one at a time and saves successful results.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SimpleChunkGenerationTest {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleChunkGenerationTest.class);

    @Inject
    @GrpcClient
    PipeStepProcessor chunkerService;

    @Inject
    ProcessingBuffer<PipeDoc> outputBuffer;

    private ProtobufTestDataHelper testDataHelper;

    @BeforeAll
    public void setup() {
        LOG.info("Output buffer enabled: {}", outputBuffer.size() >= 0);
        testDataHelper = new ProtobufTestDataHelper();
    }

    @Test
    public void generateChunkedDocuments() throws Exception {
        // Load parser output documents
        List<PipeDoc> allParserOutputDocs = new ArrayList<>();
        testDataHelper.getParserOutputDocs().forEach(allParserOutputDocs::add);
        
        // Create output directory
        Path outputDir = Paths.get("build/test-data/chunker/output");
        Files.createDirectories(outputDir);

        List<PipeDoc> successfulDocs = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;
        
        LOG.info("Processing {} parser output documents", allParserOutputDocs.size());

        // Process ALL documents
        for (int i = 0; i < allParserOutputDocs.size(); i++) { // Process all 126
            PipeDoc doc = allParserOutputDocs.get(i);
            
            try {
                // Log large documents but still process them
                if (doc.getBody().length() > 10 * 1024 * 1024) {
                    LOG.info("Processing large document {} ({} MB)", doc.getId(), 
                            doc.getBody().length() / (1024 * 1024));
                }
                
                // Sanitize the document body
                String sanitizedBody = UnicodeSanitizer.sanitizeInvalidUnicode(doc.getBody());
                PipeDoc sanitizedDoc = doc.toBuilder()
                        .setBody(sanitizedBody)
                        .build();

                // Create chunking configuration
                String chunkConfig = """
                    {
                        "source_field": "body",
                        "chunk_size": 500,
                        "chunk_overlap": 50,
                        "chunk_config_id": "standard_500_50",
                        "result_set_name_template": "chunks_%s_%s",
                        "log_prefix": "[Chunk] "
                    }
                    """;
                
                ProcessRequest request = createProcessRequest(sanitizedDoc, chunkConfig);
                
                LOG.info("Processing document {}/{}: {} (size: {} bytes)", 
                        i + 1, allParserOutputDocs.size(), 
                        doc.getId(), sanitizedDoc.getBody().length());
                
                // Process with longer timeout for large documents
                ProcessResponse response = chunkerService.processData(request)
                        .await().atMost(java.time.Duration.ofSeconds(30));

                if (response != null && response.getSuccess() && response.hasOutputDoc()) {
                    PipeDoc outputDoc = response.getOutputDoc();
                    successfulDocs.add(outputDoc);
                    successCount++;
                    
                    int chunkCount = outputDoc.getSemanticResultsCount() > 0 ? 
                            outputDoc.getSemanticResults(0).getChunksCount() : 0;
                    LOG.info("✓ Successfully chunked into {} chunks", chunkCount);
                } else {
                    failCount++;
                    LOG.warn("✗ Failed to chunk document: {}", 
                            response != null ? response.getProcessorLogsList() : "null response");
                }
            } catch (Exception e) {
                failCount++;
                LOG.error("✗ Error processing document {}: {}", doc.getId(), e.getMessage());
            }
        }

        LOG.info("\n=== Chunking Summary ===");
        LOG.info("Processed: {}", successCount + failCount);
        LOG.info("Successful: {}", successCount);
        LOG.info("Failed: {}", failCount);
        
        // Save successful documents
        if (!successfulDocs.isEmpty()) {
            LOG.info("Saving {} successful documents to {}", successfulDocs.size(), outputDir);
            
            // Create a buffer with the successful docs
            ProcessingBuffer<PipeDoc> saveBuffer = createBufferFromDocs(successfulDocs);
            saveBuffer.saveToDisk(outputDir, "chunker_output", 3);
            
            LOG.info("✓ Saved chunked documents to disk");
        }
    }

    private ProcessRequest createProcessRequest(PipeDoc doc, String configJson) throws Exception {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-chunking")
                .setPipeStepName("chunker")
                .setStreamId(UUID.randomUUID().toString())
                .build();

        Struct.Builder structBuilder = Struct.newBuilder();
        JsonFormat.parser().merge(configJson, structBuilder);
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(structBuilder.build())
                .build();

        return ProcessRequest.newBuilder()
                .setDocument(doc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();
    }
    
    private ProcessingBuffer<PipeDoc> createBufferFromDocs(List<PipeDoc> docs) {
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
            public void saveToDisk(Path location, String fileNamePrefix, int numberPrecision) {
                try {
                    for (int i = 0; i < items.size(); i++) {
                        String filename = String.format("%s_%0" + numberPrecision + "d.bin", fileNamePrefix, i);
                        Path filePath = location.resolve(filename);
                        Files.write(filePath, items.get(i).toByteArray());
                    }
                } catch (Exception e) {
                    LOG.error("Error saving to disk", e);
                }
            }
            
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
}