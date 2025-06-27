package com.rokkon.search.util;

import com.google.protobuf.InvalidProtocolBufferException;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.search.sdk.ProcessResponse;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class to load sample PipeStream data from resources.
 * These samples are used for testing throughout the application.
 */
public class SampleDataLoader {
    
    private static final String SAMPLE_PATH = "/sample-data/";
    private static final String DEFAULT_SAMPLE = "sample-pipestream-1.bin";
    
    /**
     * Loads the default sample PipeStream from resources.
     * @return PipeStream object
     * @throws IOException if the resource cannot be read
     */
    public static PipeStream loadDefaultSamplePipeStream() throws IOException {
        return loadSamplePipeStream(DEFAULT_SAMPLE);
    }
    
    /**
     * Loads a specific sample PipeStream by filename.
     * @param filename the filename (e.g., "sample-pipestream-1.bin")
     * @return PipeStream object
     * @throws IOException if the resource cannot be read
     */
    public static PipeStream loadSamplePipeStream(String filename) throws IOException {
        try (InputStream is = SampleDataLoader.class.getResourceAsStream(SAMPLE_PATH + filename)) {
            if (is == null) {
                throw new IOException("Sample file not found: " + filename);
            }
            return PipeStream.parseFrom(is);
        } catch (InvalidProtocolBufferException e) {
            throw new IOException("Failed to parse PipeStream from " + filename, e);
        }
    }
    
    /**
     * Loads the default sample PipeDoc by extracting it from the default PipeStream.
     * @return PipeDoc object or null if the PipeStream doesn't contain a document
     * @throws IOException if the resource cannot be read
     */
    public static PipeDoc loadDefaultSamplePipeDoc() throws IOException {
        PipeStream stream = loadDefaultSamplePipeStream();
        return stream.hasDocument() ? stream.getDocument() : null;
    }
    
    /**
     * Gets the PipeDoc from a specific sample PipeStream.
     * @param filename the filename (e.g., "sample-pipestream-1.bin")
     * @return PipeDoc object or null if the PipeStream doesn't contain a document
     * @throws IOException if the resource cannot be read
     */
    public static PipeDoc loadSamplePipeDoc(String filename) throws IOException {
        PipeStream stream = loadSamplePipeStream(filename);
        return stream.hasDocument() ? stream.getDocument() : null;
    }
    
    /**
     * Lists all available sample files.
     * @return array of sample filenames
     */
    public static String[] getAvailableSamples() {
        return new String[] {
            "sample-pipestream-1.bin",
            "sample-pipestream-2.bin", 
            "sample-pipestream-3.bin"
        };
    }
    
    /**
     * Loads a ProcessResponse from a sample file.
     * @param filename the filename (e.g., "sample-pipestream-1.bin")
     * @return ProcessResponse object
     * @throws IOException if the resource cannot be read
     */
    public static ProcessResponse loadSampleProcessResponse(String filename) throws IOException {
        try (InputStream is = SampleDataLoader.class.getResourceAsStream(SAMPLE_PATH + filename)) {
            if (is == null) {
                throw new IOException("Sample file not found: " + filename);
            }
            return ProcessResponse.parseFrom(is);
        } catch (InvalidProtocolBufferException e) {
            throw new IOException("Failed to parse ProcessResponse from " + filename, e);
        }
    }
    
    /**
     * Loads the default sample ProcessResponse from resources.
     * @return ProcessResponse object
     * @throws IOException if the resource cannot be read
     */
    public static ProcessResponse loadDefaultSampleProcessResponse() throws IOException {
        return loadSampleProcessResponse(DEFAULT_SAMPLE);
    }
    
    /**
     * Gets the PipeDoc from a ProcessResponse sample file.
     * @param filename the filename (e.g., "sample-pipestream-1.bin")
     * @return PipeDoc object, creates a default sample if the ProcessResponse doesn't contain an output document
     * @throws IOException if the resource cannot be read
     */
    public static PipeDoc loadSamplePipeDocFromResponse(String filename) throws IOException {
        try {
            ProcessResponse response = loadSampleProcessResponse(filename);
            if (response.hasOutputDoc()) {
                return response.getOutputDoc();
            }
        } catch (IOException e) {
            // If we can't load the file or it's not a ProcessResponse, fall back to created sample
        }
        
        // If no document found in file, create a sample document
        return SampleDataCreator.createDefaultSamplePipeDoc();
    }
    
    /**
     * Loads the default sample PipeDoc by extracting it from the default ProcessResponse.
     * @return PipeDoc object or null if the ProcessResponse doesn't contain an output document
     * @throws IOException if the resource cannot be read
     */
    public static PipeDoc loadDefaultSamplePipeDocFromResponse() throws IOException {
        ProcessResponse response = loadDefaultSampleProcessResponse();
        return response.hasOutputDoc() ? response.getOutputDoc() : null;
    }
}