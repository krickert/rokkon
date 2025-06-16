package com.rokkon.modules.tika;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration class for Tika module settings.
 * Provides default values and configuration merging capabilities.
 */
@ApplicationScoped
public class TikaConfiguration {

    @ConfigProperty(name = "max-content-length", defaultValue = "10000000")
    int maxContentLength;

    @ConfigProperty(name = "enable-geo-topic-parser", defaultValue = "false")
    boolean enableGeoTopicParser;

    @ConfigProperty(name = "extract-embedded-docs", defaultValue = "true")
    boolean extractEmbeddedDocs;

    @ConfigProperty(name = "parse-timeout-seconds", defaultValue = "60")
    int parseTimeoutSeconds;

    @ConfigProperty(name = "max-recursion-depth", defaultValue = "10")
    int maxRecursionDepth;

    @ConfigProperty(name = "enable-ocr", defaultValue = "false")
    boolean enableOcr;

    @ConfigProperty(name = "ocr-strategy", defaultValue = "OCR_ONLY")
    String ocrStrategy;

    @ConfigProperty(name = "ocr-language", defaultValue = "eng")
    String ocrLanguage;

    @ConfigProperty(name = "ocr-timeout-seconds", defaultValue = "120")
    int ocrTimeoutSeconds;

    @ConfigProperty(name = "enable-x-tika-content", defaultValue = "false")
    boolean enableXTikaContent;

    @ConfigProperty(name = "flatten-compound", defaultValue = "false")
    boolean flattenCompound;

    @ConfigProperty(name = "ignore-tika-exception", defaultValue = "false")
    boolean ignoreTikaException;

    @ConfigProperty(name = "password-file-path")
    Optional<String> passwordFilePath;

    @ConfigProperty(name = "enable-password-extraction", defaultValue = "false")
    boolean enablePasswordExtraction;
    
    /**
     * Creates a TikaConfiguration with default values for testing.
     * This method is useful when Quarkus dependency injection is not available.
     */
    public static TikaConfiguration createDefaults() {
        TikaConfiguration config = new TikaConfiguration();
        config.maxContentLength = 10000000;
        config.enableGeoTopicParser = false;
        config.extractEmbeddedDocs = true;
        config.parseTimeoutSeconds = 60;
        config.maxRecursionDepth = 10;
        config.enableOcr = false;
        config.ocrStrategy = "OCR_ONLY";
        config.ocrLanguage = "eng";
        config.ocrTimeoutSeconds = 120;
        config.enableXTikaContent = false;
        config.flattenCompound = false;
        config.ignoreTikaException = false;
        config.passwordFilePath = Optional.empty();
        config.enablePasswordExtraction = false;
        return config;
    }

    /**
     * Merges request configuration with default configuration values.
     *
     * @param requestConfig Configuration from the gRPC request
     * @return Merged configuration map
     */
    public Map<String, String> mergeWithDefaults(Map<String, String> requestConfig) {
        Map<String, String> mergedConfig = new HashMap<>();
        
        // Set defaults
        mergedConfig.put("maxContentLength", String.valueOf(maxContentLength));
        mergedConfig.put("enableGeoTopicParser", String.valueOf(enableGeoTopicParser));
        mergedConfig.put("extractEmbeddedDocs", String.valueOf(extractEmbeddedDocs));
        mergedConfig.put("parseTimeoutSeconds", String.valueOf(parseTimeoutSeconds));
        mergedConfig.put("maxRecursionDepth", String.valueOf(maxRecursionDepth));
        mergedConfig.put("enableOcr", String.valueOf(enableOcr));
        mergedConfig.put("ocrStrategy", ocrStrategy);
        mergedConfig.put("ocrLanguage", ocrLanguage);
        mergedConfig.put("ocrTimeoutSeconds", String.valueOf(ocrTimeoutSeconds));
        mergedConfig.put("enableXTikaContent", String.valueOf(enableXTikaContent));
        mergedConfig.put("flattenCompound", String.valueOf(flattenCompound));
        mergedConfig.put("ignoreTikaException", String.valueOf(ignoreTikaException));
        mergedConfig.put("enablePasswordExtraction", String.valueOf(enablePasswordExtraction));
        
        if (passwordFilePath != null && passwordFilePath.isPresent()) {
            mergedConfig.put("passwordFilePath", passwordFilePath.get());
        }
        
        // Override with request config
        if (requestConfig != null) {
            mergedConfig.putAll(requestConfig);
        }
        
        return mergedConfig;
    }

    // Getters
    public int getMaxContentLength() {
        return maxContentLength;
    }

    public boolean isEnableGeoTopicParser() {
        return enableGeoTopicParser;
    }

    public boolean isExtractEmbeddedDocs() {
        return extractEmbeddedDocs;
    }

    public int getParseTimeoutSeconds() {
        return parseTimeoutSeconds;
    }

    public int getMaxRecursionDepth() {
        return maxRecursionDepth;
    }

    public boolean isEnableOcr() {
        return enableOcr;
    }

    public String getOcrStrategy() {
        return ocrStrategy;
    }

    public String getOcrLanguage() {
        return ocrLanguage;
    }

    public int getOcrTimeoutSeconds() {
        return ocrTimeoutSeconds;
    }

    public boolean isEnableXTikaContent() {
        return enableXTikaContent;
    }

    public boolean isFlattenCompound() {
        return flattenCompound;
    }

    public boolean isIgnoreTikaException() {
        return ignoreTikaException;
    }

    public Optional<String> getPasswordFilePath() {
        return passwordFilePath;
    }

    public boolean isEnablePasswordExtraction() {
        return enablePasswordExtraction;
    }
}