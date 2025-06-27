package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.config.service.ModuleWhitelistService;
import com.rokkon.pipeline.consul.model.ModuleWhitelistRequest;
import com.rokkon.pipeline.consul.model.ModuleWhitelistResponse;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test class for ModuleWhitelistService testing.
 * No mocking - uses real services and validation.
 */
public abstract class ModuleWhitelistServiceTestBase {

    protected ModuleWhitelistService whitelistService;
    protected ObjectMapper objectMapper;
    protected HttpClient httpClient;

    protected static final String TEST_CLUSTER = "whitelist-test-cluster";
    protected static final String TEST_MODULE = "test-processor";
    protected static final String TEST_MODULE_2 = "echo-processor";

    /**
     * Get the Consul host for this test environment.
     */
    protected abstract String getConsulHost();

    /**
     * Get the Consul port for this test environment.
     */
    protected abstract String getConsulPort();

    /**
     * Get the ModuleWhitelistService instance.
     */
    protected abstract ModuleWhitelistService getWhitelistService();

    /**
     * Register a module in Consul for testing.
     * This simulates what happens when a real module registers.
     */
    protected abstract Uni<Boolean> registerModuleInConsul(String moduleName, String host, int port);

    @BeforeEach
    void setUpBase() throws Exception {
        whitelistService = getWhitelistService();
        objectMapper = new ObjectMapper();
        httpClient = HttpClient.newHttpClient();
    }

