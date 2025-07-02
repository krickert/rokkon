package com.rokkon.pipeline.seed.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Utility class for validating user inputs to prevent injection attacks and ensure data integrity.
 */
public class InputValidator {
    
    // Regex patterns for validation
    private static final Pattern HOSTNAME_PATTERN = Pattern.compile(
            "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*" +
            "([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$");
    
    private static final Pattern IP_PATTERN = Pattern.compile(
            "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}" +
            "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$");
    
    private static final Pattern KEY_PATH_PATTERN = Pattern.compile("^[a-zA-Z0-9._\\-/]+$");
    
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile("^[a-zA-Z0-9._\\-/\\\\:]+$");
    
    /**
     * Validate a hostname or IP address
     * @param host The hostname or IP address to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidHost(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        
        // Check for localhost
        if ("localhost".equalsIgnoreCase(host)) {
            return true;
        }
        
        // Check if it's a valid IP address
        if (IP_PATTERN.matcher(host).matches()) {
            return true;
        }
        
        // Check if it's a valid hostname
        return HOSTNAME_PATTERN.matcher(host).matches();
    }
    
    /**
     * Validate a port number
     * @param port The port number to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidPort(int port) {
        return port > 0 && port <= 65535;
    }
    
    /**
     * Validate a Consul key path
     * @param keyPath The key path to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidKeyPath(String keyPath) {
        if (keyPath == null || keyPath.isEmpty()) {
            return false;
        }
        
        // Check if it matches the pattern
        return KEY_PATH_PATTERN.matcher(keyPath).matches();
    }
    
    /**
     * Validate a file path
     * @param filePath The file path to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidFilePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }
        
        // Check if it matches the pattern
        if (!FILE_PATH_PATTERN.matcher(filePath).matches()) {
            return false;
        }
        
        try {
            // Check if it's a valid path
            Path path = Paths.get(filePath);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Validate a URL
     * @param url The URL to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        try {
            new URI(url);
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }
    
    /**
     * Sanitize a string to prevent injection attacks
     * @param input The input string to sanitize
     * @return The sanitized string
     */
    public static String sanitizeString(String input) {
        if (input == null) {
            return null;
        }
        
        // Remove control characters and non-printable characters
        return input.replaceAll("[\\p{Cntrl}]", "");
    }
    
    /**
     * Validate that a file exists and is readable
     * @param filePath The file path to validate
     * @return true if the file exists and is readable, false otherwise
     */
    public static boolean fileExistsAndReadable(String filePath) {
        if (!isValidFilePath(filePath)) {
            return false;
        }
        
        Path path = Paths.get(filePath);
        return Files.exists(path) && Files.isReadable(path);
    }
    
    /**
     * Validate that a directory exists and is writable
     * @param dirPath The directory path to validate
     * @return true if the directory exists and is writable, false otherwise
     */
    public static boolean directoryExistsAndWritable(String dirPath) {
        if (!isValidFilePath(dirPath)) {
            return false;
        }
        
        Path path = Paths.get(dirPath);
        return Files.exists(path) && Files.isDirectory(path) && Files.isWritable(path);
    }
    
    /**
     * Validate a configuration key
     * @param key The configuration key to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidConfigKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        
        // Configuration keys should only contain alphanumeric characters, dots, hyphens, and underscores
        return key.matches("^[a-zA-Z0-9._\\-]+$");
    }
    
    /**
     * Validate a configuration value
     * @param value The configuration value to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidConfigValue(String value) {
        // Configuration values can be any string, but we should sanitize them
        return value != null;
    }
}