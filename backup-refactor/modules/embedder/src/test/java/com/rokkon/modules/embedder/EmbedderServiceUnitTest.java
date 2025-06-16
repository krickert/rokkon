package com.rokkon.modules.embedder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.SemanticProcessingResult;
import com.rokkon.search.model.SemanticChunk;
import com.rokkon.search.model.ChunkEmbedding;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.search.sdk.ServiceRegistrationData;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmbedderService using Mockito for mocking dependencies.
 * This test creates the service manually and injects mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class EmbedderServiceUnitTest {

    @Mock
    private ObjectMapper objectMapper;
    
    @Mock
    private ReactiveVectorizer vectorizer;

    private EmbedderService embedderService;

    @BeforeEach
    void setUp() {
        embedderService = new EmbedderService();
        embedderService.objectMapper = objectMapper;
        embedderService.vectorizer = vectorizer;
    }

    @Test
    void testProcessDataWithValidChunks() throws Exception {
        // Create test chunk
        ChunkEmbedding chunkEmbedding = ChunkEmbedding.newBuilder()
                .setChunkId("chunk-1")
                .setTextContent("This is a test chunk")
                .setOriginalCharStartOffset(0)
                .setOriginalCharEndOffset(20)
                .setChunkConfigId("test-config")
                .build();

        SemanticChunk chunk = SemanticChunk.newBuilder()
                .setChunkId("chunk-1")
                .setChunkNumber(0)
                .setEmbeddingInfo(chunkEmbedding)
                .build();

        SemanticProcessingResult semanticResult = SemanticProcessingResult.newBuilder()
                .setResultId("result-1")
                .setResultSetName("test-chunks")
                .setSourceFieldName("body")
                .addChunks(chunk)
                .build();

        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("test-doc-1")
                .setBody("This is a test document with some content.")
                .addSemanticResults(semanticResult)
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("check_chunks", Value.newBuilder().setBoolValue(true).build())
                        .putFields("embedding_models", Value.newBuilder().setListValue(
                                com.google.protobuf.ListValue.newBuilder()
                                        .addValues(Value.newBuilder().setStringValue("ALL_MINILM_L6_V2").build())
                                        .build()).build())
                        .build())
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("embedder")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        EmbedderOptions expectedOptions = new EmbedderOptions();
        List<float[]> mockEmbeddings = Arrays.asList(new float[]{0.1f, 0.2f, 0.3f});

        when(objectMapper.readValue(anyString(), eq(EmbedderOptions.class))).thenReturn(expectedOptions);
        when(vectorizer.batchEmbeddings(anyList())).thenReturn(Uni.createFrom().item(mockEmbeddings));
        when(vectorizer.getModelId()).thenReturn("ALL_MINILM_L6_V2");
        when(vectorizer.getModel()).thenReturn(EmbeddingModel.ALL_MINILM_L6_V2);
        when(vectorizer.isUsingGpu()).thenReturn(true);

        Uni<ProcessResponse> responseUni = embedderService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();

        assertTrue(response.getSuccess());
        assertFalse(response.getProcessorLogsList().isEmpty());
        assertEquals(inputDoc.getId(), response.getOutputDoc().getId());
        assertTrue(response.getProcessorLogs(0).contains("Successfully processed chunks"));
        assertTrue(response.getProcessorLogs(0).contains("GPU: true"));

        verify(vectorizer).batchEmbeddings(anyList());
    }

    @Test
    void testProcessDataWithDocumentFields() throws Exception {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("test-doc-2")
                .setBody("Test document content")
                .setTitle("Test Title")
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("check_chunks", Value.newBuilder().setBoolValue(false).build())
                        .putFields("check_document_fields", Value.newBuilder().setBoolValue(true).build())
                        .build())
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("embedder")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        // Configure options to only process document fields (not chunks)
        EmbedderOptions expectedOptions = new EmbedderOptions(
                List.of(EmbeddingModel.ALL_MINILM_L6_V2),
                false, // checkChunks = false
                true,  // checkDocumentFields = true
                List.of("body", "title"),
                List.of(),
                false,
                List.of(1),
                512,
                "",
                "%s_embeddings_%s",
                32,
                "DROP_OLDEST"
        );
        float[] mockEmbedding = new float[]{0.1f, 0.2f, 0.3f};

        when(objectMapper.readValue(anyString(), eq(EmbedderOptions.class))).thenReturn(expectedOptions);
        when(vectorizer.embeddings(anyString())).thenReturn(Uni.createFrom().item(mockEmbedding));
        lenient().when(vectorizer.getModelId()).thenReturn("ALL_MINILM_L6_V2");
        lenient().when(vectorizer.getModel()).thenReturn(EmbeddingModel.ALL_MINILM_L6_V2);
        lenient().when(vectorizer.isUsingGpu()).thenReturn(false);

        Uni<ProcessResponse> responseUni = embedderService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();

        assertTrue(response.getSuccess());
        assertTrue(response.getProcessorLogs(0).contains("Successfully processed document fields"));
        assertTrue(response.getProcessorLogs(0).contains("GPU: false"));

        verify(vectorizer, atLeastOnce()).embeddings(anyString());
    }

    @Test
    void testProcessDataWithNullRequest() {
        Uni<ProcessResponse> responseUni = embedderService.processData(null);
        ProcessResponse response = responseUni.await().indefinitely();

        assertFalse(response.getSuccess());
        assertFalse(response.getProcessorLogsList().isEmpty());
        assertTrue(response.getProcessorLogs(0).contains("Request cannot be null"));
    }

    @Test
    void testProcessDataWithNoProcessingEnabled() throws Exception {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("test-doc-3")
                .setBody("Test content")
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("check_chunks", Value.newBuilder().setBoolValue(false).build())
                        .putFields("check_document_fields", Value.newBuilder().setBoolValue(false).build())
                        .build())
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("embedder")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        // Configure options with no processing enabled
        EmbedderOptions expectedOptions = new EmbedderOptions(
                List.of(EmbeddingModel.ALL_MINILM_L6_V2),
                false, // checkChunks = false
                false, // checkDocumentFields = false
                List.of("body", "title"),
                List.of(),
                false,
                List.of(1),
                512,
                "",
                "%s_embeddings_%s",
                32,
                "DROP_OLDEST"
        );
        when(objectMapper.readValue(anyString(), eq(EmbedderOptions.class))).thenReturn(expectedOptions);

        Uni<ProcessResponse> responseUni = embedderService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();

        assertTrue(response.getSuccess());
        assertTrue(response.getProcessorLogs(0).contains("No processing configured"));

        verify(vectorizer, never()).embeddings(anyString());
        verify(vectorizer, never()).batchEmbeddings(anyList());
    }

    @Test
    void testGetServiceRegistration() {
        when(vectorizer.isUsingGpu()).thenReturn(true);

        Uni<ServiceRegistrationData> registrationUni = embedderService.getServiceRegistration(null);
        ServiceRegistrationData registration = registrationUni.await().indefinitely();

        assertEquals("embedder", registration.getModuleName());
        assertFalse(registration.getJsonConfigSchema().isEmpty());
        assertTrue(registration.getJsonConfigSchema().contains("EmbedderOptions"));
        assertTrue(registration.getJsonConfigSchema().contains("embedding_models"));
        assertTrue(registration.getJsonConfigSchema().contains("max_batch_size"));
        assertTrue(registration.getJsonConfigSchema().contains("backpressure_strategy"));
    }


    @Test
    void testVectorizerErrorHandling() throws Exception {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("test-doc-error")
                .setBody("Test content")
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("check_document_fields", Value.newBuilder().setBoolValue(true).build())
                        .build())
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("embedder")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        // Configure options to process document fields (which will trigger the error)
        EmbedderOptions expectedOptions = new EmbedderOptions(
                List.of(EmbeddingModel.ALL_MINILM_L6_V2),
                false, // checkChunks = false
                true,  // checkDocumentFields = true
                List.of("body", "title"),
                List.of(),
                false,
                List.of(1),
                512,
                "",
                "%s_embeddings_%s",
                32,
                "DROP_OLDEST"
        );
        when(objectMapper.readValue(anyString(), eq(EmbedderOptions.class))).thenReturn(expectedOptions);
        when(vectorizer.embeddings(anyString())).thenReturn(
                Uni.createFrom().failure(new RuntimeException("GPU memory error")));

        Uni<ProcessResponse> responseUni = embedderService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();

        assertFalse(response.getSuccess());
        assertTrue(response.getProcessorLogs(0).contains("Error in EmbedderService"));
        assertTrue(response.hasErrorDetails());
    }
}