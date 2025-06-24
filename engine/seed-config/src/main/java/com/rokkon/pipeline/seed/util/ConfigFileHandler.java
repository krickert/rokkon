package com.rokkon.pipeline.seed.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.rokkon.pipeline.seed.model.ConfigurationModel;
import io.quarkus.logging.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Utility class for handling configuration file operations.
 * Supports reading and writing configuration files in properties, YAML, and JSON formats.
 */
public class ConfigFileHandler {

    private static final String DEFAULT_CONFIG_FILENAME = "rokkon-config.properties";
    private static final String USER_HOME = System.getProperty("user.home");
    private static final String DEFAULT_CONFIG_DIR = Paths.get(USER_HOME, ".rokkon").toString();

    // Object mappers for JSON and YAML
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    /**
     * Load configuration from a file
     * @param filePath Path to the configuration file
     * @return ConfigurationModel containing the loaded configuration
     * @throws IOException If an I/O error occurs
     * @throws IllegalArgumentException If the file format is invalid
     */
    public static ConfigurationModel loadFromFile(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("Configuration file not found: " + filePath);
        }

        if (!file.canRead()) {
            throw new IOException("Cannot read configuration file: " + filePath);
        }

        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".properties")) {
            return loadFromPropertiesFile(file);
        } else if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
            return loadFromYamlFile(file);
        } else if (fileName.endsWith(".json")) {
            return loadFromJsonFile(file);
        } else {
            throw new IllegalArgumentException("Unsupported file format: " + fileName + 
                ". Supported formats are .properties, .yaml, .yml, and .json");
        }
    }

    /**
     * Save configuration to a file
     * @param config The configuration to save
     * @param filePath Path to the configuration file
     * @throws IOException If an I/O error occurs
     */
    public static void saveToFile(ConfigurationModel config, String filePath) throws IOException {
        File file = new File(filePath);
        File parentDir = file.getParentFile();

        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
            }
        }

        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".properties")) {
            saveToPropertiesFile(config, file);
        } else if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
            saveToYamlFile(config, file);
        } else if (fileName.endsWith(".json")) {
            saveToJsonFile(config, file);
        } else {
            throw new IllegalArgumentException("Unsupported file format: " + fileName + 
                ". Supported formats are .properties, .yaml, .yml, and .json");
        }
    }

    /**
     * Get the default configuration file path
     * @return The default configuration file path
     */
    public static String getDefaultConfigFilePath() {
        return Paths.get(DEFAULT_CONFIG_DIR, DEFAULT_CONFIG_FILENAME).toString();
    }

    /**
     * Check if the default configuration file exists
     * @return true if the default configuration file exists, false otherwise
     */
    public static boolean defaultConfigFileExists() {
        return Files.exists(Paths.get(getDefaultConfigFilePath()));
    }

    /**
     * Create a default configuration file if it doesn't exist
     * @return true if the file was created, false if it already existed
     * @throws IOException If an I/O error occurs
     */
    public static boolean createDefaultConfigFileIfNotExists() throws IOException {
        Path configPath = Paths.get(getDefaultConfigFilePath());
        if (Files.exists(configPath)) {
            return false;
        }

        Path parentDir = configPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        ConfigurationModel defaultConfig = new ConfigurationModel();
        saveToFile(defaultConfig, configPath.toString());
        Log.infof("Created default configuration file at: %s", configPath);
        return true;
    }

    /**
     * Load configuration from a properties file
     * @param file The properties file
     * @return ConfigurationModel containing the loaded configuration
     * @throws IOException If an I/O error occurs
     */
    private static ConfigurationModel loadFromPropertiesFile(File file) throws IOException {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            props.load(isr);
        }

        Map<String, String> configMap = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            configMap.put(key, props.getProperty(key));
        }

        return new ConfigurationModel(configMap);
    }

    /**
     * Load configuration from a YAML file
     * @param file The YAML file
     * @return ConfigurationModel containing the loaded configuration
     * @throws IOException If an I/O error occurs
     */
    private static ConfigurationModel loadFromYamlFile(File file) throws IOException {
        Map<String, Object> yamlMap = YAML_MAPPER.readValue(file, Map.class);
        Map<String, String> configMap = flattenMap(yamlMap, "");
        return new ConfigurationModel(configMap);
    }

    /**
     * Load configuration from a JSON file
     * @param file The JSON file
     * @return ConfigurationModel containing the loaded configuration
     * @throws IOException If an I/O error occurs
     */
    private static ConfigurationModel loadFromJsonFile(File file) throws IOException {
        Map<String, Object> jsonMap = JSON_MAPPER.readValue(file, Map.class);
        Map<String, String> configMap = flattenMap(jsonMap, "");
        return new ConfigurationModel(configMap);
    }

    /**
     * Flatten a nested map into a flat map with dot-separated keys
     * @param nestedMap The nested map to flatten
     * @param prefix The prefix to use for keys
     * @return A flat map with dot-separated keys
     */
    private static Map<String, String> flattenMap(Map<String, Object> nestedMap, String prefix) {
        Map<String, String> flatMap = new HashMap<>();

        for (Map.Entry<String, Object> entry : nestedMap.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedValue = (Map<String, Object>) value;
                flatMap.putAll(flattenMap(nestedValue, key));
            } else {
                flatMap.put(key, value != null ? value.toString() : "");
            }
        }

        return flatMap;
    }

    /**
     * Save configuration to a properties file
     * @param config The configuration to save
     * @param file The properties file
     * @throws IOException If an I/O error occurs
     */
    private static void saveToPropertiesFile(ConfigurationModel config, File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(osw)) {
            writer.write(config.toFormattedString());
        }
    }

    /**
     * Save configuration to a YAML file
     * @param config The configuration to save
     * @param file The YAML file
     * @throws IOException If an I/O error occurs
     */
    private static void saveToYamlFile(ConfigurationModel config, File file) throws IOException {
        Map<String, Object> nestedMap = unflattenMap(config.getAll());
        YAML_MAPPER.writeValue(file, nestedMap);
    }

    /**
     * Save configuration to a JSON file
     * @param config The configuration to save
     * @param file The JSON file
     * @throws IOException If an I/O error occurs
     */
    private static void saveToJsonFile(ConfigurationModel config, File file) throws IOException {
        Map<String, Object> nestedMap = unflattenMap(config.getAll());
        JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, nestedMap);
    }

    /**
     * Unflatten a flat map with dot-separated keys into a nested map
     * @param flatMap The flat map to unflatten
     * @return A nested map
     */
    private static Map<String, Object> unflattenMap(Map<String, String> flatMap) {
        Map<String, Object> nestedMap = new HashMap<>();

        for (Map.Entry<String, String> entry : flatMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            String[] parts = key.split("\\.");
            Map<String, Object> current = nestedMap;

            for (int i = 0; i < parts.length - 1; i++) {
                String part = parts[i];
                if (!current.containsKey(part)) {
                    current.put(part, new HashMap<String, Object>());
                }
                current = (Map<String, Object>) current.get(part);
            }

            current.put(parts[parts.length - 1], value);
        }

        return nestedMap;
    }

    /**
     * Export the current configuration to a file
     * @param config The configuration to export
     * @param filePath The path to export to
     * @throws IOException If an I/O error occurs
     */
    public static void exportConfig(ConfigurationModel config, String filePath) throws IOException {
        saveToFile(config, filePath);
        Log.infof("Configuration exported to: %s", filePath);
    }

    /**
     * Import configuration from a file and merge with existing configuration
     * @param existingConfig The existing configuration
     * @param filePath The path to import from
     * @return The merged configuration
     * @throws IOException If an I/O error occurs
     */
    public static ConfigurationModel importAndMergeConfig(ConfigurationModel existingConfig, String filePath) throws IOException {
        ConfigurationModel importedConfig = loadFromFile(filePath);
        Map<String, String> mergedData = existingConfig.getAll();
        mergedData.putAll(importedConfig.getAll());
        return new ConfigurationModel(mergedData);
    }
}
