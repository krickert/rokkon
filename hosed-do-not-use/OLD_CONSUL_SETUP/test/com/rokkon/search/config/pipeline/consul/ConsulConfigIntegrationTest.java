package com.rokkon.search.config.pipeline.consul;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.rokkon.search.config.pipeline.model.*;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(ConsulResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConsulConfigIntegrationTest {

    @Inject
    ObjectMapper objectMapper;
    
    @ConfigProperty(name = "consul.host")
    String consulHost;
    
    @ConfigProperty(name = "consul.port")
    String consulPort;

    @Test
    @Order(1)
    public void testStoreAndRetrieveCompleteClusterConfig() throws Exception {
        String consulUrl = "http://" + consulHost + ":" + consulPort;
        
        // Build a complete cluster configuration
        PipelineClusterConfig clusterConfig = createCompleteClusterConfig();
        
        // Serialize to JSON
        String configJson = objectMapper.writeValueAsString(clusterConfig);
        System.out.println("Storing config in Consul: " + configJson);
        
        // Store in Consul using HTTP API
        String consulKey = "rokkon-clusters/test-cluster/config";
        storeInConsul(consulUrl, consulKey, configJson);
        
        // Retrieve from Consul
        String retrievedJson = retrieveFromConsul(consulUrl, consulKey);
        assertNotNull(retrievedJson);
        System.out.println("Retrieved from Consul: " + retrievedJson);
        
        // Deserialize and verify
        PipelineClusterConfig retrievedConfig = objectMapper.readValue(retrievedJson, PipelineClusterConfig.class);
        
        // Verify cluster config
        assertEquals(clusterConfig.clusterName(), retrievedConfig.clusterName());
        assertEquals(clusterConfig.defaultPipelineName(), retrievedConfig.defaultPipelineName());
        assertEquals(clusterConfig.allowedKafkaTopics().size(), retrievedConfig.allowedKafkaTopics().size());
        assertEquals(clusterConfig.allowedGrpcServices().size(), retrievedConfig.allowedGrpcServices().size());
        
        // Verify module map
        assertEquals(clusterConfig.pipelineModuleMap().availableModules().size(), 
                    retrievedConfig.pipelineModuleMap().availableModules().size());
                    
        // Verify pipeline graph
        assertEquals(clusterConfig.pipelineGraphConfig().pipelines().size(),
                    retrievedConfig.pipelineGraphConfig().pipelines().size());
                    
        PipelineConfig originalPipeline = clusterConfig.pipelineGraphConfig().pipelines().get("document-processing");
        PipelineConfig retrievedPipeline = retrievedConfig.pipelineGraphConfig().pipelines().get("document-processing");
        
        assertNotNull(originalPipeline);
        assertNotNull(retrievedPipeline);
        assertEquals(originalPipeline.name(), retrievedPipeline.name());
        assertEquals(originalPipeline.pipelineSteps().size(), retrievedPipeline.pipelineSteps().size());
        
        System.out.println("✅ Complete cluster config successfully stored and retrieved from Consul!");
    }

    @Test
    @Order(2)
    public void testStoreIndividualComponents() throws Exception {
        String consulUrl = "http://" + consulHost + ":" + consulPort;
        
        // Test individual module configuration
        SchemaReference schema = new SchemaReference("chunker-schema", 1);
        PipelineModuleConfiguration module = new PipelineModuleConfiguration(
            "Test Chunker", "chunker-test", schema, Map.of("param", "value"));
            
        String moduleJson = objectMapper.writeValueAsString(module);
        storeInConsul(consulUrl, "rokkon-clusters/test/modules/chunker-test", moduleJson);
        
        String retrievedModuleJson = retrieveFromConsul(consulUrl, "rokkon-clusters/test/modules/chunker-test");
        PipelineModuleConfiguration retrievedModule = objectMapper.readValue(retrievedModuleJson, PipelineModuleConfiguration.class);
        
        assertEquals(module.implementationName(), retrievedModule.implementationName());
        assertEquals(module.implementationId(), retrievedModule.implementationId());
        assertEquals(module.customConfigSchemaReference().subject(), 
                    retrievedModule.customConfigSchemaReference().subject());
        
        // Test pipeline step configuration
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo("chunker-test", null);
        PipelineStepConfig step = new PipelineStepConfig("test-step", StepType.PIPELINE, processorInfo);
        
        String stepJson = objectMapper.writeValueAsString(step);
        storeInConsul(consulUrl, "rokkon-clusters/test/steps/test-step", stepJson);
        
        String retrievedStepJson = retrieveFromConsul(consulUrl, "rokkon-clusters/test/steps/test-step");
        PipelineStepConfig retrievedStep = objectMapper.readValue(retrievedStepJson, PipelineStepConfig.class);
        
        assertEquals(step.stepName(), retrievedStep.stepName());
        assertEquals(step.stepType(), retrievedStep.stepType());
        assertEquals(step.processorInfo().grpcServiceName(), retrievedStep.processorInfo().grpcServiceName());
        
        System.out.println("✅ Individual components successfully stored and retrieved from Consul!");
    }

    @Test
    @Order(3)
    public void testConfigUpdateSimulation() throws Exception {
        String consulUrl = "http://" + consulHost + ":" + consulPort;
        
        // Store initial configuration
        PipelineClusterConfig initialConfig = createSimpleClusterConfig();
        String configKey = "rokkon-clusters/update-test/config";
        
        storeInConsul(consulUrl, configKey, objectMapper.writeValueAsString(initialConfig));
        
        // Retrieve and verify initial
        String initialJson = retrieveFromConsul(consulUrl, configKey);
        PipelineClusterConfig retrievedInitial = objectMapper.readValue(initialJson, PipelineClusterConfig.class);
        assertEquals("update-test-cluster", retrievedInitial.clusterName());
        assertEquals(1, retrievedInitial.allowedKafkaTopics().size());
        
        // Update configuration - add more topics and services
        PipelineClusterConfig updatedConfig = new PipelineClusterConfig(
            "update-test-cluster",
            initialConfig.pipelineGraphConfig(),
            initialConfig.pipelineModuleMap(),
            initialConfig.defaultPipelineName(),
            Set.of("topic1", "topic2", "topic3"), // Added more topics
            Set.of("service1", "service2") // Added more services
        );
        
        // Store updated configuration
        storeInConsul(consulUrl, configKey, objectMapper.writeValueAsString(updatedConfig));
        
        // Retrieve and verify update
        String updatedJson = retrieveFromConsul(consulUrl, configKey);
        PipelineClusterConfig retrievedUpdated = objectMapper.readValue(updatedJson, PipelineClusterConfig.class);
        
        assertEquals("update-test-cluster", retrievedUpdated.clusterName());
        assertEquals(3, retrievedUpdated.allowedKafkaTopics().size());
        assertEquals(2, retrievedUpdated.allowedGrpcServices().size());
        assertTrue(retrievedUpdated.allowedKafkaTopics().contains("topic3"));
        assertTrue(retrievedUpdated.allowedGrpcServices().contains("service2"));
        
        System.out.println("✅ Configuration update simulation successful!");
    }

    private PipelineClusterConfig createCompleteClusterConfig() {
        // Create modules
        SchemaReference chunkerSchema = new SchemaReference("chunker-schema", 1);
        SchemaReference embedderSchema = new SchemaReference("embedder-schema", 2);
        
        PipelineModuleConfiguration chunkerModule = new PipelineModuleConfiguration(
            "Text Chunker Service", "chunker-service", chunkerSchema, 
            Map.of("defaultChunkSize", 1000, "type", "sentence"));
            
        PipelineModuleConfiguration embedderModule = new PipelineModuleConfiguration(
            "OpenAI Embedder", "embedder-openai", embedderSchema,
            Map.of("model", "text-embedding-ada-002", "dimensions", 1536));
            
        PipelineModuleMap moduleMap = new PipelineModuleMap(Map.of(
            "chunker-service", chunkerModule,
            "embedder-openai", embedderModule
        ));

        // Create pipeline steps
        JsonNode chunkerJsonConfig = JsonNodeFactory.instance.objectNode()
            .put("chunkSize", 500)
            .put("overlap", 50)
            .put("splitOnSentences", true);
            
        PipelineStepConfig.JsonConfigOptions chunkerConfig = new PipelineStepConfig.JsonConfigOptions(
            chunkerJsonConfig, Map.of("timeout", "30s", "retries", "3"));
            
        PipelineStepConfig.ProcessorInfo chunkerProcessor = new PipelineStepConfig.ProcessorInfo(
            "chunker-service", null);
            
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "embedder-openai", Map.of("timeout", "60s", "maxRetries", "3"));
            
        PipelineStepConfig.OutputTarget chunkerOutput = new PipelineStepConfig.OutputTarget(
            "embedder-step", TransportType.GRPC, grpcTransport, null);

        PipelineStepConfig chunkerStep = new PipelineStepConfig(
            "chunker-step", StepType.PIPELINE, "Document chunking step",
            "chunker-schema", chunkerConfig, List.of(),
            Map.of("primary", chunkerOutput), 3, 1000L, 30000L, 2.0, 25000L,
            chunkerProcessor);

        // Embedder step (sink)
        PipelineStepConfig.ProcessorInfo embedderProcessor = new PipelineStepConfig.ProcessorInfo(
            "embedder-openai", null);
            
        PipelineStepConfig embedderStep = new PipelineStepConfig(
            "embedder-step", StepType.SINK, "Embedding generation step",
            "embedder-schema", null, List.of(), Map.of(), 2, 2000L, 45000L, 1.5, 60000L,
            embedderProcessor);

        // Create pipeline
        PipelineConfig pipeline = new PipelineConfig("document-processing", Map.of(
            "chunker-step", chunkerStep,
            "embedder-step", embedderStep
        ));

        // Create pipeline graph
        PipelineGraphConfig pipelineGraph = new PipelineGraphConfig(Map.of(
            "document-processing", pipeline
        ));

        // Create cluster config
        return new PipelineClusterConfig(
            "test-cluster",
            pipelineGraph,
            moduleMap,
            "document-processing",
            Set.of("input-documents", "processed-chunks", "embeddings-output", "error-dlq"),
            Set.of("chunker-service", "embedder-openai", "opensearch-sink")
        );
    }

    private PipelineClusterConfig createSimpleClusterConfig() {
        return new PipelineClusterConfig(
            "update-test-cluster",
            null,
            null,
            null,
            Set.of("topic1"),
            Set.of("service1")
        );
    }

    private void storeInConsul(String consulUrl, String key, String value) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(consulUrl + "/v1/kv/" + key))
                .PUT(HttpRequest.BodyPublishers.ofString(value))
                .build();
                
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }

    private String retrieveFromConsul(String consulUrl, String key) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(consulUrl + "/v1/kv/" + key + "?raw"))
                .GET()
                .build();
                
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return response.body();
    }
}