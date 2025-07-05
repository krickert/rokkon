package com.rokkon.pipeline.engine.ui;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.quarkiverse.playwright.InjectPlaywright;
import io.quarkiverse.playwright.WithPlaywright;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UI tests for the dashboard in dev mode.
 * Only runs when explicitly enabled via system property.
 */
@QuarkusTest  // Regular test, not integration test - so we can inject
@TestProfile(DevModeTestProfile.class)
@WithPlaywright(debug = false, headless = true)
@EnabledIfSystemProperty(named = "test.ui.devmode", matches = "true")
public class DashboardDevModeTest {

    @InjectPlaywright
    BrowserContext context;
    
    @TestHTTPResource("/")
    URL indexUrl;

    @Test
    public void testDashboardLoads() {
        Page page = context.newPage();
        page.navigate(indexUrl.toString());
        
        // Wait for dashboard to load
        page.waitForSelector("pipeline-dashboard", new Page.WaitForSelectorOptions()
            .setState(WaitForSelectorState.ATTACHED)
            .setTimeout(10000));
        
        // Check title
        String title = page.title();
        assertThat(title).contains("Pipeline Engine");
        
        // Verify navigation header is present
        Locator navHeader = page.locator("navigation-header");
        assertTrue(navHeader.isVisible(), "Navigation header should be visible");
        
        // Check for dev mode badge
        Locator devBadge = page.locator(".profile-badge");
        assertTrue(devBadge.isVisible(), "Dev mode badge should be visible");
        assertThat(devBadge.innerText()).containsIgnoringCase("dev");
    }

    @Test
    public void testModuleDeploymentUI() {
        Page page = context.newPage();
        page.navigate(indexUrl.toString());
        
        // Wait for dashboard
        page.waitForSelector("dashboard-grid");
        
        // Look for deploy module dropdown (only in dev mode)
        Locator deployDropdown = page.locator("module-deploy-dropdown");
        assertTrue(deployDropdown.isVisible(), "Deploy dropdown should be visible in dev mode");
        
        // Click the deploy button to open dropdown
        Locator deployButton = page.locator(".deploy-button");
        deployButton.click();
        
        // Check that dropdown opens
        Locator dropdownContainer = page.locator(".dropdown-container.open");
        assertTrue(dropdownContainer.isVisible(), "Dropdown should open when clicked");
        
        // Verify echo module is available
        Locator echoModule = page.locator(".module-item").filter(new Locator.FilterOptions()
            .setHasText("echo"));
        assertTrue(echoModule.isVisible(), "Echo module should be in the list");
    }

    @Test 
    public void testSSEConnection() {
        Page page = context.newPage();
        
        // Set up listener for SSE connection
        page.onResponse(response -> {
            if (response.url().contains("/api/v1/module-deployment/events")) {
                assertEquals(200, response.status(), "SSE endpoint should return 200");
                String contentType = response.headers().get("content-type");
                assertThat(contentType).contains("text/event-stream");
            }
        });
        
        page.navigate(indexUrl.toString());
        
        // Wait a bit for SSE to connect
        page.waitForTimeout(2000);
    }

    @Test
    public void testDeploymentAnimation() {
        Page page = context.newPage();
        page.navigate(indexUrl.toString());
        
        // Wait for dashboard
        page.waitForSelector("dashboard-grid");
        
        // Open deploy dropdown
        page.locator(".deploy-button").click();
        
        // Click echo module to deploy
        Locator echoModule = page.locator(".module-item").filter(new Locator.FilterOptions()
            .setHasText("echo"));
        echoModule.click();
        
        // Should see deploying card with rocket animation
        Locator deployingCard = page.locator(".deploying-card");
        deployingCard.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(5000));
        
        assertTrue(deployingCard.isVisible(), "Deploying card should appear");
        
        // Check for rocket icon
        Locator rocketIcon = page.locator(".rocket-icon");
        assertTrue(rocketIcon.isVisible(), "Rocket icon should be visible");
        
        // Check for progress bar
        Locator progressBar = page.locator(".deploying-progress-bar");
        assertTrue(progressBar.isVisible(), "Progress bar should be visible");
        
        // Wait for deployment to complete (card should disappear)
        deployingCard.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.HIDDEN)
            .setTimeout(30000));
        
        // Module should now appear in the list
        Locator moduleService = page.locator(".module-service").filter(new Locator.FilterOptions()
            .setHasText("echo"));
        assertTrue(moduleService.isVisible(), "Echo module should appear after deployment");
    }
}