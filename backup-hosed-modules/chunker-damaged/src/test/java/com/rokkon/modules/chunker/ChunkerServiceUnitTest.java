package com.rokkon.modules.chunker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.SemanticProcessingResult;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChunkerServiceUnitTest {

    @Mock
    private ObjectMapper objectMapper;
    
    @Mock
    private OverlapChunker overlapChunker;
    
    @Mock
    private ChunkMetadataExtractor metadataExtractor;

    private ChunkerService chunkerService;

    @BeforeEach
    void setUp() {
        chunkerService = new ChunkerService();
        chunkerService.objectMapper = objectMapper;
        chunkerService.overlapChunker = overlapChunker;
        chunkerService.metadataExtractor = metadataExtractor;
    }

    @Test
    void testProcessDataWithValidRequest() throws Exception {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("test-doc-1")
                .setBody("This is a test document with some content to chunk.")
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("source_field", Value.newBuilder().setStringValue("body").build())
                        .putFields("chunk_size", Value.newBuilder().setNumberValue(50).build())
                        .putFields("chunk_overlap", Value.newBuilder().setNumberValue(10).build())
                        .build())
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("chunker")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        ChunkerOptions expectedOptions = new ChunkerOptions("body", 50, 10, 
                ChunkerOptions.DEFAULT_CHUNK_ID_TEMPLATE,
                ChunkerOptions.DEFAULT_CHUNK_CONFIG_ID,
                ChunkerOptions.DEFAULT_RESULT_SET_NAME_TEMPLATE,
                ChunkerOptions.DEFAULT_LOG_PREFIX,
                ChunkerOptions.DEFAULT_PRESERVE_URLS);

        List<Chunk> mockChunks = Arrays.asList(
                new Chunk("test-stream_test-doc-1_chunk_0", "This is a test document", 0, 22),
                new Chunk("test-stream_test-doc-1_chunk_1", "document with some content", 17, 43)
        );

        ChunkingResult mockResult = new ChunkingResult(mockChunks, Collections.emptyMap());

        when(objectMapper.readValue(anyString(), eq(ChunkerOptions.class))).thenReturn(expectedOptions);
        when(overlapChunker.createChunks(eq(inputDoc), any(ChunkerOptions.class), eq("test-stream"), eq("chunker")))
                .thenReturn(mockResult);

        Map<String, Value> mockMetadata = new HashMap<>();
        mockMetadata.put("word_count", Value.newBuilder().setNumberValue(5).build());
        mockMetadata.put("character_count", Value.newBuilder().setNumberValue(22).build());

        when(metadataExtractor.extractAllMetadata(anyString(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(mockMetadata);

        Uni<ProcessResponse> responseUni = chunkerService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();

        assertTrue(response.getSuccess());
        assertFalse(response.getProcessorLogsList().isEmpty());
        assertEquals(inputDoc.getId(), response.getOutputDoc().getId());
        assertEquals(1, response.getOutputDoc().getSemanticResultsCount());

        SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
        assertEquals("body", result.getSourceFieldName());
        assertEquals(2, result.getChunksCount());

        verify(overlapChunker).createChunks(eq(inputDoc), any(ChunkerOptions.class), eq("test-stream"), eq("chunker"));
        verify(metadataExtractor, times(2)).extractAllMetadata(anyString(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void testProcessDataWithNullRequest() {
        Uni<ProcessResponse> responseUni = chunkerService.processData(null);
        ProcessResponse response = responseUni.await().indefinitely();

        assertFalse(response.getSuccess());
        assertFalse(response.getProcessorLogsList().isEmpty());
        assertTrue(response.getProcessorLogs(0).contains("Request cannot be null"));
    }

    @Test
    void testProcessDataWithNoCustomConfig() throws Exception {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("test-doc-2")
                .setBody("Test content")
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder().build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("chunker")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        List<Chunk> mockChunks = Arrays.asList(
                new Chunk("test-stream_test-doc-2_chunk_0", "Test content", 0, 12)
        );

        ChunkingResult mockResult = new ChunkingResult(mockChunks, Collections.emptyMap());

        when(overlapChunker.createChunks(eq(inputDoc), any(ChunkerOptions.class), eq("test-stream"), eq("chunker")))
                .thenReturn(mockResult);

        Map<String, Value> mockMetadata = new HashMap<>();
        mockMetadata.put("word_count", Value.newBuilder().setNumberValue(2).build());

        when(metadataExtractor.extractAllMetadata(anyString(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(mockMetadata);

        Uni<ProcessResponse> responseUni = chunkerService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();

        assertTrue(response.getSuccess());
        verify(overlapChunker).createChunks(eq(inputDoc), any(ChunkerOptions.class), eq("test-stream"), eq("chunker"));
    }

    @Test
    void testProcessDataWithEmptyChunks() throws Exception {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("test-doc-3")
                .setBody("")
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder().build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("chunker")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        ChunkingResult mockResult = new ChunkingResult(Collections.emptyList(), Collections.emptyMap());

        when(overlapChunker.createChunks(eq(inputDoc), any(ChunkerOptions.class), eq("test-stream"), eq("chunker")))
                .thenReturn(mockResult);

        Uni<ProcessResponse> responseUni = chunkerService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();

        assertTrue(response.getSuccess());
        assertTrue(response.getProcessorLogs(0).contains("No content"));
        assertEquals(0, response.getOutputDoc().getSemanticResultsCount());
    }

    @Test
    void testGetServiceRegistration() {
        Uni<ServiceRegistrationData> registrationUni = chunkerService.getServiceRegistration(null);
        ServiceRegistrationData registration = registrationUni.await().indefinitely();

        assertEquals("chunker", registration.getModuleName());
        assertFalse(registration.getJsonConfigSchema().isEmpty());
        assertTrue(registration.getJsonConfigSchema().contains("ChunkerOptions"));
    }
}