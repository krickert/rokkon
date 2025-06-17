package com.rokkon.pipeline.chunker;

/**
 * Record representing a text chunk with its metadata.
 * 
 * @param id The unique identifier for this chunk
 * @param text The actual text content of the chunk
 * @param originalIndexStart The starting character index in the original document
 * @param originalIndexEnd The ending character index in the original document
 */
public record Chunk(
    String id,
    String text,
    int originalIndexStart,
    int originalIndexEnd
) {
    // Compact constructor for validation
    public Chunk {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Chunk ID cannot be null or empty");
        }
        if (text == null) {
            throw new IllegalArgumentException("Chunk text cannot be null");
        }
        if (originalIndexStart < 0) {
            throw new IllegalArgumentException("Original index start cannot be negative");
        }
        if (originalIndexEnd < originalIndexStart) {
            throw new IllegalArgumentException("Original index end cannot be less than start");
        }
    }
}