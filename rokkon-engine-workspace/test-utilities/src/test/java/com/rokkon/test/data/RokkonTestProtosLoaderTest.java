package com.rokkon.test.data;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class RokkonTestProtosLoaderTest {

    @Inject
    RokkonTestProtosLoader loader;

    @Test
    void testLoadTikaRequestStreams() {
        Collection<PipeStream> streams = loader.loadPipeStreamsFromDirectory("test-data/tika/requests", "bin");
        
        System.out.println("Loaded " + streams.size() + " tika request streams");
        
        assertThat(streams).isNotEmpty();
        
        // Print first few filenames we're trying to load
        System.out.println("Sample stream IDs:");
        streams.stream().limit(5).forEach(s -> 
            System.out.println("  - " + s.getStreamId())
        );
    }

    @Test
    void testLoadTikaResponseDocs() {
        Collection<PipeDoc> docs = loader.loadPipeDocsFromDirectory("test-data/tika/responses", "bin");
        
        System.out.println("Loaded " + docs.size() + " tika response docs");
        
        assertThat(docs).isNotEmpty();
    }
}