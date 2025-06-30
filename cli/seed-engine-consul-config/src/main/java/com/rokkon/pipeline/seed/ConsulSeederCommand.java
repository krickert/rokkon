package com.rokkon.pipeline.seed;

import com.rokkon.pipeline.seed.interactive.InteractiveMode;
import com.rokkon.pipeline.seed.model.ConfigurationModel;
import com.rokkon.pipeline.seed.util.ConfigFileHandler;
import com.rokkon.pipeline.seed.util.InputValidator;
import io.quarkus.logging.Log;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.KeyValue;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@TopCommand
@Command(name = "seed-consul", mixinStandardHelpOptions = true,
        description = "Seeds Consul with initial application configuration")
public class ConsulSeederCommand implements Runnable {

    @Inject
    Vertx vertx;

    @Option(names = {"-h", "--host"}, description = "Consul host", defaultValue = "${consul.host:-localhost}")
    String consulHost;

    @Option(names = {"-p", "--port"}, description = "Consul port", defaultValue = "${consul.port:-8500}")
    int consulPort;

    @Option(names = {"--key"}, description = "Consul key path", defaultValue = "config/application")
    String keyPath;

    @Option(names = {"--force"}, description = "Force overwrite existing configuration")
    boolean force;
    
    @Option(names = {"-c", "--config"}, description = "Path to configuration file")
    String configFile;
    
    @Option(names = {"--export"}, description = "Export configuration to file")
    String exportFile;
    
    @Option(names = {"--import"}, description = "Import configuration from file")
    String importFile;
    
    @Option(names = {"-i", "--interactive"}, description = "Start in interactive mode")
    boolean interactive;
    
    @Option(names = {"--validate"}, description = "Validate configuration only (don't write to Consul)")
    boolean validateOnly;
    
    @Option(names = {"--timeout"}, description = "Timeout for Consul operations in seconds", defaultValue = "5")
    int timeout;

    @Override
    public void run() {
        // Validate inputs
        if (!validateInputs()) {
            return;
        }
        
        // Initialize configuration
        ConfigurationModel config = initializeConfiguration();
        if (config == null) {
            return;
        }
        
        // Handle interactive mode
        if (interactive) {
            config = startInteractiveMode(config);
            if (config == null) {
                return;
            }
        }
        
        // Handle export
        if (exportFile != null && !exportFile.isEmpty()) {
            if (!exportConfiguration(config, exportFile)) {
                return;
            }
        }
        
        // Handle validate only
        if (validateOnly) {
            Log.info("Configuration validation successful");
            return;
        }
        
        // Connect to Consul
        Log.infof("Connecting to Consul at %s:%d", consulHost, consulPort);
        
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consulHost)
            .setPort(consulPort);
            
        ConsulClient client = ConsulClient.create(vertx, options);
        
        // Check if config already exists
        CountDownLatch checkLatch = new CountDownLatch(1);
        final boolean[] configExists = {false};
        
        client.getValue(keyPath).onComplete(ar -> {
            if (ar.succeeded() && ar.result() != null && ar.result().getValue() != null) {
                configExists[0] = true;
                Log.infof("Configuration already exists at key: %s", keyPath);
            }
            checkLatch.countDown();
        });
        
        try {
            if (!checkLatch.await(timeout, TimeUnit.SECONDS)) {
                Log.error("Timeout checking existing configuration");
                client.close();
                return;
            }
        } catch (InterruptedException e) {
            Log.error("Interrupted while checking configuration", e);
            client.close();
            return;
        }
        
        // If config exists and not forcing, exit
        if (configExists[0] && !force) {
            Log.info("Configuration already exists. Use --force to overwrite.");
            client.close();
            return;
        }
        
        // Get configuration content
        String configContent = config.toFormattedString();
        
        // Store in Consul
        CountDownLatch putLatch = new CountDownLatch(1);
        final boolean[] success = {false};
        
        client.putValue(keyPath, configContent).onComplete(ar -> {
            if (ar.succeeded() && ar.result()) {
                success[0] = true;
                Log.infof("Successfully seeded configuration to Consul at key: %s", keyPath);
                Log.debug("Configuration content:\n" + configContent);
            } else {
                Log.error("Failed to seed configuration to Consul", ar.cause());
            }
            putLatch.countDown();
        });
        
        try {
            if (!putLatch.await(timeout, TimeUnit.SECONDS)) {
                Log.error("Timeout storing configuration");
                client.close();
                return;
            }
        } catch (InterruptedException e) {
            Log.error("Interrupted while storing configuration", e);
            client.close();
            return;
        }
        
