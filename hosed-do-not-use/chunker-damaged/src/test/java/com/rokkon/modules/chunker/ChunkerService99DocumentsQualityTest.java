package com.rokkon.modules.chunker;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.SemanticChunk;
import com.rokkon.search.model.SemanticProcessingResult;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive quality test for ChunkerService using 99+ processed Tika documents.
 * This test verifies that the chunker maintains quality NLP analysis and creates
 * appropriate chunks with metadata from real document content.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChunkerService99DocumentsQualityTest {

    private static final Logger log = LoggerFactory.getLogger(ChunkerService99DocumentsQualityTest.class);

    @Inject
    @GrpcService
    ChunkerService chunkerService;

    private static final String TIKA_DOCS_PATH = "modules/tika-parser/src/test/resources/test-data/sample-documents-pipe-docs";
    private static final int MIN_EXPECTED_DOCUMENTS = 99;
    private static final String TEST_STREAM_ID = "quality-test-stream";
    private static final String TEST_PIPE_STEP = "quality-chunker";

    @Test
    void testChunkerQualityWith99Documents() throws IOException {
        // Load all processed Tika documents
        List<PipeDoc> tikaProcessedDocs = loadTikaProcessedDocuments();
        
        log.info("Loaded {} Tika-processed documents for chunker quality testing", tikaProcessedDocs.size());
        assertTrue(tikaProcessedDocs.size() >= MIN_EXPECTED_DOCUMENTS, 
                "Should have at least " + MIN_EXPECTED_DOCUMENTS + " documents, found: " + tikaProcessedDocs.size());

        // Test chunking with different configurations
        testChunkingQualityMetrics(tikaProcessedDocs);
        testChunkingWithDifferentSizes(tikaProcessedDocs);
        testUrlPreservation(tikaProcessedDocs);
    }

    private void testChunkingQualityMetrics(List<PipeDoc> documents) {
        log.info("Testing chunking quality metrics with {} documents", documents.size());
        
        ChunkerOptions defaultOptions = new ChunkerOptions(
                "body", 500, 50, 
                ChunkerOptions.DEFAULT_CHUNK_ID_TEMPLATE,
                "quality_test_500_50",
                ChunkerOptions.DEFAULT_RESULT_SET_NAME_TEMPLATE,
                "[QUALITY_TEST] ",
                false
        );

        QualityMetrics aggregateMetrics = new QualityMetrics();
        int processedDocs = 0;
        int totalChunks = 0;
        int docsWithContent = 0;

        for (PipeDoc doc : documents) {
            // Skip documents without body content
            if (!doc.hasBody() || doc.getBody().trim().isEmpty()) {
                continue;
            }

            ProcessRequest request = createProcessRequest(doc, defaultOptions);
            Uni<ProcessResponse> responseUni = chunkerService.processData(request);
            ProcessResponse response = responseUni.await().indefinitely();

            assertTrue(response.getSuccess(), 
                    "Processing should succeed for document: " + doc.getId());

            if (response.getOutputDoc().getSemanticResultsCount() > 0) {
                docsWithContent++;
                SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
                totalChunks += result.getChunksCount();

                // Analyze chunk quality
                for (SemanticChunk chunk : result.getChunksList()) {
                    analyzeChunkQuality(chunk, aggregateMetrics);
                }
            }
            processedDocs++;
        }

        log.info("=== CHUNKER QUALITY METRICS ===");
        log.info("Processed documents: {}", processedDocs);
        log.info("Documents with content: {}", docsWithContent);
        log.info("Total chunks created: {}", totalChunks);
        log.info("Average chunks per document: {:.2f}", totalChunks / (double) Math.max(docsWithContent, 1));
        log.info("Average chunk length: {:.1f} characters", aggregateMetrics.getAverageChunkLength());
        log.info("Average words per chunk: {:.1f}", aggregateMetrics.getAverageWordsPerChunk());
        log.info("Average sentences per chunk: {:.1f}", aggregateMetrics.getAverageSentencesPerChunk());
        log.info("Chunks with URLs: {}", aggregateMetrics.getChunksWithUrls());
        log.info("Potential headings detected: {}", aggregateMetrics.getPotentialHeadings());
        log.info("List items detected: {}", aggregateMetrics.getListItems());

        // Quality assertions
        assertTrue(docsWithContent > 0, "Should have documents with content to chunk");
        assertTrue(totalChunks > 0, "Should create chunks from the documents");
        assertTrue(aggregateMetrics.getAverageChunkLength() > 0, "Chunks should have content");
        assertTrue(aggregateMetrics.getAverageChunkLength() <= 600, "Chunks should not be excessively long");
        assertTrue(aggregateMetrics.getAverageWordsPerChunk() > 0, "Chunks should contain words");
    }

    private void testChunkingWithDifferentSizes(List<PipeDoc> documents) {
        log.info("Testing chunking with different chunk sizes");
        
        int[] chunkSizes = {200, 500, 1000};
        List<PipeDoc> sampleDocs = documents.subList(0, Math.min(10, documents.size()));

        for (int chunkSize : chunkSizes) {
            ChunkerOptions options = new ChunkerOptions(
                    "body", chunkSize, chunkSize / 10,
                    ChunkerOptions.DEFAULT_CHUNK_ID_TEMPLATE,
                    "size_test_" + chunkSize,
                    ChunkerOptions.DEFAULT_RESULT_SET_NAME_TEMPLATE,
                    "[SIZE_TEST] ",
                    false
            );

            int totalChunks = 0;
            double totalChunkLength = 0;

            for (PipeDoc doc : sampleDocs) {
                if (!doc.hasBody() || doc.getBody().trim().isEmpty()) {
                    continue;
                }

                ProcessRequest request = createProcessRequest(doc, options);
                Uni<ProcessResponse> responseUni = chunkerService.processData(request);
                ProcessResponse response = responseUni.await().indefinitely();

                if (response.getSuccess() && response.getOutputDoc().getSemanticResultsCount() > 0) {
                    SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
                    totalChunks += result.getChunksCount();
                    
                    for (SemanticChunk chunk : result.getChunksList()) {
                        totalChunkLength += chunk.getEmbeddingInfo().getTextContent().length();
                    }
                }
            }

            double avgChunkLength = totalChunks > 0 ? totalChunkLength / totalChunks : 0;
            log.info("Chunk size {}: {} total chunks, avg length {:.1f}", 
                    chunkSize, totalChunks, avgChunkLength);
            
            // Verify chunks are approximately the right size (allowing for token boundaries)
            if (totalChunks > 0) {
                assertTrue(avgChunkLength <= chunkSize * 1.5, 
                        "Average chunk length should not exceed 1.5x target size");
            }
        }
    }

    private void testUrlPreservation(List<PipeDoc> documents) {
        log.info("Testing URL preservation in chunking");
        
        ChunkerOptions urlOptions = new ChunkerOptions(
                "body", 300, 30,
                ChunkerOptions.DEFAULT_CHUNK_ID_TEMPLATE,
                "url_preservation_test",
                ChunkerOptions.DEFAULT_RESULT_SET_NAME_TEMPLATE,
                "[URL_TEST] ",
                true  // Enable URL preservation
        );

        // Test with a few documents
        List<PipeDoc> sampleDocs = documents.subList(0, Math.min(5, documents.size()));
        int chunksWithUrlPlaceholders = 0;

        for (PipeDoc doc : sampleDocs) {
            if (!doc.hasBody()) continue;

            ProcessRequest request = createProcessRequest(doc, urlOptions);
            Uni<ProcessResponse> responseUni = chunkerService.processData(request);
            ProcessResponse response = responseUni.await().indefinitely();

            if (response.getSuccess() && response.getOutputDoc().getSemanticResultsCount() > 0) {
                SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
                
                for (SemanticChunk chunk : result.getChunksList()) {
                    if (chunk.getMetadataMap().containsKey("contains_urlplaceholder") &&
                        chunk.getMetadataMap().get("contains_urlplaceholder").getBoolValue()) {
                        chunksWithUrlPlaceholders++;
                    }
                }
            }
        }

        log.info("Found {} chunks with URL placeholders during URL preservation test", chunksWithUrlPlaceholders);
        // URL preservation should work without errors (chunks may or may not contain URLs)
    }

    private void analyzeChunkQuality(SemanticChunk chunk, QualityMetrics metrics) {
        String text = chunk.getEmbeddingInfo().getTextContent();
        metrics.addChunkLength(text.length());

        Map<String, Value> metadata = chunk.getMetadataMap();
        
        if (metadata.containsKey("word_count")) {
            metrics.addWordCount((int) metadata.get("word_count").getNumberValue());
        }
        
        if (metadata.containsKey("sentence_count")) {
            metrics.addSentenceCount((int) metadata.get("sentence_count").getNumberValue());
        }
        
        if (metadata.containsKey("contains_urlplaceholder") && 
            metadata.get("contains_urlplaceholder").getBoolValue()) {
            metrics.incrementChunksWithUrls();
        }
        
        if (metadata.containsKey("potential_heading_score") && 
            metadata.get("potential_heading_score").getNumberValue() > 0.5) {
            metrics.incrementPotentialHeadings();
        }
        
        if (metadata.containsKey("list_item_indicator") && 
            metadata.get("list_item_indicator").getBoolValue()) {
            metrics.incrementListItems();
        }
    }

    private ProcessRequest createProcessRequest(PipeDoc doc, ChunkerOptions options) {
        Struct.Builder configBuilder = Struct.newBuilder()
                .putFields("source_field", Value.newBuilder().setStringValue(options.sourceField()).build())
                .putFields("chunk_size", Value.newBuilder().setNumberValue(options.chunkSize()).build())
                .putFields("chunk_overlap", Value.newBuilder().setNumberValue(options.chunkOverlap()).build())
                .putFields("chunk_config_id", Value.newBuilder().setStringValue(options.chunkConfigId()).build())
                .putFields("preserve_urls", Value.newBuilder().setBoolValue(options.preserveUrls()).build());

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

    private List<PipeDoc> loadTikaProcessedDocuments() throws IOException {
        List<PipeDoc> documents = new ArrayList<>();
        Path docsDir = Paths.get(TIKA_DOCS_PATH);
        
        if (!Files.exists(docsDir)) {
            log.warn("Tika docs directory not found: {}", docsDir);
            return documents;
        }

        try (Stream<Path> paths = Files.walk(docsDir)) {
            paths.filter(path -> path.toString().endsWith(".bin"))
                 .sorted()
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

    private static class QualityMetrics {
        private final List<Integer> chunkLengths = new ArrayList<>();
        private final List<Integer> wordCounts = new ArrayList<>();
        private final List<Integer> sentenceCounts = new ArrayList<>();
        private int chunksWithUrls = 0;
        private int potentialHeadings = 0;
        private int listItems = 0;

        public void addChunkLength(int length) { chunkLengths.add(length); }
        public void addWordCount(int count) { wordCounts.add(count); }
        public void addSentenceCount(int count) { sentenceCounts.add(count); }
        public void incrementChunksWithUrls() { chunksWithUrls++; }
        public void incrementPotentialHeadings() { potentialHeadings++; }
        public void incrementListItems() { listItems++; }

        public double getAverageChunkLength() {
            return chunkLengths.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        }

        public double getAverageWordsPerChunk() {
            return wordCounts.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        }

        public double getAverageSentencesPerChunk() {
            return sentenceCounts.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        }

        public int getChunksWithUrls() { return chunksWithUrls; }
        public int getPotentialHeadings() { return potentialHeadings; }
        public int getListItems() { return listItems; }
    }
}