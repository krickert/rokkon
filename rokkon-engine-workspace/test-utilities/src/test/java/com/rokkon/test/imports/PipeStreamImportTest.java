package com.rokkon.test.imports;

import com.rokkon.search.model.PipeStream;
import com.rokkon.search.model.PipeDoc;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify that PipeStream imports are working correctly from proto-definitions
 */
public class PipeStreamImportTest {

    @Test
    void testPipeStreamImport() {
        // This test verifies that we can import and use PipeStream from the correct package
        PipeStream.Builder builder = PipeStream.newBuilder();
        builder.setStreamId("test-stream-id");
        builder.setCurrentPipelineName("test-pipeline");
        builder.setTargetStepName("test-step");
        builder.setCurrentHopNumber(1);
        
        PipeDoc.Builder docBuilder = PipeDoc.newBuilder();
        docBuilder.setId("test-doc-id");
        builder.setDocument(docBuilder.build());
        
        PipeStream pipeStream = builder.build();
        
        assertThat(pipeStream.getStreamId()).isEqualTo("test-stream-id");
        assertThat(pipeStream.getCurrentPipelineName()).isEqualTo("test-pipeline");
        assertThat(pipeStream.getTargetStepName()).isEqualTo("test-step");
        assertThat(pipeStream.getCurrentHopNumber()).isEqualTo(1);
        assertThat(pipeStream.hasDocument()).isTrue();
        assertThat(pipeStream.getDocument().getId()).isEqualTo("test-doc-id");
    }
}