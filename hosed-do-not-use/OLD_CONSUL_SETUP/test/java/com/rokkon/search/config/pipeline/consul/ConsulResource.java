package com.rokkon.search.config.pipeline.consul;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.consul.ConsulContainer;

import java.util.Map;

public class ConsulResource implements QuarkusTestResourceLifecycleManager {

    private ConsulContainer consulContainer;

    @Override
    public Map<String, String> start() {
        consulContainer = new ConsulContainer("hashicorp/consul:1.15.3")
                .withConsulCommand(
                        """
                        kv put rokkon-clusters/test-cluster/config - <<EOF
                        {
                          "clusterName": "test-cluster",
                          "defaultPipelineName": "document-processing",
                          "allowedKafkaTopics": ["input-documents", "processed-chunks"],
                          "allowedGrpcServices": ["chunker-service", "embedder-service"]
                        }
                        EOF
                        """
                );

        consulContainer.start();

        String url = consulContainer.getHost() + ":" + consulContainer.getFirstMappedPort();

        return Map.of(
                "quarkus.consul-config.agent.host-port", url,
                "quarkus.consul-config.enabled", "true",
                "consul.host", consulContainer.getHost(),
                "consul.port", consulContainer.getFirstMappedPort().toString()
        );
    }

    @Override
    public void stop() {
        if (consulContainer != null) {
            consulContainer.stop();
        }
    }
}