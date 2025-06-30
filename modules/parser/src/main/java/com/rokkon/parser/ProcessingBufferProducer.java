package com.rokkon.parser;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.pipeline.util.ProcessingBuffer;
import com.rokkon.pipeline.util.ProcessingBufferFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Producer for ProcessingBuffer instances in the parser module.
 * This class creates a singleton ProcessingBuffer instance with configuration
 * injected from application properties.
 */
@Singleton
public class ProcessingBufferProducer {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessingBufferProducer.class);

    // Configuration properties injected via constructor
    private final boolean bufferEnabled;
    private final int bufferCapacity;
    private final Path bufferDirectory;
    private final String bufferPrefix;
    private final int bufferPrecision;
    
    // Calculate precision based on capacity
    private static int calculatePrecision(int capacity) {
        if (capacity <= 0) return 1;
        return Math.max(1, (int) Math.ceil(Math.log10(capacity)));
    }

    @Inject
    public ProcessingBufferProducer(
            @ConfigProperty(name = "processing.buffer.enabled", defaultValue = "false") boolean bufferEnabled,
            @ConfigProperty(name = "processing.buffer.capacity", defaultValue = "200") int bufferCapacity,
            @ConfigProperty(name = "processing.buffer.directory") Optional<String> bufferDirectoryPath,
            @ConfigProperty(name = "processing.buffer.prefix", defaultValue = "parser_output") String bufferPrefix) {
        this.bufferEnabled = bufferEnabled;
        this.bufferCapacity = bufferCapacity;
        this.bufferDirectory = bufferDirectoryPath.isPresent() ? 
                Paths.get(bufferDirectoryPath.get()) : 
                Paths.get(System.getProperty("java.io.tmpdir"));
        this.bufferPrefix = bufferPrefix;
        // Calculate precision dynamically based on capacity
        this.bufferPrecision = calculatePrecision(bufferCapacity);
        
        if (bufferEnabled) {
            LOG.info("Processing buffer enabled with capacity {} and directory {}", 
                    bufferCapacity, this.bufferDirectory);
        } else {
            LOG.info("Processing buffer disabled");
        }
    }

    @Produces
    @Singleton
    @jakarta.inject.Named("outputBuffer")
    public ProcessingBuffer<PipeDoc> createPipeDocOutputBuffer() {
        if (bufferEnabled) {
            return ProcessingBufferFactory.createBuffer(
                    true,
                    bufferCapacity,
                    PipeDoc.class,
                    bufferDirectory,
                    bufferPrefix + "_output",
                    bufferPrecision
            );
        } else {
            return ProcessingBufferFactory.createBuffer(
                    false,
                    bufferCapacity,
                    PipeDoc.class
            );
        }
    }
    
    @Produces
    @Singleton
    @jakarta.inject.Named("inputBuffer")
    public ProcessingBuffer<PipeStream> createPipeStreamInputBuffer() {
        if (bufferEnabled) {
            return ProcessingBufferFactory.createBuffer(
                    true,
                    bufferCapacity,
                    PipeStream.class,
                    bufferDirectory,
                    bufferPrefix + "_input",
                    bufferPrecision
            );
        } else {
            return ProcessingBufferFactory.createBuffer(
                    false,
                    bufferCapacity,
                    PipeStream.class
            );
        }
    }
}