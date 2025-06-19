package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;

/**
 * Integration test for PipelineConfigService running in prod mode.
 * Extends PipelineConfigServiceTestBase to reuse common test logic.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
class PipelineConfigServiceIT extends PipelineConfigServiceTestBase {

    private PipelineConfigServiceClient pipelineConfigServiceClient;
    private ClusterServiceClient clusterServiceClient;

    @BeforeEach
    void initClients() {
        // Create REST clients for accessing the services
        // In integration tests, we access services through HTTP endpoints
        String baseUrl = RestAssured.baseURI + ":" + RestAssured.port;
        
        this.pipelineConfigServiceClient = new PipelineConfigServiceClient(baseUrl);
        this.clusterServiceClient = new ClusterServiceClient(baseUrl);
    }

    @Override
    protected PipelineConfigService getPipelineConfigService() {
        return pipelineConfigServiceClient;
    }

    @Override
    protected ClusterService getClusterService() {
        return clusterServiceClient;
    }

    /**
     * REST client adapter for PipelineConfigService.
     * Implements the service interface by making HTTP calls.
     */
    private static class PipelineConfigServiceClient implements PipelineConfigService {
        private final String baseUrl;

        PipelineConfigServiceClient(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        // TODO: Implement REST client methods that delegate to HTTP endpoints
        // This is a placeholder for now - in a real implementation,
        // these would make actual HTTP calls to the REST endpoints
        
        @Override
        public io.smallrye.mutiny.Uni<com.rokkon.pipeline.validation.ValidationResult> createPipeline(
                String clusterName, String pipelineId, com.rokkon.pipeline.config.model.PipelineConfig config) {
            // TODO: Implement REST call
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public io.smallrye.mutiny.Uni<com.rokkon.pipeline.validation.ValidationResult> updatePipeline(
                String clusterName, String pipelineId, com.rokkon.pipeline.config.model.PipelineConfig config) {
            // TODO: Implement REST call
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public io.smallrye.mutiny.Uni<com.rokkon.pipeline.validation.ValidationResult> deletePipeline(
                String clusterName, String pipelineId) {
            // TODO: Implement REST call
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public io.smallrye.mutiny.Uni<java.util.Optional<com.rokkon.pipeline.config.model.PipelineConfig>> getPipeline(
                String clusterName, String pipelineId) {
            // TODO: Implement REST call
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public io.smallrye.mutiny.Uni<java.util.Map<String, com.rokkon.pipeline.config.model.PipelineConfig>> listPipelines(
                String clusterName) {
            // TODO: Implement REST call
            throw new UnsupportedOperationException("Not implemented yet");
        }
    }

    /**
     * REST client adapter for ClusterService.
     * Implements the service interface by making HTTP calls.
     */
    private static class ClusterServiceClient implements ClusterService {
        private final String baseUrl;

        ClusterServiceClient(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        // TODO: Implement REST client methods
        
        @Override
        public io.smallrye.mutiny.Uni<com.rokkon.pipeline.validation.ValidationResult> createCluster(String clusterName) {
            // TODO: Implement REST call
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public io.smallrye.mutiny.Uni<java.util.List<com.rokkon.pipeline.consul.model.Cluster>> listClusters() {
            // TODO: Implement REST call
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public io.smallrye.mutiny.Uni<com.rokkon.pipeline.consul.model.Cluster> getCluster(String clusterName) {
            // TODO: Implement REST call
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public io.smallrye.mutiny.Uni<com.rokkon.pipeline.validation.ValidationResult> deleteCluster(String clusterName) {
            // TODO: Implement REST call
            throw new UnsupportedOperationException("Not implemented yet");
        }
    }
}