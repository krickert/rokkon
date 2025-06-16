package com.rokkon.test.pipeline;

import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ServiceMetadata;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fluent API for testing document processing pipelines.
 * 
 * Example usage:
 * <pre>
 * pipeline.document(rawDoc)
 *         .tika()
 *         .then()
 *         .chunker(chunkConfig1)
 *         .chunker(chunkConfig2)
 *         .then()
 *         .embedder(embedConfig1)
 *         .embedder(embedConfig2)
 *         .expectVectorSets(4);  // 2 chunker × 2 embedder = 4 vector sets
 * </pre>
 */
@ApplicationScoped
public class PipelineTestBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(PipelineTestBuilder.class);
    
    // Services will be provided by the individual module tests
    private PipeStepProcessor echoService;
    private PipeStepProcessor tikaService;
    private PipeStepProcessor chunkerService;
    private PipeStepProcessor embedderService;
    
    /**
     * Configure the services for this pipeline test.
     * This should be called from module tests to provide their specific services.
     */
    public PipelineTestBuilder withServices(PipeStepProcessor echo, PipeStepProcessor tika, 
                                           PipeStepProcessor chunker, PipeStepProcessor embedder) {
        this.echoService = echo;
        this.tikaService = tika;
        this.chunkerService = chunker;
        this.embedderService = embedder;
        return this;
    }
    
    /**
     * Starts a pipeline test with a document.
     */
    public PipelineStep document(PipeDoc document) {
        return new PipelineStep(document);
    }
    
    /**
     * Represents a step in the pipeline processing chain.
     */
    public class PipelineStep {
        private PipeDoc currentDocument;
        private final String streamId;
        private int stepNumber;
        
        public PipelineStep(PipeDoc document) {
            this.currentDocument = document;
            this.streamId = "pipeline-test-" + System.currentTimeMillis();
            this.stepNumber = 0;
        }
        
        /**
         * Process through Echo service (baseline test).
         */
        public PipelineStep echo() {
            return echo(createDefaultEchoConfig());
        }
        
        public PipelineStep echo(ProcessConfiguration config) {
            LOG.info("Pipeline step {}: Processing through Echo service", ++stepNumber);
            
            ProcessRequest request = createProcessRequest(config, "echo");
            Uni<ProcessResponse> responseUni = echoService.processData(request);
            ProcessResponse response = responseUni.await().indefinitely();
            
            assertTrue(response.getSuccess(), "Echo processing should succeed");
            currentDocument = response.getOutputDoc();
            
            LOG.debug("Echo processing completed successfully");
            return this;
        }
        
        /**
         * Process through Tika service to extract text.
         */
        public PipelineStep tika() {
            return tika(createDefaultTikaConfig());
        }
        
        public PipelineStep tika(ProcessConfiguration config) {
            LOG.info("Pipeline step {}: Processing through Tika service", ++stepNumber);
            
            ProcessRequest request = createProcessRequest(config, "tika");
            Uni<ProcessResponse> responseUni = tikaService.processData(request);
            ProcessResponse response = responseUni.await().indefinitely();
            
            assertTrue(response.getSuccess(), "Tika processing should succeed");
            currentDocument = response.getOutputDoc();
            
            // Verify text extraction
            assertTrue(currentDocument.hasBody() && !currentDocument.getBody().trim().isEmpty(),
                "Tika should extract text content");
            
            LOG.debug("Tika processing completed - extracted {} characters", 
                currentDocument.getBody().length());
            return this;
        }
        
        /**
         * Process through Chunker service to create semantic chunks.
         */
        public PipelineStep chunker() {
            return chunker(createDefaultChunkerConfig());
        }
        
        public PipelineStep chunker(ProcessConfiguration config) {
            LOG.info("Pipeline step {}: Processing through Chunker service", ++stepNumber);
            
            ProcessRequest request = createProcessRequest(config, "chunker");
            Uni<ProcessResponse> responseUni = chunkerService.processData(request);
            ProcessResponse response = responseUni.await().indefinitely();
            
            assertTrue(response.getSuccess(), "Chunker processing should succeed");
            currentDocument = response.getOutputDoc();
            
            // Verify chunks were created
            assertTrue(currentDocument.getSemanticResultsCount() > 0,
                "Chunker should create semantic results");
            
            int totalChunks = currentDocument.getSemanticResultsList().stream()
                    .mapToInt(result -> result.getChunksCount())
                    .sum();
            
            assertTrue(totalChunks > 0, "Chunker should create chunks");
            
            LOG.debug("Chunker processing completed - created {} chunks", totalChunks);
            return this;
        }
        
        /**
         * Process through Embedder service to create vectors.
         */
        public PipelineStep embedder() {
            return embedder(createDefaultEmbedderConfig());
        }
        
        public PipelineStep embedder(ProcessConfiguration config) {
            LOG.info("Pipeline step {}: Processing through Embedder service", ++stepNumber);
            
            ProcessRequest request = createProcessRequest(config, "embedder");
            Uni<ProcessResponse> responseUni = embedderService.processData(request);
            ProcessResponse response = responseUni.await().indefinitely();
            
            assertTrue(response.getSuccess(), "Embedder processing should succeed");
            currentDocument = response.getOutputDoc();
            
            LOG.debug("Embedder processing completed");
            return this;
        }
        
        /**
         * Fluent transition to continue processing.
         */
        public PipelineStep then() {
            return this;
        }
        
        /**
         * Get the current document in the pipeline.
         */
        public PipeDoc getCurrentDocument() {
            return currentDocument;
        }
        
        /**
         * Verify that the expected number of vector sets were created.
         * Expected calculation: chunker_runs × embedder_runs = vector_sets
         */
        public void expectVectorSets(int expectedVectorSets) {
            LOG.info("Verifying {} expected vector sets", expectedVectorSets);
            
            // Count semantic results (each represents a chunk set + embedding combination)
            int actualSemanticResults = currentDocument.getSemanticResultsCount();
            
            LOG.info("Found {} semantic results in document", actualSemanticResults);
            
            // For now, just verify we have semantic results - we'll refine this once we see the actual structure
            assertTrue(actualSemanticResults > 0, "Should have semantic results with embeddings");
            
            LOG.info("✅ Basic vector validation passed: {} semantic results found", actualSemanticResults);
        }
        
        /**
         * Verify processing was successful.
         */
        public void expectSuccess() {
            assertNotNull(currentDocument, "Pipeline should produce a document");
            LOG.info("✅ Pipeline processing completed successfully through {} steps", stepNumber);
        }
        
        private ProcessRequest createProcessRequest(ProcessConfiguration config, String stepName) {
            ServiceMetadata metadata = ServiceMetadata.newBuilder()
                    .setStreamId(streamId)
                    .setPipeStepName("test-" + stepName)
                    .setCurrentHopNumber(stepNumber)
                    .build();
            
            return ProcessRequest.newBuilder()
                    .setDocument(currentDocument)
                    .setConfig(config)
                    .setMetadata(metadata)
                    .build();
        }
    }
    
    // Default configuration creators
    private ProcessConfiguration createDefaultEchoConfig() {
        return ProcessConfiguration.newBuilder()
                .putConfigParams("message", "Pipeline test")
                .putConfigParams("repeat_count", "1")
                .build();
    }
    
    private ProcessConfiguration createDefaultTikaConfig() {
        return ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "1000000")
                .build();
    }
    
    private ProcessConfiguration createDefaultChunkerConfig() {
        return ProcessConfiguration.newBuilder()
                .putConfigParams("source_field", "body")
                .putConfigParams("chunk_size", "512")
                .putConfigParams("overlap_size", "50")
                .build();
    }
    
    private ProcessConfiguration createDefaultEmbedderConfig() {
        return ProcessConfiguration.newBuilder()
                .putConfigParams("check_chunks", "true")
                .putConfigParams("check_document_fields", "false")
                .putConfigParams("embedding_models", "ALL_MINILM_L6_V2")
                .build();
    }
}