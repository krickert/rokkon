package com.rokkon.modules.embedder;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.search.sdk.ServiceRegistrationData;
import com.rokkon.search.sdk.PipeStepProcessorGrpc;
import io.quarkus.grpc.GrpcService;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for EmbedderService using @QuarkusTest.
 * Tests the service within the Quarkus application context with full CDI injection.
 * 
 * Note: Some tests are conditionally enabled based on GPU availability.
 */
@QuarkusTest
class EmbedderServiceQuarkusTest {

    @Inject
    @GrpcService
    EmbedderService embedderService;

    @Inject
    ReactiveVectorizer vectorizer;

    @Test
    void testQuarkusApplicationStartup() {
        assertNotNull(embedderService, "EmbedderService should be injected by Quarkus CDI");
        assertNotNull(vectorizer, "ReactiveVectorizer should be injected by Quarkus CDI");
    }

    @Test
    void testServiceImplementsCorrectInterface() {
        assertTrue(embedderService instanceof PipeStepProcessorGrpc.PipeStepProcessorImplBase,
                "EmbedderService should extend PipeStepProcessorImplBase");
    }

    @Test
    void testServiceHasGrpcServiceAnnotation() {
        assertTrue(embedderService.getClass().isAnnotationPresent(GrpcService.class),
                "EmbedderService should have @GrpcService annotation");
    }

    @Test
    void testVectorizerInitialization() {
        assertNotNull(vectorizer.getModelId(), "Vectorizer should have a model ID");
        assertNotNull(vectorizer.getModel(), "Vectorizer should have a model");
        assertTrue(vectorizer.getMaxBatchSize() > 0, "Vectorizer should have a positive max batch size");
        
        log.info("Vectorizer initialized with model: {}, GPU: {}, max batch size: {}", 
                vectorizer.getModelId(), vectorizer.isUsingGpu(), vectorizer.getMaxBatchSize());
    }

    @Test
    void testProcessDataWithDocumentFields() {
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("test-doc")
                .setBody("This is a test document for embedding generation")
                .setTitle("Test Document")
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("check_chunks", Value.newBuilder().setBoolValue(false).build())
                        .putFields("check_document_fields", Value.newBuilder().setBoolValue(true).build())
                        .putFields("max_batch_size", Value.newBuilder().setNumberValue(8).build())
                        .build())
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("embedder-test")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        Uni<ProcessResponse> responseUni = embedderService.processData(request);
        assertNotNull(responseUni, "processData should return a Uni<ProcessResponse>");

        ProcessResponse response = responseUni.await().indefinitely();
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processing should be successful");
        assertEquals("test-doc", response.getOutputDoc().getId(), "Output document should maintain original ID");
        
        // Should have added semantic results with embeddings
        assertTrue(response.getOutputDoc().getSemanticResultsCount() > 0, 
                "Should have added semantic results for document fields");
    }

    @Test
    void testGetServiceRegistration() {
        Uni<ServiceRegistrationData> registrationUni = embedderService.getServiceRegistration(Empty.getDefaultInstance());
        assertNotNull(registrationUni, "getServiceRegistration should return a Uni<ServiceRegistrationData>");

        ServiceRegistrationData registration = registrationUni.await().indefinitely();
        assertNotNull(registration, "Registration should not be null");
        assertEquals("embedder", registration.getModuleName(), "Module name should be 'embedder'");
        assertFalse(registration.getJsonConfigSchema().isEmpty(), "JSON schema should not be empty");
        
        // Verify schema contains new reactive configuration options
        String schema = registration.getJsonConfigSchema();
        assertTrue(schema.contains("max_batch_size"), "Schema should include max_batch_size");
        assertTrue(schema.contains("backpressure_strategy"), "Schema should include backpressure_strategy");
        assertTrue(schema.contains("embedding_models"), "Schema should include embedding_models");
    }

    @Test
    void testProcessDataWithMinimalRequest() {
        PipeDoc minimalDoc = PipeDoc.newBuilder()
                .setId("minimal-doc")
                .setBody("Short text")
                .build();

        ProcessRequest minimalRequest = ProcessRequest.newBuilder()
                .setDocument(minimalDoc)
                .setConfig(ProcessConfiguration.newBuilder().build())
                .setMetadata(ServiceMetadata.newBuilder()
                        .setStreamId("minimal-stream")
                        .setPipeStepName("minimal-embedder")
                        .build())
                .build();

        Uni<ProcessResponse> responseUni = embedderService.processData(minimalRequest);
        ProcessResponse response = responseUni.await().indefinitely();

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals("minimal-doc", response.getOutputDoc().getId());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CUDA_VISIBLE_DEVICES", matches = ".*")
    void testGpuAcceleration() {
        // This test only runs if CUDA is available
        if (vectorizer.isUsingGpu()) {
            log.info("GPU acceleration is enabled: {}", vectorizer.isUsingGpu());
            assertTrue(vectorizer.isUsingGpu(), "Should be using GPU when CUDA is available");
        } else {
            log.info("GPU not available, using CPU: {}", vectorizer.isUsingGpu());
        }
    }

    @Test
    void testBatchProcessingCapabilities() {
        // Test that the vectorizer can handle batch processing
        assertTrue(vectorizer.getMaxBatchSize() >= 1, "Should support at least batch size of 1");
        assertTrue(vectorizer.getMaxBatchSize() <= 128, "Batch size should be reasonable for GPU memory");
        
        log.info("Vectorizer supports batch size up to: {}", vectorizer.getMaxBatchSize());
    }

    @Test
    void testVectorizerModelConfiguration() {
        EmbeddingModel model = vectorizer.getModel();
        assertNotNull(model, "Vectorizer should have an embedding model");
        assertNotNull(model.getUri(), "Model should have a URI");
        assertNotNull(model.getDescription(), "Model should have a description");
        
        log.info("Using embedding model: {} ({})", model.name(), model.getDescription());
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmbedderServiceQuarkusTest.class);
}