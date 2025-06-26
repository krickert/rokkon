package com.rokkon.engine;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.health.ServiceHealth;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@QuarkusIntegrationTest
public class ConsulIntegrationTest {

    @Container
    public static GenericContainer<?> consulContainer = new GenericContainer<>("consul:latest")
            .withExposedPorts(8500)
            .withCommand("agent", "-dev", "-client", "0.0.0.0");

    @Test
    void testConsulIsRunning() {
        Consul client = Consul.builder()
                .withHostAndPort("localhost", consulContainer.getMappedPort(8500))
                .build();

        HealthClient healthClient = client.healthClient();
        List<ServiceHealth> nodes = healthClient.getHealthyServiceInstances("consul").getResponse();

        assertNotNull(nodes);
        assertTrue(!nodes.isEmpty());
        System.out.println("Consul is running and accessible.");
    }
}
