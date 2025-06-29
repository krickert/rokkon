package com.rokkon.pipeline.events;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ModuleRegistrationRequestEventTest {

    @Test
    void testRecordCreation() {
        Map<String, String> metadata = Map.of("key1", "value1", "key2", "value2");
        
        ModuleRegistrationRequestEvent event = new ModuleRegistrationRequestEvent(
            "test-module",
            "impl-123",
            "localhost",
            8080,
            "GRPC",
            "1.0.0",
            metadata,
            "engine-host",
            49000,
            "{\"type\": \"object\"}",
            "req-123"
        );

        assertThat(event.moduleName()).isEqualTo("test-module");
        assertThat(event.implementationId()).isEqualTo("impl-123");
        assertThat(event.host()).isEqualTo("localhost");
        assertThat(event.port()).isEqualTo(8080);
        assertThat(event.serviceType()).isEqualTo("GRPC");
        assertThat(event.version()).isEqualTo("1.0.0");
        assertThat(event.metadata()).isEqualTo(metadata);
        assertThat(event.engineHost()).isEqualTo("engine-host");
        assertThat(event.enginePort()).isEqualTo(49000);
        assertThat(event.jsonSchema()).isEqualTo("{\"type\": \"object\"}");
        assertThat(event.requestId()).isEqualTo("req-123");
    }

    @Test
    void testCreateMethodWithNonNullMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("region", "us-west");
        metadata.put("env", "prod");
        
        ModuleRegistrationRequestEvent event = ModuleRegistrationRequestEvent.create(
            "processor-module",
            "proc-456",
            "10.0.0.1",
            9090,
            "HTTP",
            "2.1.0",
            metadata,
            "engine.example.com",
            50000,
            null,
            "request-456"
        );

        assertThat(event.metadata()).containsExactlyInAnyOrderEntriesOf(metadata);
        assertThat(event.metadata()).isNotSameAs(metadata); // Should be a copy
        
        // Verify immutability - changes to original map don't affect event
        metadata.put("new-key", "new-value");
        assertThat(event.metadata()).doesNotContainKey("new-key");
    }

    @Test
    void testCreateMethodWithNullMetadata() {
        ModuleRegistrationRequestEvent event = ModuleRegistrationRequestEvent.create(
            "null-metadata-module",
            "impl-789",
            "192.168.1.1",
            7070,
            "GRPC",
            "3.0.0",
            null,
            "engine-local",
            49000,
            "{}",
            "req-789"
        );

        assertThat(event.metadata()).isNotNull();
        assertThat(event.metadata()).isEmpty();
    }

    @Test
    void testRecordEquality() {
        Map<String, String> metadata = Map.of("test", "data");
        
        ModuleRegistrationRequestEvent event1 = ModuleRegistrationRequestEvent.create(
            "module",
            "impl",
            "host",
            8080,
            "GRPC",
            "1.0",
            metadata,
            "engine",
            49000,
            "schema",
            "req-1"
        );
        
        ModuleRegistrationRequestEvent event2 = ModuleRegistrationRequestEvent.create(
            "module",
            "impl",
            "host",
            8080,
            "GRPC",
            "1.0",
            metadata,
            "engine",
            49000,
            "schema",
            "req-1"
        );
        
        ModuleRegistrationRequestEvent event3 = ModuleRegistrationRequestEvent.create(
            "module",
            "impl",
            "host",
            8080,
            "GRPC",
            "1.0",
            metadata,
            "engine",
            49000,
            "schema",
            "req-2" // Different request ID
        );

        assertThat(event1).isEqualTo(event2);
        assertThat(event1).isNotEqualTo(event3);
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    void testToString() {
        ModuleRegistrationRequestEvent event = ModuleRegistrationRequestEvent.create(
            "string-test-module",
            "impl-999",
            "test-host",
            3000,
            "REST",
            "0.1.0",
            Map.of("key", "value"),
            "engine-host",
            49000,
            "schema",
            "req-999"
        );

        String eventString = event.toString();
        assertThat(eventString).contains("string-test-module");
        assertThat(eventString).contains("impl-999");
        assertThat(eventString).contains("test-host");
        assertThat(eventString).contains("3000");
        assertThat(eventString).contains("req-999");
    }

    @Test
    void testNullValuesInFields() {
        // Test that null values are handled appropriately
        ModuleRegistrationRequestEvent event = ModuleRegistrationRequestEvent.create(
            null,  // null module name
            null,  // null implementation ID
            null,  // null host
            0,     // zero port
            null,  // null service type
            null,  // null version
            null,  // null metadata
            null,  // null engine host
            0,     // zero engine port
            null,  // null json schema
            null   // null request ID
        );

        assertThat(event.moduleName()).isNull();
        assertThat(event.implementationId()).isNull();
        assertThat(event.host()).isNull();
        assertThat(event.port()).isEqualTo(0);
        assertThat(event.serviceType()).isNull();
        assertThat(event.version()).isNull();
        assertThat(event.metadata()).isEmpty(); // null metadata becomes empty map
        assertThat(event.engineHost()).isNull();
        assertThat(event.enginePort()).isEqualTo(0);
        assertThat(event.jsonSchema()).isNull();
        assertThat(event.requestId()).isNull();
    }
}