package com.rokkon.integration;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic test to verify integration test setup is working
 * without requiring Docker containers.
 */
@QuarkusIntegrationTest
public class BasicIntegrationTestCheck {
    
    @Test
    public void testIntegrationTestFrameworkWorks() {
        // Simple test to verify the integration test framework is set up correctly
        assertThat(1 + 1).isEqualTo(2);
        System.out.println("✅ Integration test framework is working!");
    }
}