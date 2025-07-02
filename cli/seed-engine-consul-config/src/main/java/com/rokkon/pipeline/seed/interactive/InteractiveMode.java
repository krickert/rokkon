package com.rokkon.pipeline.seed.interactive;

import com.rokkon.pipeline.seed.model.ConfigurationModel;
import com.rokkon.pipeline.seed.util.InputValidator;
import io.quarkus.logging.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Provides an interactive command-line interface for configuring the Consul seeder.
 * Includes features like autocomplete, default values, and validation.
 */
public class InteractiveMode {
    
    private static final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    private static final String PROMPT_PREFIX = "rokkon> ";
    
    private final ConfigurationModel config;
    
    public InteractiveMode(ConfigurationModel initialConfig) {
        this.config = initialConfig != null ? initialConfig : new ConfigurationModel();
    }
    
    /**
     * Start the interactive mode session
     * @return The updated configuration model
     */
    public ConfigurationModel start() {
        System.out.println("=== Rokkon Consul Seeder Interactive Mode ===");
        System.out.println("Type 'help' for available commands, 'exit' to quit");
        
        boolean running = true;
        while (running) {
            System.out.print(PROMPT_PREFIX);
            try {
                String input = reader.readLine().trim();
                
                if (input.isEmpty()) {
                    continue;
                }
                
                String[] parts = input.split("\\s+", 2);
                String command = parts[0].toLowerCase();
                String args = parts.length > 1 ? parts[1] : "";
                
                switch (command) {
                    case "exit":
                    case "quit":
                        running = false;
                        break;
                    case "help":
                        showHelp();
                        break;
                    case "list":
                        listConfiguration();
                        break;
                    case "set":
                        handleSetCommand(args);
                        break;
                    case "get":
                        handleGetCommand(args);
                        break;
                    case "remove":
                        handleRemoveCommand(args);
                        break;
                    case "clear":
                        handleClearCommand();
                        break;
                    case "consul":
                        handleConsulCommand(args);
                        break;
                    default:
                        System.out.println("Unknown command: " + command);
                        System.out.println("Type 'help' for available commands");
                }
            } catch (IOException e) {
                Log.error("Error reading input", e);
                running = false;
            }
        }
        
        System.out.println("Exiting interactive mode");
        return config;
    }
    
    /**
     * Show help information
     */
    private void showHelp() {
        System.out.println("Available commands:");
        System.out.println("  help                 - Show this help message");
        System.out.println("  list                 - List all configuration entries");
        System.out.println("  get <key>            - Get the value of a configuration entry");
        System.out.println("  set <key> <value>    - Set a configuration entry");
        System.out.println("  remove <key>         - Remove a configuration entry");
        System.out.println("  clear                - Clear all configuration entries");
        System.out.println("  consul               - Configure Consul connection settings");
        System.out.println("  exit, quit           - Exit interactive mode");
    }
    
