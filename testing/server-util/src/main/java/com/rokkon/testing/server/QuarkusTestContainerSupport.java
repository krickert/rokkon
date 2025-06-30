package com.rokkon.testing.server;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class providing common Testcontainers setup for Quarkus server integration tests.
 */
public abstract class QuarkusTestContainerSupport {
    
    protected static final Network TEST_NETWORK = Network.newNetwork();
    
    /**
     * Creates a PostgreSQL container configured for Quarkus testing.
     */
    protected static PostgreSQLContainer<?> createPostgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                .withNetwork(TEST_NETWORK)
                .withNetworkAliases("postgres")
                .withDatabaseName("test")
                .withUsername("test")
                .withPassword("test");
    }
    
    /**
     * Creates a Kafka container configured for Quarkus testing.
     */
    protected static KafkaContainer createKafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
                .withNetwork(TEST_NETWORK)
                .withNetworkAliases("kafka");
    }
    
    /**
     * Creates a generic container for a Quarkus application.
     */
    protected static GenericContainer<?> createQuarkusAppContainer(String imageName) {
        return new GenericContainer<>(DockerImageName.parse(imageName))
                .withNetwork(TEST_NETWORK)
                .withExposedPorts(8080, 9000) // HTTP and gRPC ports
                .withEnv("QUARKUS_PROFILE", "test");
    }
}