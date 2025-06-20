package com.rokkon.pipeline.consul.config;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.PropertiesConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Custom ConfigSource that reads Consul connection settings from a profile-specific YAML file.
 * The file location can be overridden using the CONSUL_CONFIG_LOCATION environment variable.
 */
public class ConsulConnectionConfigSource implements ConfigSourceFactory {
    
    private static final Logger LOG = Logger.getLogger(ConsulConnectionConfigSource.class);
    private static final String CONFIG_FILE_PATTERN = "consul-connection-%s.yml";
    private static final String DEFAULT_PROFILE = "dev";
    private static final int PRIORITY = 275; // Higher than application.yml (250) but lower than env vars (300)
    
    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        List<ConfigSource> sources = new ArrayList<>();
        
        try {
            // Determine the active profile
            ConfigValue profileValue = context.getValue("quarkus.profile");
            String profile = profileValue != null && profileValue.getValue() != null 
                ? profileValue.getValue() 
                : DEFAULT_PROFILE;
            
            // Determine config file location
            Path configPath = determineConfigPath(profile);
            
            if (Files.exists(configPath)) {
                LOG.infof("Loading Consul connection config from: %s", configPath);
                Map<String, String> properties = loadYamlAsProperties(configPath);
                
                if (!properties.isEmpty()) {
                    sources.add(new PropertiesConfigSource(
                        properties,
                        "consul-connection-" + profile,
                        PRIORITY
                    ));
                }
            } else {
                LOG.debugf("Consul connection config file not found: %s", configPath);
            }
        } catch (Exception e) {
            LOG.warnf("Failed to load Consul connection config: %s", e.getMessage());
        }
        
        return sources;
    }
    
    private Path determineConfigPath(String profile) {
        // Check environment variable first
        String envLocation = System.getenv("CONSUL_CONFIG_LOCATION");
        if (envLocation != null && !envLocation.isBlank()) {
            return Paths.get(envLocation);
        }
        
        String fileName = String.format(CONFIG_FILE_PATTERN, profile);
        
        // Check in user's home directory under .rokkon folder first
        Path homeConfigPath = Paths.get(System.getProperty("user.home"), ".rokkon", fileName);
        if (Files.exists(homeConfigPath)) {
            return homeConfigPath;
        }
        
        // Fall back to current directory
        return Paths.get(System.getProperty("user.dir"), fileName);
    }
    
    private Map<String, String> loadYamlAsProperties(Path configPath) throws IOException {
        Map<String, String> properties = new HashMap<>();
        
        String content = Files.readString(configPath);
        Yaml yaml = new Yaml();
        Map<String, Object> yamlMap = yaml.load(content);
        
        if (yamlMap != null) {
            // Convert nested YAML to flat properties
            flattenYamlMap("", yamlMap, properties);
        }
        
        return properties;
    }
    
    private void flattenYamlMap(String prefix, Map<String, Object> map, Map<String, String> properties) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                flattenYamlMap(key, nestedMap, properties);
            } else if (value != null) {
                properties.put(key, value.toString());
            }
        }
    }
}