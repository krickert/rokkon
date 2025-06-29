package com.rokkon.pipeline.events;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ModuleRegistrationResponseEventTest {

    @Test
    void testSuccessFactoryMethod() {
        ModuleRegistrationResponseEvent event = ModuleRegistrationResponseEvent.success(
            "req-123",
            "module-456",
            "test-module",
            "Module registered successfully"
        );

        assertThat(event.requestId()).isEqualTo("req-123");
        assertThat(event.success()).isTrue();
        assertThat(event.message()).isEqualTo("Module registered successfully");
        assertThat(event.moduleId()).isEqualTo("module-456");
        assertThat(event.moduleName()).isEqualTo("test-module");
        assertThat(event.error()).isNull();
    }

    @Test
    void testFailureFactoryMethod() {
        ModuleRegistrationResponseEvent event = ModuleRegistrationResponseEvent.failure(
            "req-789",
            "Connection refused to Consul"
        );

        assertThat(event.requestId()).isEqualTo("req-789");
        assertThat(event.success()).isFalse();
        assertThat(event.message()).isEqualTo("Registration failed");
        assertThat(event.moduleId()).isNull();
        assertThat(event.moduleName()).isNull();
        assertThat(event.error()).isEqualTo("Connection refused to Consul");
    }

    @Test
    void testDirectRecordCreation() {
        ModuleRegistrationResponseEvent event = new ModuleRegistrationResponseEvent(
            "req-custom",
            true,
            "Custom message",
            "module-custom",
            "custom-module",
            null
        );

        assertThat(event.requestId()).isEqualTo("req-custom");
        assertThat(event.success()).isTrue();
        assertThat(event.message()).isEqualTo("Custom message");
        assertThat(event.moduleId()).isEqualTo("module-custom");
        assertThat(event.moduleName()).isEqualTo("custom-module");
        assertThat(event.error()).isNull();
    }

    @Test
    void testRecordEquality() {
        ModuleRegistrationResponseEvent event1 = ModuleRegistrationResponseEvent.success(
            "req-1",
            "module-1",
            "module",
            "Success"
        );
        
        ModuleRegistrationResponseEvent event2 = ModuleRegistrationResponseEvent.success(
            "req-1",
            "module-1",
            "module",
            "Success"
        );
        
        ModuleRegistrationResponseEvent event3 = ModuleRegistrationResponseEvent.success(
            "req-2", // Different request ID
            "module-1",
            "module",
            "Success"
        );

        assertThat(event1).isEqualTo(event2);
        assertThat(event1).isNotEqualTo(event3);
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    void testToString() {
        ModuleRegistrationResponseEvent successEvent = ModuleRegistrationResponseEvent.success(
            "req-toString",
            "module-toString",
            "toString-module",
            "Test message"
        );

        String eventString = successEvent.toString();
        assertThat(eventString).contains("req-toString");
        assertThat(eventString).contains("true"); // success=true
        assertThat(eventString).contains("module-toString");
        assertThat(eventString).contains("toString-module");
        assertThat(eventString).contains("Test message");
    }

    @Test
    void testFailureWithNullError() {
        ModuleRegistrationResponseEvent event = ModuleRegistrationResponseEvent.failure(
            "req-null-error",
            null
        );

        assertThat(event.requestId()).isEqualTo("req-null-error");
        assertThat(event.success()).isFalse();
        assertThat(event.error()).isNull();
        assertThat(event.moduleId()).isNull();
        assertThat(event.moduleName()).isNull();
    }

    @Test
    void testSuccessWithNullValues() {
        ModuleRegistrationResponseEvent event = ModuleRegistrationResponseEvent.success(
            null,  // null request ID
            null,  // null module ID
            null,  // null module name
            null   // null message
        );

        assertThat(event.requestId()).isNull();
        assertThat(event.success()).isTrue();
        assertThat(event.message()).isNull();
        assertThat(event.moduleId()).isNull();
        assertThat(event.moduleName()).isNull();
        assertThat(event.error()).isNull();
    }

    @Test
    void testMixedStateEvent() {
        // Test a contradictory state (success=false but has moduleId)
        ModuleRegistrationResponseEvent event = new ModuleRegistrationResponseEvent(
            "req-mixed",
            false,  // failure
            "Mixed state",
            "module-mixed",  // has module ID despite failure
            "mixed-module",
            "Some error"
        );

        assertThat(event.success()).isFalse();
        assertThat(event.moduleId()).isEqualTo("module-mixed");
        assertThat(event.error()).isEqualTo("Some error");
        // This shows the record allows inconsistent states - might want validation in production
    }
}