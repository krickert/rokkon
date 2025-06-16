package com.rokkon.modules.tika.util;

import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for mapping Tika Metadata to protobuf-compatible maps.
 * Handles metadata normalization and cleanup.
 */
public class MetadataMapper {
    private static final Logger LOG = LoggerFactory.getLogger(MetadataMapper.class);

    private MetadataMapper() {
        // Utility class
    }

    /**
     * Converts Tika Metadata to a Map suitable for protobuf.
     *
     * @param metadata The Tika metadata object
     * @return A map of metadata key-value pairs
     */
    public static Map<String, String> toMap(Metadata metadata) {
        Map<String, String> metadataMap = new HashMap<>();
        
        if (metadata == null) {
            return metadataMap;
        }
        
        for (String name : metadata.names()) {
            String[] values = metadata.getValues(name);
            if (values != null && values.length > 0) {
                if (values.length == 1) {
                    // Single value
                    String value = cleanValue(values[0]);
                    if (value != null && !value.isEmpty()) {
                        metadataMap.put(normalizeKey(name), value);
                    }
                } else {
                    // Multiple values - join with semicolon
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < values.length; i++) {
                        String value = cleanValue(values[i]);
                        if (value != null && !value.isEmpty()) {
                            if (sb.length() > 0) {
                                sb.append("; ");
                            }
                            sb.append(value);
                        }
                    }
                    if (sb.length() > 0) {
                        metadataMap.put(normalizeKey(name), sb.toString());
                    }
                }
            }
        }
        
        LOG.debug("Mapped {} metadata fields", metadataMap.size());
        return metadataMap;
    }

    /**
     * Normalizes metadata keys to be consistent and protobuf-friendly.
     *
     * @param key The original metadata key
     * @return The normalized key
     */
    private static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        
        // Replace common problematic characters
        return key.replace(":", "_")
                  .replace(" ", "_")
                  .replace("-", "_")
                  .replace(".", "_")
                  .toLowerCase();
    }

    /**
     * Cleans and validates metadata values.
     *
     * @param value The raw metadata value
     * @return The cleaned value, or null if invalid
     */
    private static String cleanValue(String value) {
        if (value == null) {
            return null;
        }
        
        String cleaned = value.trim();
        
        // Skip empty values
        if (cleaned.isEmpty()) {
            return null;
        }
        
        // Skip very long values that might be binary data
        if (cleaned.length() > 10000) {
            LOG.debug("Skipping very long metadata value (length: {})", cleaned.length());
            return null;
        }
        
        // Check for binary data indicators
        if (containsBinaryData(cleaned)) {
            LOG.debug("Skipping binary metadata value");
            return null;
        }
        
        return cleaned;
    }

    /**
     * Checks if a string likely contains binary data.
     *
     * @param value The string to check
     * @return true if the string likely contains binary data
     */
    private static boolean containsBinaryData(String value) {
        if (value.length() < 20) {
            return false; // Short strings are probably not binary
        }
        
        int nonPrintableCount = 0;
        int totalLength = Math.min(value.length(), 100); // Check first 100 chars
        
        for (int i = 0; i < totalLength; i++) {
            char c = value.charAt(i);
            // Count non-printable characters (excluding common whitespace)
            if (c < 32 && c != '\t' && c != '\n' && c != '\r') {
                nonPrintableCount++;
            } else if (c > 126) {
                nonPrintableCount++;
            }
        }
        
        // If more than 10% of characters are non-printable, consider it binary
        return (nonPrintableCount * 10) > totalLength;
    }

    /**
     * Gets a specific metadata value by key, with fallback keys.
     *
     * @param metadata The Tika metadata object
     * @param primaryKey The primary key to look for
     * @param fallbackKeys Alternative keys to try if primary key is not found
     * @return The metadata value, or empty string if not found
     */
    public static String getValue(Metadata metadata, String primaryKey, String... fallbackKeys) {
        if (metadata == null) {
            return "";
        }
        
        String value = cleanValue(metadata.get(primaryKey));
        if (value != null && !value.isEmpty()) {
            return value;
        }
        
        // Try fallback keys
        for (String fallbackKey : fallbackKeys) {
            value = cleanValue(metadata.get(fallbackKey));
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        
        return "";
    }
}