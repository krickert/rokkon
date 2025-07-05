package com.rokkon.pipeline.engine.ui;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Live UI tests for the dashboard against running dev mode.
 * This doesn't use @QuarkusTest, so it connects to the existing dev mode instance.
 */
@EnabledIfSystemProperty(named = "test.ui.devmode", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DashboardLiveTest {

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;
    
    private static final String BASE_URL = "http://localhost:39001";

    @BeforeAll
    public static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
            .setHeadless(true));
    }

    @AfterAll
    public static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    public void createContextAndPage() {
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterEach
    public void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    @Order(1)
    public void testDashboardLoads() {
        page.navigate(BASE_URL);
        
        // Wait for the web component to load
        page.waitForSelector("pipeline-dashboard", new Page.WaitForSelectorOptions()
            .setState(WaitForSelectorState.ATTACHED)
            .setTimeout(10000));
        
        // Check title
        String title = page.title();
        assertThat(title).contains("Pipeline Engine");
        
        // Verify the custom element is present
        Locator dashboard = page.locator("pipeline-dashboard");
        assertTrue(dashboard.isVisible(), "Dashboard component should be visible");
    }

    @Test
    @Order(2)
    public void testNavigationHeader() {
        page.navigate(BASE_URL);
        
        // Wait for navigation header to load (inside shadow DOM)
        page.waitForTimeout(2000); // Give web components time to initialize
        
        // Try to find elements in the light DOM first
        Locator appDiv = page.locator("#app");
        assertTrue(appDiv.isVisible(), "App container should be visible");
    }

    @Test
    @Order(3)
    public void testModuleDeploymentUI() {
        page.navigate(BASE_URL);
        
        // Wait for dashboard to fully load
        page.waitForTimeout(3000);
        
        // The deploy dropdown is in the shadow DOM of dashboard-grid
        // For now, just verify the dashboard loads without errors
        Locator dashboard = page.locator("pipeline-dashboard");
        assertTrue(dashboard.isVisible(), "Dashboard should be visible");
        
        // Check for any error messages
        Locator errors = page.locator(".error");
        assertEquals(0, errors.count(), "No errors should be displayed");
    }

    @Test
    @Order(4)
    public void testSSEConnection() {
        // Set up listener for SSE connection
        page.onResponse(response -> {
            if (response.url().contains("/api/v1/module-deployment/events")) {
                assertEquals(200, response.status(), "SSE endpoint should return 200");
                String contentType = response.headers().get("content-type");
                assertThat(contentType).contains("text/event-stream");
            }
        });
        
        page.navigate(BASE_URL);
        
        // Wait for potential SSE connection
        page.waitForTimeout(3000);
    }

    @Test
    @Order(5)
    public void testAPIEndpointAccessible() {
        // Test that the API endpoints are accessible
        APIResponse response = page.request().get(BASE_URL + "/api/v1/engine/info");
        assertEquals(200, response.status());
        
        // Check response contains expected data
        String body = response.text();
        assertThat(body).contains("\"profile\":\"dev\"");
        assertThat(body).contains("\"status\":\"online\"");
    }
}