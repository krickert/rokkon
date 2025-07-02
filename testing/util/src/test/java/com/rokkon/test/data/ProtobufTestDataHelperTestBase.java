package com.rokkon.test.data;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class ProtobufTestDataHelperTestBase {

    protected abstract ProtobufTestDataHelper getProtobufTestDataHelper();

    @Test
    void testTikaRequestStreamsLoading() {
        Collection<PipeStream> tikaRequests = getProtobufTestDataHelper().getTikaRequestStreams();
        
        assertThat(tikaRequests)
            .isNotNull()
            .isNotEmpty()
            .allSatisfy(stream -> {
                assertThat(stream).isNotNull();
                assertThat(stream.getStreamId()).isNotEmpty();
                assertThat(stream.hasDocument()).isTrue();
                assertThat(stream.getDocument().hasBlob()).isTrue();
            });
    }

    @Test
    void testTikaResponseDocumentsLoading() {
        Collection<PipeDoc> tikaResponses = getProtobufTestDataHelper().getTikaResponseDocuments();
        
        assertThat(tikaResponses)
            .isNotNull()
            .isNotEmpty()
            .allSatisfy(doc -> {
                assertThat(doc).isNotNull();
                assertThat(doc.getId()).isNotEmpty();
                // Tika responses should have extracted text in body
                assertThat(doc.hasBody()).isTrue();
                assertThat(doc.getBody()).isNotEmpty();
            });
    }

    @Test
    void testChunkerInputDocumentsLoading() {
        Collection<PipeDoc> chunkerInputs = getProtobufTestDataHelper().getChunkerInputDocuments();
        
        assertThat(chunkerInputs)
            .isNotNull()
            .isNotEmpty()
            .allSatisfy(doc -> {
                assertThat(doc).isNotNull();
                assertThat(doc.getId()).isNotEmpty();
                // Chunker inputs should have body content from Tika
                assertThat(doc.hasBody()).isTrue();
                assertThat(doc.getBody()).isNotEmpty();
            });
    }

    @Test
    void testLazyLoadingMechanism() {
        // First call should trigger loading
        Collection<PipeStream> firstCall = getProtobufTestDataHelper().getTikaRequestStreams();
        
        // Second call should return the same instance (lazy loaded)
        Collection<PipeStream> secondCall = getProtobufTestDataHelper().getTikaRequestStreams();
        
        assertThat(firstCall).isSameAs(secondCall);
    }

    @Test
    void testMapCreation() {
        Map<String, PipeDoc> tikaRequestMap = getProtobufTestDataHelper().getTikaPipeDocumentsMap();
        Collection<PipeDoc> tikaRequestDocs = getProtobufTestDataHelper().getTikaPipeDocuments();
        
        assertThat(tikaRequestMap)
            .isNotNull()
            .hasSize(tikaRequestDocs.size());
        
        // Verify all documents are in the map with correct keys
        tikaRequestDocs.forEach(doc -> 
            assertThat(tikaRequestMap).containsEntry(doc.getId(), doc)
        );
    }

    @Test
    void testOrderedSampleDocuments() {
        var orderedDocs = getProtobufTestDataHelper().getOrderedSamplePipeDocuments();
        
        assertThat(orderedDocs)
            .isNotNull();
        
        // Skip test if no sample documents are available
        if (orderedDocs.isEmpty()) {
            return;
        }
        
        assertThat(orderedDocs)
            .isNotEmpty()
            .isSortedAccordingTo((doc1, doc2) -> doc1.getId().compareTo(doc2.getId()));
    }

    @Test
    void testSampleDocumentByIndex() {
        var orderedDocs = getProtobufTestDataHelper().getOrderedSamplePipeDocuments();
        
        if (!orderedDocs.isEmpty()) {
            // Test valid index
            PipeDoc firstDoc = getProtobufTestDataHelper().getSamplePipeDocByIndex(0);
            assertThat(firstDoc).isEqualTo(orderedDocs.get(0));
            
            // Test last index
            PipeDoc lastDoc = getProtobufTestDataHelper().getSamplePipeDocByIndex(orderedDocs.size() - 1);
            assertThat(lastDoc).isEqualTo(orderedDocs.get(orderedDocs.size() - 1));
        }
    }

    @Test
    void testSampleDocumentByIndexOutOfBounds() {
        var orderedDocs = getProtobufTestDataHelper().getOrderedSamplePipeDocuments();
        
        // Test negative index
        assertThatThrownBy(() -> getProtobufTestDataHelper().getSamplePipeDocByIndex(-1))
            .isInstanceOf(IndexOutOfBoundsException.class);
        
        // Test index beyond size
        assertThatThrownBy(() -> getProtobufTestDataHelper().getSamplePipeDocByIndex(orderedDocs.size()))
            .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void testPipelineStages() {
        Set<String> stages = getProtobufTestDataHelper().getPipelineStages();
        
        // Verify we can load documents for each stage
        stages.forEach(stage -> {
            Collection<PipeDoc> stageDocs = getProtobufTestDataHelper().getPipelineGeneratedDocuments(stage);
            assertThat(stageDocs)
                .as("Documents for stage: " + stage)
                .isNotNull(); // May be empty for some stages
        });
    }

    @Test
    void testChunkerOutputStreams() {
        Collection<PipeStream> defaultChunks = getProtobufTestDataHelper().getChunkerOutputStreams();
        Collection<PipeStream> smallChunks = getProtobufTestDataHelper().getChunkerOutputStreamsSmall();
        Collection<PipeStream> allChunks = getProtobufTestDataHelper().getAllChunkerOutputStreams();
        
        // All chunks should contain both default and small
        assertThat(allChunks.size())
            .isEqualTo(defaultChunks.size() + smallChunks.size());
    }

    @Test
    void testTikaRequestDocumentsExtraction() {
        Collection<PipeDoc> tikaRequestDocs = getProtobufTestDataHelper().getTikaRequestDocuments();
        Collection<PipeStream> tikaRequestStreams = getProtobufTestDataHelper().getTikaRequestStreams();
        
        // Should have same number of documents as streams
        assertThat(tikaRequestDocs).hasSize(tikaRequestStreams.size());
        
        // Each document should have a blob
        assertThat(tikaRequestDocs)
            .allSatisfy(doc -> assertThat(doc.hasBlob()).isTrue());
    }

    @Test
    @Disabled("Thread safety test needs proper CDI injection to work correctly")
    void testThreadSafety() throws InterruptedException {
        // Test that lazy loading is thread-safe
        final int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        Collection<?>[] results = new Collection<?>[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = getProtobufTestDataHelper().getEmbedderInputDocuments();
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // All results should be the same instance
        for (int i = 1; i < threadCount; i++) {
            assertThat(results[i]).isSameAs(results[0]);
        }
    }

    @Test
    void testBinaryFileLoading() {
        // This test verifies that binary protobuf files can be loaded from the classpath
        // This is critical for the issue you mentioned about loading from JAR vs filesystem
        
        // Test loading from various directories
        Collection<PipeStream> tikaRequests = getProtobufTestDataHelper().getTikaRequestStreams();
        Collection<PipeDoc> chunkerInputs = getProtobufTestDataHelper().getChunkerInputDocuments();
        
        // Verify at least some data was loaded
        assertThat(tikaRequests).isNotEmpty();
        assertThat(chunkerInputs).isNotEmpty();
        
        // Verify the binary data was properly parsed
        tikaRequests.forEach(stream -> {
            assertThat(stream.getSerializedSize()).isGreaterThan(0);
            assertThat(stream.toByteArray()).isNotEmpty();
        });
    }
}