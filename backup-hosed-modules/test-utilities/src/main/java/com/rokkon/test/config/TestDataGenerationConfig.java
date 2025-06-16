package com.rokkon.test.config;

/**
 * Configuration for test data generation in the Rokkon testing framework.
 * Controls whether test data should be regenerated and where it should be written.
 */
public class TestDataGenerationConfig {
    
    /**
     * System property to enable test data regeneration.
     * Set -Drokkon.test.data.regenerate=true to enable.
     */
    public static final String REGENERATE_PROPERTY = "rokkon.test.data.regenerate";
    
    /**
     * System property to control where test data is written.
     * Set -Drokkon.test.data.output.dir=/path/to/output to override default.
     */
    public static final String OUTPUT_DIR_PROPERTY = "rokkon.test.data.output.dir";
    
    /**
     * System property to enable deterministic IDs for test data.
     * Set -Drokkon.test.data.deterministic=true to enable.
     */
    public static final String DETERMINISTIC_IDS_PROPERTY = "rokkon.test.data.deterministic";
    
    /**
     * Default output directory (temp directory).
     */
    public static final String DEFAULT_OUTPUT_DIR = System.getProperty("java.io.tmpdir");
    
    /**
     * Check if test data regeneration is enabled.
     */
    public static boolean isRegenerationEnabled() {
        return Boolean.parseBoolean(System.getProperty(REGENERATE_PROPERTY, "false"));
    }
    
    /**
     * Get the output directory for test data.
     */
    public static String getOutputDirectory() {
        return System.getProperty(OUTPUT_DIR_PROPERTY, DEFAULT_OUTPUT_DIR);
    }
    
    /**
     * Check if deterministic IDs should be used.
     */
    public static boolean isDeterministicMode() {
        return Boolean.parseBoolean(System.getProperty(DETERMINISTIC_IDS_PROPERTY, "true"));
    }
    
    /**
     * Get the actual resources directory for permanent storage.
     */
    public static String getResourcesDirectory() {
        return "src/main/resources/test-data/sample-documents";
    }
}