    /**
     * Creates a test cluster directly in Consul.
     */
    protected void createTestCluster() throws Exception {
        PipelineClusterConfig clusterConfig = new PipelineClusterConfig(
            TEST_CLUSTER,
            new PipelineGraphConfig(Map.of()),
            new PipelineModuleMap(Map.of()),
            null, // defaultPipelineName
            Set.of(), // allowedKafkaTopics
            Set.of() // allowedGrpcServices
        );

        String key = String.format("rokkon-clusters/%s/config", TEST_CLUSTER);
        String url = String.format("http://%s:%s/v1/kv/%s", getConsulHost(), getConsulPort(), key);

        String json = objectMapper.writeValueAsString(clusterConfig);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    /**
     * Registers test modules in Consul.
     */
    protected void registerTestModules() {
        // Register test-processor
        try {
            boolean success = registerModuleInConsul(TEST_MODULE, "localhost", 9090)
                .await().atMost(java.time.Duration.ofSeconds(10));
            assertThat(success).isTrue();
        } catch (Exception e) {
            System.err.println("Failed to register module " + TEST_MODULE + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to register test module: " + TEST_MODULE, e);
        }

        // Register echo-processor
        try {
            boolean success = registerModuleInConsul(TEST_MODULE_2, "localhost", 9091)
                .await().atMost(java.time.Duration.ofSeconds(10));
            assertThat(success).isTrue();
        } catch (Exception e) {
            System.err.println("Failed to register module " + TEST_MODULE_2 + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to register test module: " + TEST_MODULE_2, e);
        }
    }

    @Test
    @Disabled("Test may fail due to Consul connectivity issues")
    void testWhitelistModuleSuccess() throws Exception {
        // Setup: Create test cluster and register module
        createTestCluster();
        registerTestModules();

        // Create whitelist request
        ModuleWhitelistRequest request = new ModuleWhitelistRequest(
            "Test Processor Module",
            TEST_MODULE,
            null,
            Map.of("defaultTimeout", "5000")
        );

        // Whitelist the module
        ModuleWhitelistResponse response = whitelistService.whitelistModule(TEST_CLUSTER, request)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(response.success()).isTrue();
        assertThat(response.message()).contains("successfully whitelisted");
        assertThat(response.errors()).isEmpty();

        // Verify module is in whitelist
        List<PipelineModuleConfiguration> modules = whitelistService.listWhitelistedModules(TEST_CLUSTER)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(modules).hasSize(1);
        assertThat(modules.get(0).implementationId()).isEqualTo(TEST_MODULE);
        assertThat(modules.get(0).implementationName()).isEqualTo("Test Processor Module");
    }

    @Test
    @Disabled("Test may fail due to Consul connectivity issues")
    void testWhitelistModuleNotInConsul() throws Exception {
        // Create test cluster first
        createTestCluster();

        // Try to whitelist a module that doesn't exist in Consul
        ModuleWhitelistRequest request = new ModuleWhitelistRequest(
            "Non-existent Module",
            "non-existent-module"
        );

        ModuleWhitelistResponse response = whitelistService.whitelistModule(TEST_CLUSTER, request)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("not found in Consul");
        assertThat(response.message()).contains("must be registered at least once");
    }

    @Test
    @Disabled("Test may fail due to Consul connectivity issues")
    void testWhitelistModuleTwice() throws Exception {
        // Setup: Create test cluster and register module
        createTestCluster();
        registerTestModules();

        // Whitelist a module
        ModuleWhitelistRequest request = new ModuleWhitelistRequest(
            "Test Module",
            TEST_MODULE
        );

        ModuleWhitelistResponse response = whitelistService.whitelistModule(TEST_CLUSTER, request)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(response.success()).isTrue();

        // Try to whitelist it again
        ModuleWhitelistResponse response2 = whitelistService.whitelistModule(TEST_CLUSTER, request)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(response2.success()).isTrue();
        assertThat(response2.message()).contains("already whitelisted");
    }

    @Test
    @Disabled("Test may fail due to Consul connectivity issues")
    void testRemoveModuleFromWhitelist() throws Exception {
        // Setup: Create test cluster and register module
        createTestCluster();
        registerTestModules();

        // First whitelist a module
        ModuleWhitelistRequest request = new ModuleWhitelistRequest(
            "Module to Remove",
            TEST_MODULE_2
        );

        ModuleWhitelistResponse response = whitelistService.whitelistModule(TEST_CLUSTER, request)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(response.success()).isTrue();

        // Verify it's whitelisted
        List<PipelineModuleConfiguration> modules = whitelistService.listWhitelistedModules(TEST_CLUSTER)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(modules).hasSize(1);

        // Remove it
        ModuleWhitelistResponse removeResponse = whitelistService.removeModuleFromWhitelist(TEST_CLUSTER, TEST_MODULE_2)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(removeResponse.success()).isTrue();
        assertThat(removeResponse.message()).contains("removed from cluster");

        // Verify it's gone
        List<PipelineModuleConfiguration> modulesAfterRemoval = whitelistService.listWhitelistedModules(TEST_CLUSTER)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(modulesAfterRemoval).isEmpty();
    }

    @Test
    @Disabled("Test may fail due to Consul connectivity issues")
    void testRemoveModuleInUse() throws Exception {
        // Setup: Create test cluster and register module
        createTestCluster();
        registerTestModules();

        // Create a pipeline that uses a module
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step",
            StepType.PIPELINE,
            new PipelineStepConfig.ProcessorInfo(TEST_MODULE, null)
        );

        PipelineConfig pipeline = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );

        // First whitelist the module
        ModuleWhitelistRequest request = new ModuleWhitelistRequest(
            "Module in Use",
            TEST_MODULE
        );

        ModuleWhitelistResponse response = whitelistService.whitelistModule(TEST_CLUSTER, request)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(response.success()).isTrue();

        // Update cluster with pipeline that uses the module
        PipelineClusterConfig updatedConfig = loadClusterConfig();
        PipelineGraphConfig newGraphConfig = new PipelineGraphConfig(
            Map.of("test-pipeline", pipeline)
        );

        PipelineClusterConfig configWithPipeline = new PipelineClusterConfig(
            updatedConfig.clusterName(),
            newGraphConfig,
            updatedConfig.pipelineModuleMap(),
            updatedConfig.defaultPipelineName(),
            updatedConfig.allowedKafkaTopics(),
            updatedConfig.allowedGrpcServices()
        );

        saveClusterConfig(configWithPipeline);

        // Try to remove the module that's in use
        ModuleWhitelistResponse removeResponse = whitelistService.removeModuleFromWhitelist(TEST_CLUSTER, TEST_MODULE)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(removeResponse.success()).isFalse();
        assertThat(removeResponse.message()).contains("currently used in pipeline");
    }

    @Test
    @Disabled("Test may fail due to Consul connectivity issues")
    void testWhitelistValidatesAgainstPipelines() throws Exception {
        // Setup: Create test cluster and register module
        createTestCluster();
        registerTestModules();

        // Create a pipeline that uses a non-whitelisted module
        PipelineStepConfig step = new PipelineStepConfig(
            "invalid-step",
            StepType.PIPELINE,
            new PipelineStepConfig.ProcessorInfo("not-whitelisted-module", null)
        );

        PipelineConfig pipeline = new PipelineConfig(
            "invalid-pipeline",
            Map.of("step1", step)
        );

        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
            Map.of("invalid-pipeline", pipeline)
        );

        // Update cluster with invalid pipeline
        PipelineClusterConfig currentConfig = loadClusterConfig();
        PipelineClusterConfig invalidConfig = new PipelineClusterConfig(
            currentConfig.clusterName(),
            graphConfig,
            currentConfig.pipelineModuleMap(),
            currentConfig.defaultPipelineName(),
            currentConfig.allowedKafkaTopics(),
            currentConfig.allowedGrpcServices()
        );

        saveClusterConfig(invalidConfig);

        // Now try to whitelist a different module - should still validate all pipelines
        ModuleWhitelistRequest request = new ModuleWhitelistRequest(
            "Some Other Module",
            TEST_MODULE
        );

        ModuleWhitelistResponse response = whitelistService.whitelistModule(TEST_CLUSTER, request)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(response.success()).isFalse();
        assertThat(response.errors()).anyMatch(error -> 
            error.contains("not-whitelisted-module") && error.contains("not whitelisted")
        );
    }

