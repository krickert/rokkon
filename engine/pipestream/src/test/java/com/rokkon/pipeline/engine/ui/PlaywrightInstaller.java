package com.rokkon.pipeline.engine.ui;

import com.microsoft.playwright.CLI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Helper to install Playwright browsers.
 * Run with: ./gradlew test --tests "PlaywrightInstaller.installPlaywright"
 */
public class PlaywrightInstaller {
    
    @Test
    @EnabledIfSystemProperty(named = "playwright.install", matches = "true")
    public void installPlaywright() {
        System.out.println("Installing Playwright browsers...");
        try {
            CLI.main(new String[]{"install"});
            System.out.println("Playwright browsers installed successfully!");
        } catch (Exception e) {
            System.err.println("Failed to install Playwright browsers: " + e.getMessage());
            throw new RuntimeException("Playwright installation failed", e);
        }
    }
}