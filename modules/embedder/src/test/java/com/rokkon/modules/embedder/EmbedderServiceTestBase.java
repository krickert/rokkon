package com.rokkon.modules.embedder;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.*;
import com.rokkon.test.data.ProtobufTestDataHelper;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class EmbedderServiceTestBase {

    private static final Logger log = LoggerFactory.getLogger(EmbedderServiceTestBase.class);

    protected abstract PipeStepProcessor getEmbedderService();

    @Test
    void testProcessData() {
        // Create a test document
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setBody("This is a test document body")
                .setTitle("Test Document")
                .build();

        // Create service metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("embedder-step")
                .setStreamId(UUID.randomUUID().toString())
                .setCurrentHopNumber(1)
                .putContextParams("tenant", "test-tenant")
                .build();

        // Create configuration
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("check_chunks", Value.newBuilder().setBoolValue(true).build())
                        .putFields("check_document_fields", Value.newBuilder().setBoolValue(true).build())
                        .build())
                .build();

        // Create request
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();

        // Execute and verify
        var response = getEmbedderService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        assertThat(response.getOutputDoc().getId()).isEqualTo(testDoc.getId());
        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("processed document fields"));
    }

    @Test
    void testProcessDataWithoutDocument() {
        // Test with no document - should still succeed but with error logs
        ProcessRequest request = ProcessRequest.newBuilder()
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("embedder-step")
                        .build())
                .setConfig(ProcessConfiguration.newBuilder().build())
                // No document set
                .build();

        var response = getEmbedderService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("Error in EmbedderService"));
    }

    @Test
    void testGetServiceRegistrationWithoutHealthCheck() {
        RegistrationRequest request = RegistrationRequest.newBuilder().build();
        var registration = getEmbedderService().getServiceRegistration(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(registration.getModuleName()).isEqualTo("embedder");
        assertThat(registration.hasJsonConfigSchema()).isTrue();
        assertThat(registration.getJsonConfigSchema()).contains("EmbedderOptions");
        assertThat(registration.getHealthCheckPassed()).isTrue();
        assertThat(registration.getHealthCheckMessage()).contains("No health check performed");
    }

    @Test
    void testGetServiceRegistrationWithHealthCheck() {
        // Create a test document for health check
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("health-check-doc")
                .setBody("Health check test for embedder")
                .setTitle("Health Check")
                .build();

        ProcessRequest processRequest = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("health-check")
                        .setPipeStepName("embedder-health")
                        .setStreamId("health-check-stream")
                        .build())
                .setConfig(ProcessConfiguration.newBuilder()
                        .setCustomJsonConfig(Struct.newBuilder()
                                .putFields("check_document_fields", Value.newBuilder().setBoolValue(true).build())
                                .build())
                        .build())
                .build();

        // Call with test request for health check
        RegistrationRequest request = RegistrationRequest.newBuilder()
                .setTestRequest(processRequest)
                .build();
        
        var registration = getEmbedderService().getServiceRegistration(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat(registration.getModuleName()).isEqualTo("embedder");
        assertThat(registration.hasJsonConfigSchema()).isTrue();
        assertThat(registration.getHealthCheckPassed()).isTrue();
        assertThat(registration.getHealthCheckMessage()).contains("Embedder module is healthy");
    }

    @Test
    void testProcessChunkerOutputData() {
        // Get chunker output data from test-utilities
        ProtobufTestDataHelper testHelper = new ProtobufTestDataHelper();
        Collection<PipeStream> chunkerOutputStreams = testHelper.getChunkerPipeStreams();

        // If no chunker output data is available, log a warning and skip the test
        if (chunkerOutputStreams.isEmpty()) {
            log.warn("No chunker output data available for testing. Skipping test.");
            return;
        }

        // Use the first chunker output stream for testing
        PipeStream chunkerOutputStream = chunkerOutputStreams.iterator().next();
        PipeDoc chunkerOutputDoc = chunkerOutputStream.getDocument();

        log.info("Testing embedder with chunker output document ID: {}", chunkerOutputDoc.getId());
        log.info("Chunker output document has {} semantic results", chunkerOutputDoc.getSemanticResultsCount());

        // Create service metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("embedder-step")
                .setStreamId(chunkerOutputStream.getStreamId())
                .setCurrentHopNumber(1)
                .putContextParams("tenant", "test-tenant")
                .build();

        // Create configuration with chunk processing enabled
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("check_chunks", Value.newBuilder().setBoolValue(true).build())
                        .putFields("check_document_fields", Value.newBuilder().setBoolValue(true).build())
                        .build())
                .build();

        // Create request with chunker output document
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(chunkerOutputDoc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();

        // Execute and verify
        var response = getEmbedderService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Verify the response
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        assertThat(response.getOutputDoc().getId()).isEqualTo(chunkerOutputDoc.getId());
        assertThat(response.getProcessorLogsList()).isNotEmpty();

        // Verify that chunks were processed
        PipeDoc outputDoc = response.getOutputDoc();
        assertThat(outputDoc.getSemanticResultsCount()).isGreaterThan(0);

        // Log the results
        log.info("Embedder successfully processed chunker output document");
        log.info("Output document has {} semantic results", outputDoc.getSemanticResultsCount());

        // Verify that embeddings were added to the chunks
        for (int i = 0; i < outputDoc.getSemanticResultsCount(); i++) {
            SemanticProcessingResult result = outputDoc.getSemanticResults(i);
            log.info("Semantic result {} has {} chunks", i, result.getChunksCount());

            for (int j = 0; j < result.getChunksCount(); j++) {
                SemanticChunk chunk = result.getChunks(j);
                ChunkEmbedding embeddingInfo = chunk.getEmbeddingInfo();

                // Verify that the chunk has a vector embedding
                assertThat(embeddingInfo.getVectorCount()).isGreaterThan(0);
                log.info("Chunk {} has {} vector dimensions", j, embeddingInfo.getVectorCount());
            }
        }
    }
}