    @Test
    @Disabled("Test may fail due to Consul connectivity issues")
    void testListWhitelistedModules() throws Exception {
        // Setup: Create test cluster and register modules
        createTestCluster();
        registerTestModules();

        // Whitelist multiple modules
        ModuleWhitelistRequest request1 = new ModuleWhitelistRequest(
            "First Module",
            TEST_MODULE,
            new SchemaReference("schema1", 1),
            Map.of("config1", "value1")
        );

        ModuleWhitelistRequest request2 = new ModuleWhitelistRequest(
            "Second Module", 
            TEST_MODULE_2,
            null,
            null
        );

        ModuleWhitelistResponse response1 = whitelistService.whitelistModule(TEST_CLUSTER, request1)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(response1.success()).isTrue();

        ModuleWhitelistResponse response2 = whitelistService.whitelistModule(TEST_CLUSTER, request2)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(response2.success()).isTrue();

        // List modules
        List<PipelineModuleConfiguration> modules = whitelistService.listWhitelistedModules(TEST_CLUSTER)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(modules).hasSize(2);

        // Find first module
        PipelineModuleConfiguration firstModule = modules.stream()
            .filter(m -> m.implementationId().equals(TEST_MODULE))
            .findFirst()
            .orElseThrow();

        assertThat(firstModule.implementationName()).isEqualTo("First Module");
        assertThat(firstModule.customConfigSchemaReference()).isNotNull();
        assertThat(firstModule.customConfigSchemaReference().subject()).isEqualTo("schema1");
        assertThat(firstModule.customConfig()).containsEntry("config1", "value1");

        // Find second module
        PipelineModuleConfiguration secondModule = modules.stream()
            .filter(m -> m.implementationId().equals(TEST_MODULE_2))
            .findFirst()
            .orElseThrow();

        assertThat(secondModule.implementationName()).isEqualTo("Second Module");
        assertThat(secondModule.customConfigSchemaReference()).isNull();
        assertThat(secondModule.customConfig()).isEmpty();
    }

    /**
     * Helper method to load cluster config from Consul.
     */
    protected PipelineClusterConfig loadClusterConfig() throws Exception {
        String key = String.format("rokkon-clusters/%s/config", TEST_CLUSTER);
        String url = String.format("http://%s:%s/v1/kv/%s?raw", getConsulHost(), getConsulPort(), key);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        return objectMapper.readValue(response.body(), PipelineClusterConfig.class);
    }

    /**
     * Helper method to save cluster config to Consul.
     */
    protected void saveClusterConfig(PipelineClusterConfig config) throws Exception {
        String key = String.format("rokkon-clusters/%s/config", TEST_CLUSTER);
        String url = String.format("http://%s:%s/v1/kv/%s", getConsulHost(), getConsulPort(), key);

        String json = objectMapper.writeValueAsString(config);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }
}
