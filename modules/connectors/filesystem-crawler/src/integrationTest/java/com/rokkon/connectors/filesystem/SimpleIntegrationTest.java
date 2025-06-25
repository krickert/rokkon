package com.rokkon.connectors.filesystem;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify that the integration test environment is working correctly.
 */
@QuarkusTest
public class SimpleIntegrationTest {

    @Test
    void testSimpleAssertion() {
        // Just a simple assertion to verify that the test environment is working
        assertTrue(true, "This test should always pass");
    }
}