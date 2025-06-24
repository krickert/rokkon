package com.rokkon.pipeline.chunker;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.pipeline.utils.ProcessingBuffer;
import com.rokkon.pipeline.utils.ProcessingBufferFactory;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Producer for ProcessingBuffer instances used by the chunker service.
 * This allows capturing output documents during test runs.
 */
@Singleton
public class ProcessingBufferProducer {
    private static final Logger LOG = Logger.getLogger(ProcessingBufferProducer.class);

    @ConfigProperty(name = "processing.buffer.enabled", defaultValue = "false")
    boolean bufferEnabled;

    @ConfigProperty(name = "processing.buffer.capacity", defaultValue = "1000")
    int bufferCapacity;

    @ConfigProperty(name = "processing.buffer.directory", defaultValue = "target/test-data")
    String bufferDirectory;

    /**
     * Produces the output buffer for PipeDoc instances.
     * This buffer captures processed documents after chunking.
     */
    @Produces
    @Singleton
    public ProcessingBuffer<PipeDoc> createPipeDocOutputBuffer() {
        ProcessingBuffer<PipeDoc> buffer = ProcessingBufferFactory.createBuffer(
                bufferEnabled,
                bufferCapacity,
                PipeDoc.class
        );
        
        if (bufferEnabled) {
            LOG.infof("Processing buffer enabled with capacity %d and directory %s", 
                    bufferCapacity, bufferDirectory);
        }
        
        return buffer;
    }
}