package com.rokkon.pipeline.seed.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Model class representing the configuration data for the Consul seeder.
 * This class provides methods for validating and manipulating configuration data.
 */
public class ConfigurationModel {
    // Regex pattern for validating keys (alphanumeric, dots, hyphens, and underscores)
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9._\\-]+$");

    // Object mappers for JSON and YAML
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private Map<String, String> configData;

    public ConfigurationModel() {
        this.configData = new HashMap<>();
        initializeDefaultConfig();
    }

    public ConfigurationModel(Map<String, String> configData) {
        this.configData = new HashMap<>(configData);
    }

    /**
     * Initialize with default configuration values
     */
    private void initializeDefaultConfig() {
        // Engine settings
        configData.put("rokkon.engine.name", "rokkon-engine");
        configData.put("rokkon.engine.grpc-port", "8081");
        configData.put("rokkon.engine.rest-port", "8080");
        configData.put("rokkon.engine.debug", "false");

        // Consul cleanup settings
        configData.put("rokkon.consul.cleanup.enabled", "true");
        configData.put("rokkon.consul.cleanup.interval", "PT5M");
        configData.put("rokkon.consul.cleanup.zombie-threshold", "2m");
        configData.put("rokkon.consul.cleanup.cleanup-stale-whitelist", "true");

        // Consul health check settings
        configData.put("rokkon.consul.health.check-interval", "10s");
        configData.put("rokkon.consul.health.deregister-after", "1m");
        configData.put("rokkon.consul.health.timeout", "5s");

        // Module management settings
        configData.put("rokkon.modules.auto-discover", "false");
        configData.put("rokkon.modules.service-prefix", "module-");
        configData.put("rokkon.modules.require-whitelist", "true");
        configData.put("rokkon.modules.connection-timeout", "PT30S");
        configData.put("rokkon.modules.max-instances-per-module", "10");

        // Default cluster configuration
        configData.put("rokkon.default-cluster.name", "default");
        configData.put("rokkon.default-cluster.auto-create", "true");
        configData.put("rokkon.default-cluster.description", "Default cluster for Rokkon pipelines");
    }

    /**
     * Get a configuration value
     * @param key The configuration key
     * @return The configuration value or null if not found
     */
    public String get(String key) {
        return configData.get(key);
    }

    /**
     * Set a configuration value
     * @param key The configuration key
     * @param value The configuration value
     * @return This ConfigurationModel instance for chaining
     * @throws IllegalArgumentException if the key is invalid
     */
    public ConfigurationModel set(String key, String value) {
        validateKey(key);
        configData.put(key, value);
        return this;
    }

    /**
     * Remove a configuration entry
     * @param key The configuration key to remove
     * @return This ConfigurationModel instance for chaining
     */
    public ConfigurationModel remove(String key) {
        configData.remove(key);
        return this;
    }

    /**
     * Get all configuration data
     * @return A copy of the configuration data map
     */
    public Map<String, String> getAll() {
        return new HashMap<>(configData);
    }

    /**
     * Convert to Properties object
     * @return Properties object containing all configuration
     */
    public Properties toProperties() {
        Properties props = new Properties();
        props.putAll(configData);
        return props;
    }

    /**
     * Validate a configuration key
     * @param key The key to validate
     * @throws IllegalArgumentException if the key is invalid
     */
    private void validateKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Configuration key cannot be null or empty");
        }

        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(
                "Invalid configuration key format: " + key + 
                ". Keys must contain only alphanumeric characters, dots, hyphens, and underscores."
            );
        }
    }

    /**
     * Convert the configuration to a formatted string suitable for storage
     * @return Formatted configuration string
     */
    public String toFormattedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Rokkon Engine Configuration\n\n");

        // Group configurations by their prefix
        Map<String, StringBuilder> sections = new HashMap<>();

        for (Map.Entry<String, String> entry : configData.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            String section = key.contains(".") ? key.substring(0, key.indexOf('.')) : "other";

            if (!sections.containsKey(section)) {
                sections.put(section, new StringBuilder());
                sections.get(section).append("# ").append(section).append(" settings\n");
            }

            sections.get(section).append(key).append("=").append(value).append("\n");
        }

        // Append each section to the main string builder
        for (StringBuilder sectionContent : sections.values()) {
            sb.append(sectionContent).append("\n");
        }

        return sb.toString();
    }

    /**
     * Convert the configuration to a JSON string
     * @return JSON string representation of the configuration
     * @throws JsonProcessingException If an error occurs during JSON processing
     */
    public String toJsonString() throws JsonProcessingException {
        Map<String, Object> nestedMap = unflattenMap();
        return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(nestedMap);
    }

    /**
     * Convert the configuration to a YAML string
     * @return YAML string representation of the configuration
     * @throws JsonProcessingException If an error occurs during YAML processing
     */
    public String toYamlString() throws JsonProcessingException {
        Map<String, Object> nestedMap = unflattenMap();
        return YAML_MAPPER.writeValueAsString(nestedMap);
    }

    /**
     * Unflatten the configuration data into a nested map
     * @return A nested map representation of the configuration data
     */
    private Map<String, Object> unflattenMap() {
        Map<String, Object> nestedMap = new HashMap<>();

        for (Map.Entry<String, String> entry : configData.entrySet()) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigurationModel that = (ConfigurationModel) o;
        return Objects.equals(configData, that.configData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configData);
    }
}
