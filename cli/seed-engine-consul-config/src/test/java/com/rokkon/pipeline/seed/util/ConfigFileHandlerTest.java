package com.rokkon.pipeline.seed.util;

import com.rokkon.pipeline.seed.model.ConfigurationModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the ConfigFileHandler class.
 * Tests loading from and saving to properties, YAML, and JSON files.
 */
public class ConfigFileHandlerTest {

    @TempDir
    Path tempDir;

    private ConfigurationModel testConfig;
    private File propertiesFile;
    private File yamlFile;
    private File jsonFile;
    private File ymlFile;

    @BeforeEach
    void setUp() {
        // Create a test configuration
        Map<String, String> configData = new HashMap<>();
        configData.put("rokkon.engine.name", "test-engine");
        configData.put("rokkon.engine.grpc-port", "9000");
        configData.put("rokkon.engine.rest-port", "9001");
        configData.put("rokkon.consul.cleanup.enabled", "true");
        configData.put("rokkon.consul.cleanup.interval", "PT10M");
        configData.put("rokkon.modules.service-prefix", "test-module-");
        configData.put("rokkon.default-cluster.name", "test-cluster");
        
        testConfig = new ConfigurationModel(configData);
        
        // Create test files
        propertiesFile = tempDir.resolve("test-config.properties").toFile();
        yamlFile = tempDir.resolve("test-config.yaml").toFile();
        ymlFile = tempDir.resolve("test-config.yml").toFile();
        jsonFile = tempDir.resolve("test-config.json").toFile();
    }

    @AfterEach
    void tearDown() {
        // Clean up test files
        propertiesFile.delete();
        yamlFile.delete();
        ymlFile.delete();
        jsonFile.delete();
    }

    @Test
    void testSaveAndLoadPropertiesFile() throws IOException {
        // Save to properties file
        ConfigFileHandler.saveToFile(testConfig, propertiesFile.getAbsolutePath());
        
        // Verify file exists
        assertThat(propertiesFile).exists();
        
        // Load from properties file
        ConfigurationModel loadedConfig = ConfigFileHandler.loadFromFile(propertiesFile.getAbsolutePath());
        
        // Verify loaded config matches original
        assertThat(loadedConfig.getAll()).containsAllEntriesOf(testConfig.getAll());
        assertThat(loadedConfig.get("rokkon.engine.name")).isEqualTo("test-engine");
        assertThat(loadedConfig.get("rokkon.engine.grpc-port")).isEqualTo("9000");
    }

    @Test
    void testSaveAndLoadYamlFile() throws IOException {
        // Save to YAML file
        ConfigFileHandler.saveToFile(testConfig, yamlFile.getAbsolutePath());
        
        // Verify file exists
        assertThat(yamlFile).exists();
        
        // Load from YAML file
        ConfigurationModel loadedConfig = ConfigFileHandler.loadFromFile(yamlFile.getAbsolutePath());
        
        // Verify loaded config matches original
        assertThat(loadedConfig.getAll()).containsAllEntriesOf(testConfig.getAll());
        assertThat(loadedConfig.get("rokkon.engine.name")).isEqualTo("test-engine");
        assertThat(loadedConfig.get("rokkon.engine.grpc-port")).isEqualTo("9000");
    }

    @Test
    void testSaveAndLoadYmlFile() throws IOException {
        // Save to YML file
        ConfigFileHandler.saveToFile(testConfig, ymlFile.getAbsolutePath());
        
        // Verify file exists
        assertThat(ymlFile).exists();
        
        // Load from YML file
        ConfigurationModel loadedConfig = ConfigFileHandler.loadFromFile(ymlFile.getAbsolutePath());
        
        // Verify loaded config matches original
        assertThat(loadedConfig.getAll()).containsAllEntriesOf(testConfig.getAll());
        assertThat(loadedConfig.get("rokkon.engine.name")).isEqualTo("test-engine");
        assertThat(loadedConfig.get("rokkon.engine.grpc-port")).isEqualTo("9000");
    }

    @Test
    void testSaveAndLoadJsonFile() throws IOException {
        // Save to JSON file
        ConfigFileHandler.saveToFile(testConfig, jsonFile.getAbsolutePath());
        
        // Verify file exists
        assertThat(jsonFile).exists();
        
        // Load from JSON file
        ConfigurationModel loadedConfig = ConfigFileHandler.loadFromFile(jsonFile.getAbsolutePath());
        
        // Verify loaded config matches original
        assertThat(loadedConfig.getAll()).containsAllEntriesOf(testConfig.getAll());
        assertThat(loadedConfig.get("rokkon.engine.name")).isEqualTo("test-engine");
        assertThat(loadedConfig.get("rokkon.engine.grpc-port")).isEqualTo("9000");
    }

    @Test
    void testUnsupportedFileFormat() {
        // Create a file with unsupported extension
        File unsupportedFile = tempDir.resolve("test-config.txt").toFile();
        
        // Verify that saving to unsupported format throws exception
        assertThrows(IllegalArgumentException.class, () -> {
            ConfigFileHandler.saveToFile(testConfig, unsupportedFile.getAbsolutePath());
        });
        
        // Create an empty file with unsupported extension
        try {
            Files.createFile(unsupportedFile.toPath());
        } catch (IOException e) {
            // Ignore
        }
        
        // Verify that loading from unsupported format throws exception
        assertThrows(IllegalArgumentException.class, () -> {
            ConfigFileHandler.loadFromFile(unsupportedFile.getAbsolutePath());
        });
    }

    @Test
    void testImportAndMergeConfig() throws IOException {
        // Create a different config
        Map<String, String> mergeData = new HashMap<>();
        mergeData.put("rokkon.engine.name", "merged-engine");
        mergeData.put("rokkon.new-key", "new-value");
        ConfigurationModel mergeConfig = new ConfigurationModel(mergeData);
        
        // Save to properties file
        ConfigFileHandler.saveToFile(mergeConfig, propertiesFile.getAbsolutePath());
        
        // Import and merge
        ConfigurationModel mergedConfig = ConfigFileHandler.importAndMergeConfig(testConfig, propertiesFile.getAbsolutePath());
        
        // Verify merged config contains both original and imported values
        assertThat(mergedConfig.get("rokkon.engine.name")).isEqualTo("merged-engine"); // Overwritten
        assertThat(mergedConfig.get("rokkon.engine.grpc-port")).isEqualTo("9000"); // Original
        assertThat(mergedConfig.get("rokkon.new-key")).isEqualTo("new-value"); // New
    }

    @Test
    void testExportConfig() throws IOException {
        // Export to properties file
        ConfigFileHandler.exportConfig(testConfig, propertiesFile.getAbsolutePath());
        
        // Verify file exists
        assertThat(propertiesFile).exists();
        
        // Load from properties file
        ConfigurationModel loadedConfig = ConfigFileHandler.loadFromFile(propertiesFile.getAbsolutePath());
        
        // Verify loaded config matches original
        assertThat(loadedConfig.getAll()).containsAllEntriesOf(testConfig.getAll());
    }
}