    /**
     * List all configuration entries
     */
    private void listConfiguration() {
        Map<String, String> allConfig = new TreeMap<>(config.getAll());
        
        if (allConfig.isEmpty()) {
            System.out.println("No configuration entries");
            return;
        }
        
        System.out.println("Configuration entries:");
        for (Map.Entry<String, String> entry : allConfig.entrySet()) {
            System.out.printf("  %s = %s%n", entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Handle the 'set' command
     * @param args Command arguments
     */
    private void handleSetCommand(String args) {
        String[] parts = args.split("\\s+", 2);
        
        if (parts.length < 2) {
            System.out.println("Usage: set <key> <value>");
            return;
        }
        
        String key = parts[0];
        String value = parts[1];
        
        if (!InputValidator.isValidConfigKey(key)) {
            System.out.println("Invalid key format: " + key);
            System.out.println("Keys must contain only alphanumeric characters, dots, hyphens, and underscores");
            return;
        }
        
        String sanitizedValue = InputValidator.sanitizeString(value);
        config.set(key, sanitizedValue);
        System.out.println("Set " + key + " = " + sanitizedValue);
    }
    
    /**
     * Handle the 'get' command
     * @param args Command arguments
     */
    private void handleGetCommand(String args) {
        if (args.isEmpty()) {
            System.out.println("Usage: get <key>");
            return;
        }
        
        String key = args.trim();
        String value = config.get(key);
        
        if (value == null) {
            System.out.println("Key not found: " + key);
        } else {
            System.out.println(key + " = " + value);
        }
    }
    
    /**
     * Handle the 'remove' command
     * @param args Command arguments
     */
    private void handleRemoveCommand(String args) {
        if (args.isEmpty()) {
            System.out.println("Usage: remove <key>");
            return;
        }
        
        String key = args.trim();
        String value = config.get(key);
        
        if (value == null) {
            System.out.println("Key not found: " + key);
        } else {
            config.remove(key);
            System.out.println("Removed " + key);
        }
    }
    
    /**
     * Handle the 'clear' command
     */
    private void handleClearCommand() {
        System.out.print("Are you sure you want to clear all configuration entries? (y/N): ");
        try {
            String input = reader.readLine().trim().toLowerCase();
            if (input.equals("y") || input.equals("yes")) {
                config.getAll().keySet().forEach(config::remove);
                System.out.println("All configuration entries cleared");
            } else {
                System.out.println("Operation cancelled");
            }
        } catch (IOException e) {
            Log.error("Error reading input", e);
        }
    }
    
    /**
     * Handle the 'consul' command
     * @param args Command arguments
     */
    private void handleConsulCommand(String args) {
        System.out.println("=== Consul Connection Configuration ===");
        
        // Get current values
        String currentHost = config.get("rokkon.consul.host");
        String currentPort = config.get("rokkon.consul.port");
        String currentKeyPath = config.get("rokkon.consul.key-path");
        
        // Prompt for host
        String host = promptWithDefault("Consul host", currentHost != null ? currentHost : "localhost");
        if (!InputValidator.isValidHost(host)) {
            System.out.println("Invalid hostname or IP address: " + host);
            return;
        }
        
        // Prompt for port
        String portStr = promptWithDefault("Consul port", currentPort != null ? currentPort : "8500");
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (!InputValidator.isValidPort(port)) {
                System.out.println("Invalid port number: " + port);
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid port number: " + portStr);
            return;
        }
        
        // Prompt for key path
        String keyPath = promptWithDefault("Consul key path", currentKeyPath != null ? currentKeyPath : "config/application");
        if (!InputValidator.isValidKeyPath(keyPath)) {
            System.out.println("Invalid key path: " + keyPath);
            return;
        }
        
        // Update configuration
        config.set("rokkon.consul.host", host);
        config.set("rokkon.consul.port", String.valueOf(port));
        config.set("rokkon.consul.key-path", keyPath);
        
        System.out.println("Consul connection configuration updated");
    }
    
    /**
     * Prompt the user for input with a default value
     * @param prompt The prompt message
     * @param defaultValue The default value
     * @return The user input or the default value if the input is empty
     */
    private String promptWithDefault(String prompt, String defaultValue) {
        System.out.print(prompt + " [" + defaultValue + "]: ");
        try {
            String input = reader.readLine().trim();
            return input.isEmpty() ? defaultValue : input;
        } catch (IOException e) {
            Log.error("Error reading input", e);
            return defaultValue;
        }
    }
    
    /**
     * Prompt the user for input with autocomplete suggestions
     * @param prompt The prompt message
     * @param suggestions The autocomplete suggestions
     * @return The user input
     */
    private String promptWithAutocomplete(String prompt, List<String> suggestions) {
        System.out.print(prompt + " (Tab for suggestions): ");
        try {
            String input = reader.readLine().trim();
            
            // Simple autocomplete simulation (in a real app, we'd use JLine or similar)
            if (input.isEmpty() && !suggestions.isEmpty()) {
                System.out.println("Suggestions: " + String.join(", ", suggestions));
                return promptWithAutocomplete(prompt, suggestions);
            }
            
            return input;
        } catch (IOException e) {
            Log.error("Error reading input", e);
            return "";
        }
    }
}