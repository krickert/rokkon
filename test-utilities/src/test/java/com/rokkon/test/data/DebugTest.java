package com.rokkon.test.data;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@Disabled("Debug test - only enable when debugging test data loading issues")
class DebugTest {

    @Inject
    ProtobufTestDataHelper helper;

    @Test
    void debugTikaRequestLoading() {
        System.out.println("=== DEBUG: Testing All Test Data Directories ===");
        
        // Test Tika requests
        System.out.println("\n1. TIKA REQUESTS:");
        var tikaRequests = helper.getTikaRequestStreams();
        System.out.println("   Loaded count: " + tikaRequests.size());
        assertThat(tikaRequests).as("Tika request streams").isNotEmpty();
        
        // Test Tika responses
        System.out.println("\n2. TIKA RESPONSES:");
        var tikaResponses = helper.getTikaResponseDocuments();
        System.out.println("   Loaded count: " + tikaResponses.size());
        assertThat(tikaResponses).as("Tika response documents").isNotEmpty();
        
        // Test Chunker input
        System.out.println("\n3. CHUNKER INPUT:");
        var chunkerInput = helper.getChunkerInputDocuments();
        System.out.println("   Loaded count: " + chunkerInput.size());
        assertThat(chunkerInput).as("Chunker input documents").isNotEmpty();
        
        // Test Chunker output
        System.out.println("\n4. CHUNKER OUTPUT:");
        var chunkerOutput = helper.getChunkerOutputStreams();
        System.out.println("   Loaded count: " + chunkerOutput.size());
        // Note: chunker output might be empty if not generated yet
        
        // Test Sample documents (might not exist)
        System.out.println("\n5. SAMPLE DOCUMENTS:");
        var sampleDocs = helper.getSamplePipeDocuments();
        System.out.println("   Loaded count: " + sampleDocs.size());
        
        // Test Embedder input
        System.out.println("\n6. EMBEDDER INPUT:");
        var embedderInput = helper.getEmbedderInputDocuments();
        System.out.println("   Loaded count: " + embedderInput.size());
        
        // Test Embedder output
        System.out.println("\n7. EMBEDDER OUTPUT:");
        var embedderOutput = helper.getEmbedderOutputDocuments();
        System.out.println("   Loaded count: " + embedderOutput.size());
        
        System.out.println("\n=== Summary ===");
        System.out.println("Tika requests loaded: " + tikaRequests.size());
        System.out.println("Tika responses loaded: " + tikaResponses.size());
        System.out.println("Chunker input loaded: " + chunkerInput.size());
        System.out.println("Chunker output loaded: " + chunkerOutput.size());
        System.out.println("Sample documents loaded: " + sampleDocs.size());
        System.out.println("Embedder input loaded: " + embedderInput.size());
        System.out.println("Embedder output loaded: " + embedderOutput.size());
    }
}