        client.close();
        
        if (success[0]) {
            Log.info("Consul seeding completed successfully!");
        } else {
            Log.error("Consul seeding failed!");
            System.exit(1);
        }
    }
    
    /**
     * Validate command-line inputs
     * @return true if inputs are valid, false otherwise
     */
    private boolean validateInputs() {
        // Validate host
        if (!InputValidator.isValidHost(consulHost)) {
            Log.errorf("Invalid Consul host: %s", consulHost);
            return false;
        }
        
        // Validate port
        if (!InputValidator.isValidPort(consulPort)) {
            Log.errorf("Invalid Consul port: %d", consulPort);
            return false;
        }
        
        // Validate key path
        if (!InputValidator.isValidKeyPath(keyPath)) {
            Log.errorf("Invalid Consul key path: %s", keyPath);
            return false;
        }
        
        // Validate config file if specified
        if (configFile != null && !configFile.isEmpty()) {
            if (!InputValidator.isValidFilePath(configFile)) {
                Log.errorf("Invalid configuration file path: %s", configFile);
                return false;
            }
            
            if (!InputValidator.fileExistsAndReadable(configFile)) {
                Log.errorf("Configuration file does not exist or is not readable: %s", configFile);
                return false;
            }
        }
        
        // Validate export file if specified
        if (exportFile != null && !exportFile.isEmpty()) {
            if (!InputValidator.isValidFilePath(exportFile)) {
                Log.errorf("Invalid export file path: %s", exportFile);
                return false;
            }
            
            // Check if parent directory exists and is writable
            File file = new File(exportFile);
            File parentDir = file.getParentFile();
            if (parentDir != null && !InputValidator.directoryExistsAndWritable(parentDir.getAbsolutePath())) {
                Log.errorf("Export directory does not exist or is not writable: %s", parentDir.getAbsolutePath());
                return false;
            }
        }
        
        // Validate import file if specified
        if (importFile != null && !importFile.isEmpty()) {
            if (!InputValidator.isValidFilePath(importFile)) {
                Log.errorf("Invalid import file path: %s", importFile);
                return false;
            }
            
            if (!InputValidator.fileExistsAndReadable(importFile)) {
                Log.errorf("Import file does not exist or is not readable: %s", importFile);
                return false;
            }
        }
        
        // Validate timeout
        if (timeout <= 0) {
            Log.errorf("Invalid timeout value: %d. Must be greater than 0.", timeout);
            return false;
        }
        
        return true;
    }
    
    /**
     * Initialize configuration from various sources
     * @return The initialized configuration model, or null if initialization failed
     */
    private ConfigurationModel initializeConfiguration() {
        ConfigurationModel config = new ConfigurationModel();
        
        try {
            // Load from config file if specified
            if (configFile != null && !configFile.isEmpty()) {
                Log.infof("Loading configuration from file: %s", configFile);
                config = ConfigFileHandler.loadFromFile(configFile);
            }
            
            // Import and merge if specified
            if (importFile != null && !importFile.isEmpty()) {
                Log.infof("Importing configuration from file: %s", importFile);
                config = ConfigFileHandler.importAndMergeConfig(config, importFile);
            }
            
            return config;
        } catch (IOException e) {
            Log.error("Error initializing configuration", e);
            return null;
        }
    }
    
    /**
     * Start interactive mode
     * @param initialConfig The initial configuration
     * @return The updated configuration model, or null if interactive mode failed
     */
    private ConfigurationModel startInteractiveMode(ConfigurationModel initialConfig) {
        Log.info("Starting interactive mode");
        try {
            InteractiveMode interactiveMode = new InteractiveMode(initialConfig);
            return interactiveMode.start();
        } catch (Exception e) {
            Log.error("Error in interactive mode", e);
            return null;
        }
    }
    
    /**
     * Export configuration to a file
     * @param config The configuration to export
     * @param filePath The file path to export to
     * @return true if export was successful, false otherwise
     */
    private boolean exportConfiguration(ConfigurationModel config, String filePath) {
        try {
            ConfigFileHandler.exportConfig(config, filePath);
            Log.infof("Configuration exported to: %s", filePath);
            return true;
        } catch (IOException e) {
            Log.error("Error exporting configuration", e);
            return false;
        }
    }
    
    private String createDefaultConfiguration() {
        return new ConfigurationModel().toFormattedString();
    }
}