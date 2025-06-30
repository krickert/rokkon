package com.rokkon.testing.server;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.KafkaContainer;

import static org.assertj.core.api.Assertions.assertThat;

class QuarkusTestContainerSupportTest {
    
    @Test
    void testCreatePostgresContainer() {
        PostgreSQLContainer<?> container = QuarkusTestContainerSupport.createPostgresContainer();
        
        assertThat(container).isNotNull();
        assertThat(container.getDatabaseName()).isEqualTo("test");
        assertThat(container.getUsername()).isEqualTo("test");
        assertThat(container.getPassword()).isEqualTo("test");
    }
    
    @Test
    void testCreateKafkaContainer() {
        KafkaContainer container = QuarkusTestContainerSupport.createKafkaContainer();
        
        assertThat(container).isNotNull();
        assertThat(container.getNetworkAliases()).contains("kafka");
    }
    
    @Test
    void testCreateQuarkusAppContainer() {
        var container = QuarkusTestContainerSupport.createQuarkusAppContainer("test-app:latest");
        
        assertThat(container).isNotNull();
        assertThat(container.getExposedPorts()).contains(8080, 9000);
        assertThat(container.getEnvMap()).containsEntry("QUARKUS_PROFILE", "test");
    }
}