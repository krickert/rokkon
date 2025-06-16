package com.rokkon.modules.embedder;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ServiceMetadata;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import io.quarkus.grpc.GrpcService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for EmbedderService using chunked documents from the chunker module.
 * This test demonstrates the reactive processing capabilities with real document chunks.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmbedderServicePerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(EmbedderServicePerformanceTest.class);

    @Inject
    @GrpcService
    EmbedderService embedderService;

    @Inject
    ReactiveVectorizer vectorizer;

    private static final String CHUNKED_DOCS_PATH = "modules/tika-parser/src/test/resources/test-data/sample-documents-pipe-docs";
    private static final int MIN_EXPECTED_DOCUMENTS = 10;
    private static final String TEST_STREAM_ID = "performance-test-stream";
    private static final String TEST_PIPE_STEP = "performance-embedder";

    @Test
    @EnabledIfSystemProperty(named = "test.performance", matches = "true")
    void testEmbedderPerformanceWithChunkedDocuments() throws IOException {
        log.info("=== EMBEDDER PERFORMANCE TEST ===");
        log.info("GPU Enabled: {}", vectorizer.isUsingGpu());
        log.info("Model: {} ({})", vectorizer.getModel().name(), vectorizer.getModel().getDescription());
        log.info("Max Batch Size: {}", vectorizer.getMaxBatchSize());

        // Load processed documents (ideally with chunks from chunker)
        List<PipeDoc> processedDocs = loadProcessedDocuments();
        
        if (processedDocs.size() < MIN_EXPECTED_DOCUMENTS) {
            log.warn("Only {} documents available, skipping performance test", processedDocs.size());
            return;
        }

        log.info("Loaded {} processed documents for embedding performance testing", processedDocs.size());

        // Test different batch configurations
        testPerformanceWithBatchSize(processedDocs, 8);
        testPerformanceWithBatchSize(processedDocs, 16);
        testPerformanceWithBatchSize(processedDocs, 32);
        if (vectorizer.isUsingGpu()) {
            testPerformanceWithBatchSize(processedDocs, 64);
        }
    }

    private void testPerformanceWithBatchSize(List<PipeDoc> documents, int batchSize) {
        log.info("\n--- Testing with batch size: {} ---", batchSize);

        EmbedderOptions options = new EmbedderOptions();
        
        // Override batch size for testing
        EmbedderOptions testOptions = new EmbedderOptions(
                options.embeddingModels(),
                true, // check chunks
                true, // check document fields
                options.documentFields(),
                options.customFieldMappings(),
                options.processKeywords(),
                options.keywordNgramSizes(),
                options.maxTokenSize(),
                "[PERF_TEST] ",
                options.resultSetNameTemplate(),
                batchSize, // custom batch size
                options.backpressureStrategy()
        );

        List<PipeDoc> testDocuments = documents.subList(0, Math.min(20, documents.size()));
        
        PerformanceMetrics metrics = new PerformanceMetrics();
        Instant startTime = Instant.now();

        for (PipeDoc doc : testDocuments) {
            Instant docStartTime = Instant.now();
            
            ProcessRequest request = createProcessRequest(doc, testOptions);
            Uni<ProcessResponse> responseUni = embedderService.processData(request);
            ProcessResponse response = responseUni.await().indefinitely();

            assertTrue(response.getSuccess(), "Processing should succeed for document: " + doc.getId());
            
            Duration docProcessingTime = Duration.between(docStartTime, Instant.now());
            analyzeEmbeddingResponse(response, metrics, docProcessingTime);
        }

        Duration totalTime = Duration.between(startTime, Instant.now());
        logPerformanceResults(batchSize, testDocuments.size(), metrics, totalTime);
    }

    private void analyzeEmbeddingResponse(ProcessResponse response, PerformanceMetrics metrics, Duration processingTime) {
        metrics.addProcessingTime(processingTime);
        
        PipeDoc outputDoc = response.getOutputDoc();
        int embeddingResultsCount = 0;
        int totalEmbeddings = 0;

        for (SemanticProcessingResult result : outputDoc.getSemanticResultsList()) {
            embeddingResultsCount += result.getChunksCount();
            
            // Count embeddings from chunks
            for (SemanticChunk chunk : result.getChunksList()) {
                if (chunk.getEmbeddingInfo().getVectorCount() > 0) {
                    totalEmbeddings++;
                }
            }
        }
        
        // Count named embeddings
        totalEmbeddings += outputDoc.getNamedEmbeddingsCount();

        metrics.addEmbeddingCounts(embeddingResultsCount, totalEmbeddings);
        
        if (!response.getProcessorLogsList().isEmpty()) {
            String logMessage = response.getProcessorLogs(0);
            if (logMessage.contains("chunks")) {
                metrics.incrementChunkDocuments();
            }
            if (logMessage.contains("document fields")) {
                metrics.incrementFieldDocuments();
            }
        }
    }

    private void logPerformanceResults(int batchSize, int documentCount, PerformanceMetrics metrics, Duration totalTime) {
        log.info("Batch Size: {}", batchSize);
        log.info("Documents Processed: {}", documentCount);
        log.info("Total Processing Time: {} ms", totalTime.toMillis());
        log.info("Average Time per Document: {:.2f} ms", metrics.getAverageProcessingTimeMs());
        log.info("Total Embeddings Generated: {}", metrics.getTotalEmbeddings());
        log.info("Average Embeddings per Document: {:.1f}", metrics.getAverageEmbeddingsPerDocument(documentCount));
        log.info("Throughput: {:.2f} documents/second", documentCount / (totalTime.toMillis() / 1000.0));
        log.info("Embedding Throughput: {:.2f} embeddings/second", metrics.getTotalEmbeddings() / (totalTime.toMillis() / 1000.0));
        log.info("Documents with Chunks: {}", metrics.getChunkDocuments());
        log.info("Documents with Field Embeddings: {}", metrics.getFieldDocuments());
        
        // Performance assertions
        assertTrue(metrics.getAverageProcessingTimeMs() < 10000, "Average processing time should be under 10 seconds per document");
        assertTrue(metrics.getTotalEmbeddings() > 0, "Should generate embeddings");
        
        if (vectorizer.isUsingGpu()) {
            log.info("✅ GPU-accelerated processing completed successfully");
        } else {
            log.info("✅ CPU processing completed successfully");
        }
    }

    @Test
    void testReactiveStreamProcessing() {
        log.info("Testing reactive stream processing capabilities");

        // Create a document with multiple chunks to test batch processing
        PipeDoc testDoc = createMultiChunkDocument();
        
        EmbedderOptions options = new EmbedderOptions();
        ProcessRequest request = createProcessRequest(testDoc, options);

        Instant startTime = Instant.now();
        Uni<ProcessResponse> responseUni = embedderService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();
        Duration processingTime = Duration.between(startTime, Instant.now());

        assertTrue(response.getSuccess(), "Reactive processing should succeed");
        log.info("Reactive processing completed in {} ms", processingTime.toMillis());

        // Verify embeddings were generated
        assertTrue(response.getOutputDoc().getSemanticResultsCount() > 0, "Should have semantic results");
        
        boolean hasEmbeddings = response.getOutputDoc().getSemanticResultsList().stream()
                .anyMatch(result -> result.getChunksCount() > 0 && 
                    result.getChunksList().stream().anyMatch(chunk -> chunk.getEmbeddingInfo().getVectorCount() > 0));
        assertTrue(hasEmbeddings, "Should have embedding results");
    }

    private PipeDoc createMultiChunkDocument() {
        // Create a document with multiple chunks to test batch processing
        List<SemanticChunk> chunks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ChunkEmbedding chunkEmbedding = ChunkEmbedding.newBuilder()
                    .setChunkId("chunk-" + i)
                    .setTextContent("This is test chunk number " + i + " with some sample content for embedding generation.")
                    .setOriginalCharStartOffset(i * 50)
                    .setOriginalCharEndOffset((i + 1) * 50)
                    .setChunkConfigId("test-config")
                    .build();

            SemanticChunk chunk = SemanticChunk.newBuilder()
                    .setChunkId("chunk-" + i)
                    .setChunkNumber(i)
                    .setEmbeddingInfo(chunkEmbedding)
                    .build();

            chunks.add(chunk);
        }

        SemanticProcessingResult semanticResult = SemanticProcessingResult.newBuilder()
                .setResultId("multi-chunk-result")
                .setResultSetName("test-chunks")
                .setSourceFieldName("body")
                .addAllChunks(chunks)
                .build();

        return PipeDoc.newBuilder()
                .setId("multi-chunk-doc")
                .setBody("This is a test document with multiple chunks for performance testing.")
                .setTitle("Multi-Chunk Test Document")
                .addSemanticResults(semanticResult)
                .build();
    }

    private ProcessRequest createProcessRequest(PipeDoc doc, EmbedderOptions options) {
        Struct.Builder configBuilder = Struct.newBuilder()
                .putFields("check_chunks", Value.newBuilder().setBoolValue(options.checkChunks()).build())
                .putFields("check_document_fields", Value.newBuilder().setBoolValue(options.checkDocumentFields()).build())
                .putFields("max_batch_size", Value.newBuilder().setNumberValue(options.maxBatchSize()).build())
                .putFields("log_prefix", Value.newBuilder().setStringValue(options.logPrefix()).build());

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(configBuilder.build())
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId(TEST_STREAM_ID)
                .setPipeStepName(TEST_PIPE_STEP)
                .build();

        return ProcessRequest.newBuilder()
                .setDocument(doc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
    }

    private List<PipeDoc> loadProcessedDocuments() throws IOException {
        List<PipeDoc> documents = new ArrayList<>();
        Path docsDir = Paths.get(CHUNKED_DOCS_PATH);
        
        if (!Files.exists(docsDir)) {
            log.warn("Processed docs directory not found: {}", docsDir);
            return documents;
        }

        try (Stream<Path> paths = Files.walk(docsDir)) {
            paths.filter(path -> path.toString().endsWith(".bin"))
                 .sorted()
                 .limit(50) // Limit for performance testing
                 .forEach(path -> {
                     try {
                         byte[] data = Files.readAllBytes(path);
                         PipeDoc doc = PipeDoc.parseFrom(data);
                         documents.add(doc);
                     } catch (Exception e) {
                         log.warn("Failed to load document from {}: {}", path, e.getMessage());
                     }
                 });
        }

        log.info("Successfully loaded {} documents from {}", documents.size(), docsDir);
        return documents;
    }

    private static class PerformanceMetrics {
        private final List<Duration> processingTimes = new ArrayList<>();
        private int totalEmbeddingResults = 0;
        private int totalEmbeddings = 0;
        private int chunkDocuments = 0;
        private int fieldDocuments = 0;

        public void addProcessingTime(Duration time) {
            processingTimes.add(time);
        }

        public void addEmbeddingCounts(int embeddingResults, int embeddings) {
            totalEmbeddingResults += embeddingResults;
            totalEmbeddings += embeddings;
        }

        public void incrementChunkDocuments() { chunkDocuments++; }
        public void incrementFieldDocuments() { fieldDocuments++; }

        public double getAverageProcessingTimeMs() {
            return processingTimes.stream()
                    .mapToLong(Duration::toMillis)
                    .average()
                    .orElse(0.0);
        }

        public int getTotalEmbeddings() { return totalEmbeddings; }
        public int getChunkDocuments() { return chunkDocuments; }
        public int getFieldDocuments() { return fieldDocuments; }

        public double getAverageEmbeddingsPerDocument(int documentCount) {
            return documentCount > 0 ? (double) totalEmbeddings / documentCount : 0.0;
        }
    }
}