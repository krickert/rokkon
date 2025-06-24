package com.rokkon.pipeline.chunker;

import java.util.List;
import java.util.Map;

/**
 * Record representing the result of a chunking operation.
 * 
 * @param chunks The list of chunks created from the document
 * @param placeholderToUrlMap A map of URL placeholders to their original URLs (for URL preservation)
 */
public record ChunkingResult(
    List<Chunk> chunks,
    Map<String, String> placeholderToUrlMap
) {
    public ChunkingResult {
        if (chunks == null) {
            throw new IllegalArgumentException("Chunks list cannot be null");
        }
        if (placeholderToUrlMap == null) {
            throw new IllegalArgumentException("Placeholder to URL map cannot be null");
        }
    }
}