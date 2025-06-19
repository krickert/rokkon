package com.rokkon.modules.embedder;

import com.rokkon.pipeline.utils.ProcessingBuffer;
import com.rokkon.pipeline.utils.ProcessingBufferFactory;
import com.rokkon.search.model.PipeDoc;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer for ProcessingBuffer instances used in the embedder module.
 */
@Singleton
public class ProcessingBufferProducer {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessingBufferProducer.class);

    @ConfigProperty(name = "processing.buffer.enabled", defaultValue = "false")
    boolean bufferEnabled;

    @ConfigProperty(name = "processing.buffer.capacity", defaultValue = "1000")
    int bufferCapacity;

    @ConfigProperty(name = "processing.buffer.directory", defaultValue = "target/test-data")
    String bufferDirectory;

    /**
     * Produces the output buffer for PipeDoc instances.
     * This buffer captures processed documents after embedding.
     */
    @Produces
    @Singleton
    public ProcessingBuffer<PipeDoc> createPipeDocOutputBuffer() {
        ProcessingBuffer<PipeDoc> buffer = ProcessingBufferFactory.createBuffer(
                bufferEnabled,
                bufferCapacity,
                PipeDoc.class
        );
        
        LOG.info("Processing buffer enabled: {}", bufferEnabled);
        if (bufferEnabled && bufferDirectory != null && !bufferDirectory.isEmpty()) {
            LOG.info("Processing buffer will save to: {}", bufferDirectory);
        }
        
        return buffer;
    }
}