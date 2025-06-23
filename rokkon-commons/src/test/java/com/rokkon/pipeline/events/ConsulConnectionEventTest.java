package com.rokkon.pipeline.events;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ConsulConnectionEventTest {

    @Test
    void testConnectedEvent() {
        ConsulConnectionEvent.ConsulConnectionConfig config = 
            new ConsulConnectionEvent.ConsulConnectionConfig("consul.example.com", 8500, true);
        
        ConsulConnectionEvent event = new ConsulConnectionEvent(
            ConsulConnectionEvent.Type.CONNECTED,
            config,
            "Successfully connected to Consul"
        );

        assertThat(event.getType()).isEqualTo(ConsulConnectionEvent.Type.CONNECTED);
        assertThat(event.getConfig()).isEqualTo(config);
        assertThat(event.getMessage()).isEqualTo("Successfully connected to Consul");
    }

    @Test
    void testDisconnectedEvent() {
        ConsulConnectionEvent.ConsulConnectionConfig config = 
            new ConsulConnectionEvent.ConsulConnectionConfig("localhost", 8500, false);
        
        ConsulConnectionEvent event = new ConsulConnectionEvent(
            ConsulConnectionEvent.Type.DISCONNECTED,
            config,
            "Lost connection to Consul"
        );

        assertThat(event.getType()).isEqualTo(ConsulConnectionEvent.Type.DISCONNECTED);
        assertThat(event.getConfig()).isEqualTo(config);
        assertThat(event.getMessage()).isEqualTo("Lost connection to Consul");
    }

    @Test
    void testConnectionFailedEvent() {
        ConsulConnectionEvent.ConsulConnectionConfig config = 
            new ConsulConnectionEvent.ConsulConnectionConfig("consul.local", 8500, false);
        
        ConsulConnectionEvent event = new ConsulConnectionEvent(
            ConsulConnectionEvent.Type.CONNECTION_FAILED,
            config,
            "Connection refused: consul.local:8500"
        );

        assertThat(event.getType()).isEqualTo(ConsulConnectionEvent.Type.CONNECTION_FAILED);
        assertThat(event.getConfig()).isEqualTo(config);
        assertThat(event.getMessage()).isEqualTo("Connection refused: consul.local:8500");
    }

    @Test
    void testConsulConnectionConfig() {
        ConsulConnectionEvent.ConsulConnectionConfig config = 
            new ConsulConnectionEvent.ConsulConnectionConfig("192.168.1.100", 8501, true);

        assertThat(config.host()).isEqualTo("192.168.1.100");
        assertThat(config.port()).isEqualTo(8501);
        assertThat(config.connected()).isTrue();
    }

    @Test
    void testConfigEquality() {
        ConsulConnectionEvent.ConsulConnectionConfig config1 = 
            new ConsulConnectionEvent.ConsulConnectionConfig("host1", 8500, true);
        
        ConsulConnectionEvent.ConsulConnectionConfig config2 = 
            new ConsulConnectionEvent.ConsulConnectionConfig("host1", 8500, true);
        
        ConsulConnectionEvent.ConsulConnectionConfig config3 = 
            new ConsulConnectionEvent.ConsulConnectionConfig("host2", 8500, true);

        assertThat(config1).isEqualTo(config2);
        assertThat(config1).isNotEqualTo(config3);
        assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }

    @Test
    void testConfigToString() {
        ConsulConnectionEvent.ConsulConnectionConfig config = 
            new ConsulConnectionEvent.ConsulConnectionConfig("consul-server", 8500, false);

        String configString = config.toString();
        assertThat(configString).contains("consul-server");
        assertThat(configString).contains("8500");
        assertThat(configString).contains("false");
    }

    @Test
    void testEventWithNullValues() {
        ConsulConnectionEvent event = new ConsulConnectionEvent(
            ConsulConnectionEvent.Type.CONNECTED,
            null,  // null config
            null   // null message
        );

        assertThat(event.getType()).isEqualTo(ConsulConnectionEvent.Type.CONNECTED);
        assertThat(event.getConfig()).isNull();
        assertThat(event.getMessage()).isNull();
    }

    @Test
    void testAllEventTypes() {
        // Verify all enum values are accessible
        ConsulConnectionEvent.Type[] types = ConsulConnectionEvent.Type.values();
        
        assertThat(types).containsExactly(
            ConsulConnectionEvent.Type.CONNECTED,
            ConsulConnectionEvent.Type.DISCONNECTED,
            ConsulConnectionEvent.Type.CONNECTION_FAILED
        );
    }

    @Test
    void testEventTypeValueOf() {
        assertThat(ConsulConnectionEvent.Type.valueOf("CONNECTED"))
            .isEqualTo(ConsulConnectionEvent.Type.CONNECTED);
        assertThat(ConsulConnectionEvent.Type.valueOf("DISCONNECTED"))
            .isEqualTo(ConsulConnectionEvent.Type.DISCONNECTED);
        assertThat(ConsulConnectionEvent.Type.valueOf("CONNECTION_FAILED"))
            .isEqualTo(ConsulConnectionEvent.Type.CONNECTION_FAILED);
    }

    @Test
    void testConfigWithZeroPort() {
        ConsulConnectionEvent.ConsulConnectionConfig config = 
            new ConsulConnectionEvent.ConsulConnectionConfig("consul", 0, false);

        assertThat(config.port()).isEqualTo(0);
    }

    @Test
    void testConfigWithNullHost() {
        ConsulConnectionEvent.ConsulConnectionConfig config = 
            new ConsulConnectionEvent.ConsulConnectionConfig(null, 8500, true);

        assertThat(config.host()).isNull();
        assertThat(config.port()).isEqualTo(8500);
        assertThat(config.connected()).isTrue();
    }
}