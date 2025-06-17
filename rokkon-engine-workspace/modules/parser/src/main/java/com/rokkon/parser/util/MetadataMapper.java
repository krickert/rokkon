package com.rokkon.parser.util;

import org.apache.tika.metadata.Metadata;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for mapping Tika Metadata to protobuf-compatible maps.
 * Handles metadata normalization and cleanup.
 */
public class MetadataMapper {
    private static final Logger LOG = Logger.getLogger(MetadataMapper.class);
    
    private static final int DEFAULT_MAX_VALUE_LENGTH = 10000; // Default maximum metadata value length

    private MetadataMapper() {
        // Utility class
    }

    /**
     * Converts Tika Metadata to a Map suitable for protobuf.
     *
     * @param metadata The Tika metadata object
     * @param config Configuration map containing processing options
     * @return A map of metadata key-value pairs
     */
    public static Map<String, String> toMap(Metadata metadata, Map<String, String> config) {
        Map<String, String> metadataMap = new HashMap<>();
        
        if (metadata == null) {
            return metadataMap;
        }
        
        // Get max value length from config, use default if not specified
        int maxValueLength = getIntConfig(config, "maxMetadataValueLength", DEFAULT_MAX_VALUE_LENGTH);
        
        for (String name : metadata.names()) {
            String[] values = metadata.getValues(name);
            if (values != null && values.length > 0) {
                if (values.length == 1) {
                    // Single value
                    String value = cleanValue(values[0], maxValueLength);
                    if (value != null && !value.isEmpty()) {
                        metadataMap.put(normalizeKey(name), value);
                    }
                } else {
                    // Multiple values - join with semicolon
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < values.length; i++) {
                        String value = cleanValue(values[i], maxValueLength);
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
        
        LOG.debugf("Mapped %d metadata fields", metadataMap.size());
        return metadataMap;
    }

    /**
     * Convenience method for backward compatibility - uses default config.
     *
     * @param metadata The Tika metadata object
     * @return A map of metadata key-value pairs
     */
    public static Map<String, String> toMap(Metadata metadata) {
        return toMap(metadata, new HashMap<>());
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
     * @param maxLength Maximum allowed length for the value
     * @return The cleaned value, or null if invalid
     */
    private static String cleanValue(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        
        String cleaned = value.trim();
        
        // Skip empty values
        if (cleaned.isEmpty()) {
            return null;
        }
        
        // Check for binary data (high percentage of non-printable characters)
        if (isBinaryData(cleaned)) {
            LOG.debug("Skipping binary metadata value");
            return null;
        }
        
        // Truncate if too long (only if maxLength > 0, -1 means unlimited)
        if (maxLength > 0 && cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength) + "...";
            LOG.debugf("Truncated metadata value to %d characters", maxLength);
        }
        
        return cleaned;
    }
    
    /**
     * Checks if a string contains mostly binary data.
     *
     * @param value The string to check
     * @return true if the string appears to be binary data
     */
    private static boolean isBinaryData(String value) {
        if (value.length() < 10) {
            return false; // Too short to determine
        }
        
        int nonPrintableCount = 0;
        for (char c : value.toCharArray()) {
            if (c < 32 && c != '\t' && c != '\n' && c != '\r') {
                nonPrintableCount++;
            }
        }
        
        // If more than 10% non-printable characters, consider it binary
        return (double) nonPrintableCount / value.length() > 0.1;
    }
    
    /**
     * Helper method to get integer configuration values.
     *
     * @param config Configuration map
     * @param key Configuration key
     * @param defaultValue Default value if key not found or invalid
     * @return Integer value from config or default
     */
    private static int getIntConfig(Map<String, String> config, String key, int defaultValue) {
        if (config == null || !config.containsKey(key)) {
            return defaultValue;
        }
        
        try {
            return Integer.parseInt(config.get(key));
        } catch (NumberFormatException e) {
            LOG.warnf("Invalid integer value for config key '%s': %s, using default: %d", 
                     key, config.get(key), defaultValue);
            return defaultValue;
        }
    }
}