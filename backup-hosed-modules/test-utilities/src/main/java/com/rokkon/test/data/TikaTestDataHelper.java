package com.rokkon.test.data;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.test.generation.TestDataGenerator;
import com.rokkon.test.protobuf.ProtobufUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class for accessing Tika-specific test data.
 * Provides lazy-loaded access to Tika request and response test data.
 */
@ApplicationScoped
public class TikaTestDataHelper {
    private static final Logger LOG = LoggerFactory.getLogger(TikaTestDataHelper.class);
    
    // Paths to test data (relative to classpath resources)
    private static final String TIKA_REQUESTS_PATH = "/test-data/tika/requests";
    private static final String TIKA_RESPONSES_PATH = "/test-data/tika/responses";
    
    // Cached data
    private static volatile List<PipeStream> tikaRequestStreams;
    private static volatile List<PipeDoc> tikaResponseDocs;
    private static volatile Map<String, PipeStream> tikaRequestStreamsMap;
    private static volatile Map<String, PipeDoc> tikaResponseDocsMap;
    
    /**
     * Gets all Tika request streams (input data with document blobs).
     * These represent the data that would be sent to Tika for processing.
     */
    public List<PipeStream> getTikaRequestStreams() {
        if (tikaRequestStreams == null) {
            synchronized (TikaTestDataHelper.class) {
                if (tikaRequestStreams == null) {
                    LOG.info("Loading Tika request streams from {}", TIKA_REQUESTS_PATH);
                    try {
                        tikaRequestStreams = ProtobufUtils.loadPipeStreamsFromDirectory(TIKA_REQUESTS_PATH, ".bin");
                        LOG.info("Loaded {} Tika request streams", tikaRequestStreams.size());
                    } catch (Exception e) {
                        LOG.warn("Failed to load Tika request streams: {}", e.getMessage());
                        tikaRequestStreams = List.of();
                    }
                }
            }
        }
        return tikaRequestStreams;
    }
    
    /**
     * Gets all Tika response documents (output data with extracted text).
     * These represent the data that Tika would return after processing.
     */
    public List<PipeDoc> getTikaResponseDocs() {
        if (tikaResponseDocs == null) {
            synchronized (TikaTestDataHelper.class) {
                if (tikaResponseDocs == null) {
                    LOG.info("Loading Tika response documents from {}", TIKA_RESPONSES_PATH);
                    try {
                        tikaResponseDocs = ProtobufUtils.loadPipeDocsFromDirectory(TIKA_RESPONSES_PATH, ".bin");
                        LOG.info("Loaded {} Tika response documents", tikaResponseDocs.size());
                    } catch (Exception e) {
                        LOG.warn("Failed to load Tika response documents: {}", e.getMessage());
                        tikaResponseDocs = List.of();
                    }
                }
            }
        }
        return tikaResponseDocs;
    }
    
    /**
     * Gets Tika request streams as a map by stream ID.
     */
    public Map<String, PipeStream> getTikaRequestStreamsMap() {
        if (tikaRequestStreamsMap == null) {
            synchronized (TikaTestDataHelper.class) {
                if (tikaRequestStreamsMap == null) {
                    List<PipeStream> streams = getTikaRequestStreams();
                    tikaRequestStreamsMap = new ConcurrentHashMap<>();
                    streams.forEach(stream -> tikaRequestStreamsMap.put(stream.getStreamId(), stream));
                }
            }
        }
        return tikaRequestStreamsMap;
    }
    
    /**
     * Gets Tika response documents as a map by document ID.
     */
    public Map<String, PipeDoc> getTikaResponseDocsMap() {
        if (tikaResponseDocsMap == null) {
            synchronized (TikaTestDataHelper.class) {
                if (tikaResponseDocsMap == null) {
                    List<PipeDoc> docs = getTikaResponseDocs();
                    tikaResponseDocsMap = new ConcurrentHashMap<>();
                    docs.forEach(doc -> tikaResponseDocsMap.put(doc.getId(), doc));
                }
            }
        }
        return tikaResponseDocsMap;
    }
    
    /**
     * Gets a specific Tika request stream by ID.
     */
    public PipeStream getTikaRequestStreamById(String streamId) {
        return getTikaRequestStreamsMap().get(streamId);
    }
    
    /**
     * Gets a specific Tika response document by ID.
     */
    public PipeDoc getTikaResponseDocById(String docId) {
        return getTikaResponseDocsMap().get(docId);
    }
    
    /**
     * Gets the first N Tika request streams for smaller test runs.
     */
    public List<PipeStream> getFirstTikaRequestStreams(int count) {
        List<PipeStream> allStreams = getTikaRequestStreams();
        return allStreams.subList(0, Math.min(count, allStreams.size()));
    }
    
    /**
     * Gets the first N Tika response documents for smaller test runs.
     */
    public List<PipeDoc> getFirstTikaResponseDocs(int count) {
        List<PipeDoc> allDocs = getTikaResponseDocs();
        return allDocs.subList(0, Math.min(count, allDocs.size()));
    }
    
    /**
     * Clears all cached data (useful for testing data regeneration).
     */
    public void clearCache() {
        tikaRequestStreams = null;
        tikaResponseDocs = null;
        tikaRequestStreamsMap = null;
        tikaResponseDocsMap = null;
        LOG.debug("Cleared Tika test data cache");
    }
    
    /**
     * Gets the count of available Tika request streams without loading them all.
     */
    public int getTikaRequestStreamCount() {
        return getTikaRequestStreams().size();
    }
    
    /**
     * Gets the count of available Tika response documents without loading them all.
     */
    public int getTikaResponseDocCount() {
        return getTikaResponseDocs().size();
    }
}