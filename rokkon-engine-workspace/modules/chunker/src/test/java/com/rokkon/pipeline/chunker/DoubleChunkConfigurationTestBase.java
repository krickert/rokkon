package com.rokkon.pipeline.chunker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.SemanticChunk;
import com.rokkon.search.model.SemanticProcessingResult;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.test.data.TestDocumentFactory;
import com.rokkon.test.protobuf.ProtobufUtils;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test class for creating documents with two different chunking configurations:
 * 1. 150 size chunk with 40 overlap
 * 2. 500 size chunk with 100 overlap
 * 
 * The test saves the output to the test data helper for downstream testing in the embedder.
 */
public abstract class DoubleChunkConfigurationTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(DoubleChunkConfigurationTestBase.class);
    
    // Create TestDocumentFactory directly instead of injecting it
    protected final TestDocumentFactory documentFactory = new TestDocumentFactory();
    
    // Abstract method to get the chunker service - will be implemented differently in unit vs integration tests
    protected abstract PipeStepProcessor getChunkerService();
    
    // Abstract method to get the output directory - allows different paths for unit vs integration tests
    protected abstract String getOutputDir();
    
    @Test
    void testDoubleChunkConfiguration() throws IOException {
        String outputDir = getOutputDir();
        String smallChunksDir = outputDir + "/small-chunks";
        String largeChunksDir = outputDir + "/large-chunks";
        
        // Create output directories
        Files.createDirectories(Paths.get(outputDir));
        Files.createDirectories(Paths.get(smallChunksDir));
        Files.createDirectories(Paths.get(largeChunksDir));

        // Load test documents
        List<PipeDoc> testDocs = documentFactory.loadTikaProcessedDocuments();

        // Limit to first 5 documents for testing
        List<PipeDoc> docsToProcess = testDocs.subList(0, Math.min(5, testDocs.size()));

        LOG.info("Processing {} documents with two different chunk configurations", docsToProcess.size());

        List<PipeDoc> smallChunkDocs = new ArrayList<>();
        List<PipeDoc> largeChunkDocs = new ArrayList<>();

        // Process each document with both configurations
        for (PipeDoc doc : docsToProcess) {
            // Process with small chunks (150/40)
            PipeDoc smallChunkDoc = processWithChunkConfig(doc, 150, 40, "small_chunks_150_40");
            smallChunkDocs.add(smallChunkDoc);

            // Process with large chunks (500/100)
            PipeDoc largeChunkDoc = processWithChunkConfig(doc, 500, 100, "large_chunks_500_100");
            largeChunkDocs.add(largeChunkDoc);

            // Verify that both configurations produced chunks
            verifyChunks(smallChunkDoc, "small_chunks_150_40");
            verifyChunks(largeChunkDoc, "large_chunks_500_100");
        }

        // Save the processed documents for downstream testing
        saveProcessedDocs(smallChunkDocs, smallChunksDir, "small_chunk_doc");
        saveProcessedDocs(largeChunkDocs, largeChunksDir, "large_chunk_doc");

        LOG.info("Successfully processed and saved documents with two different chunk configurations");
        LOG.info("Small chunks (150/40) saved to: {}", smallChunksDir);
        LOG.info("Large chunks (500/100) saved to: {}", largeChunksDir);
    }

    /**
     * Process a document with the specified chunk configuration
     */
    protected PipeDoc processWithChunkConfig(PipeDoc doc, int chunkSize, int chunkOverlap, String configId) {
        // Create service metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("chunker-step")
                .setStreamId(UUID.randomUUID().toString())
                .build();

        // Create configuration with specified chunk size and overlap
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("source_field", Value.newBuilder().setStringValue("body").build())
                        .putFields("chunk_size", Value.newBuilder().setNumberValue(chunkSize).build())
                        .putFields("chunk_overlap", Value.newBuilder().setNumberValue(chunkOverlap).build())
                        .putFields("chunk_config_id", Value.newBuilder().setStringValue(configId).build())
                        .build())
                .build();

        // Create request
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(doc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();

        // Execute and get response
        ProcessResponse response = getChunkerService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Return the processed document
        return response.getOutputDoc();
    }

    /**
     * Verify that the document has chunks with the expected configuration
     */
    protected void verifyChunks(PipeDoc doc, String expectedConfigId) {
        assertThat(doc.getSemanticResultsCount()).isGreaterThan(0);

        SemanticProcessingResult result = doc.getSemanticResults(0);
        assertThat(result.getChunksCount()).isGreaterThan(0);
        assertThat(result.getChunkConfigId()).isEqualTo(expectedConfigId);

        // Log chunk information
        LOG.info("Document {} processed with config {}: {} chunks created", 
                doc.getId(), expectedConfigId, result.getChunksCount());

        // Verify first chunk has expected properties
        SemanticChunk firstChunk = result.getChunks(0);
        assertThat(firstChunk.getEmbeddingInfo().getChunkConfigId()).isEqualTo(expectedConfigId);
        assertThat(firstChunk.getEmbeddingInfo().getTextContent()).isNotEmpty();
    }

    /**
     * Save the processed documents to disk for downstream testing
     */
    protected void saveProcessedDocs(List<PipeDoc> docs, String outputDir, String filePrefix) throws IOException {
        Path dirPath = Paths.get(outputDir);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        for (int i = 0; i < docs.size(); i++) {
            PipeDoc doc = docs.get(i);
            String filename = String.format("%s_%03d_%s.bin", filePrefix, i, doc.getId());
            Path filePath = dirPath.resolve(filename);

            try {
                ProtobufUtils.saveProtobufToDisk(filePath.toString(), doc);
                LOG.debug("Saved document to {}", filePath);
            } catch (IOException e) {
                LOG.error("Failed to save document to {}: {}", filePath, e.getMessage());
                throw e;
            }
        }

        LOG.info("Saved {} documents to {}", docs.size(), outputDir);
    }
}