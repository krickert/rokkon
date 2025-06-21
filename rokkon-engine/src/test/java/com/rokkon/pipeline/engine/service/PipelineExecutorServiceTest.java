package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.consul.service.PipelineConfigService;
import com.rokkon.search.engine.ProcessResponse;
import com.rokkon.search.engine.ProcessStatus;
import com.rokkon.search.model.ActionType;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ProcessRequest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class PipelineExecutorServiceTest {

    @Inject
    PipelineExecutorService pipelineExecutorService;

    @InjectMock
    PipelineConfigService pipelineConfigService;

    @InjectMock
    GrpcModuleRouter grpcRouter;

    @InjectMock
    KafkaModuleRouter kafkaRouter;

    private PipelineConfig testPipelineConfig;
    private PipeStepProcessor mockModuleClient;

    @BeforeEach
    void setUp() {
        // Create test pipeline configuration
        PipelineStepConfig initialStep = new PipelineStepConfig(
                "initial-step",
                StepType.INITIAL_PIPELINE,
                null, // no module for initial step
                null, // no custom config
                null, // no config params
                Map.of("output1", new OutputDefinition(
                        TransportType.GRPC,
                        new GrpcTransportDefinition("test-step"),
                        null
                )),
                null, // no kafka inputs
                null  // no error handling
        );

        PipelineStepConfig testStep = new PipelineStepConfig(
                "test-step",
                StepType.PIPELINE,
                "test-module",
                null, // no custom config
                Map.of("param1", "value1"),
                null, // no outputs - end of pipeline
                null, // no kafka inputs
                null  // no error handling
        );

        testPipelineConfig = new PipelineConfig(
                "test-pipeline",
                "Test Pipeline",
                Map.of("initial-step", initialStep, "test-step", testStep),
                null, // no labels
                null  // no description
        );

        // Create mock module client
        mockModuleClient = Mockito.mock(PipeStepProcessor.class);
    }

    @Test
    void testExecutePipeline_Success() {
        // Given
        String pipelineName = "test-pipeline";
        PipeDoc document = PipeDoc.newBuilder()
                .setId("doc-123")
                .setTitle("Test Document")
                .setBody("Test content")
                .build();

        when(pipelineConfigService.getPipelineConfig(pipelineName))
                .thenReturn(Uni.createFrom().item(testPipelineConfig));

        ServiceDiscoveryService.ServiceInstance serviceInstance = 
                new ServiceDiscoveryService.ServiceInstance("test-module", "test-module-1", 
                        "localhost", 9090, null);
        when(serviceDiscoveryService.discoverHealthyService("test-module"))
                .thenReturn(Uni.createFrom().item(serviceInstance));

        when(moduleClientFactory.getModuleClient(any()))
                .thenReturn(Uni.createFrom().item(mockModuleClient));

        com.rokkon.search.sdk.ProcessResponse moduleResponse = 
                com.rokkon.search.sdk.ProcessResponse.newBuilder()
                        .setSuccess(true)
                        .setOutputDoc(document)
                        .addProcessorLogs("Module processed successfully")
                        .build();
        when(mockModuleClient.processData(any(ProcessRequest.class)))
                .thenReturn(Uni.createFrom().item(moduleResponse));

        // When
        ProcessResponse response = pipelineExecutorService.executePipeline(
                pipelineName, document, ActionType.CREATE)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ProcessStatus.ACCEPTED);
        assertThat(response.getMessage()).contains("completed successfully");
    }

    @Test
    void testExecutePipeline_PipelineNotFound() {
        // Given
        String pipelineName = "non-existent-pipeline";
        PipeDoc document = PipeDoc.newBuilder()
                .setId("doc-123")
                .build();

        when(pipelineConfigService.getPipelineConfig(pipelineName))
                .thenReturn(Uni.createFrom().nullItem());

        // When & Then
        pipelineExecutorService.executePipeline(pipelineName, document, ActionType.CREATE)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .assertFailedWith(IllegalArgumentException.class, "Pipeline not found");
    }

    @Test
    void testExecutePipeline_NoHealthyModuleInstances() {
        // Given
        String pipelineName = "test-pipeline";
        PipeDoc document = PipeDoc.newBuilder()
                .setId("doc-123")
                .build();

        when(pipelineConfigService.getPipelineConfig(pipelineName))
                .thenReturn(Uni.createFrom().item(testPipelineConfig));

        when(serviceDiscoveryService.discoverHealthyService("test-module"))
                .thenReturn(Uni.createFrom().nullItem());

        // When
        ProcessResponse response = pipelineExecutorService.executePipeline(
                pipelineName, document, ActionType.CREATE)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ProcessStatus.ERROR);
        assertThat(response.getMessage()).contains("No healthy instances found");
    }

    @Test
    void testExecutePipeline_ModuleProcessingFailure() {
        // Given
        String pipelineName = "test-pipeline";
        PipeDoc document = PipeDoc.newBuilder()
                .setId("doc-123")
                .build();

        when(pipelineConfigService.getPipelineConfig(pipelineName))
                .thenReturn(Uni.createFrom().item(testPipelineConfig));

        ServiceDiscoveryService.ServiceInstance serviceInstance = 
                new ServiceDiscoveryService.ServiceInstance("test-module", "test-module-1", 
                        "localhost", 9090, null);
        when(serviceDiscoveryService.discoverHealthyService("test-module"))
                .thenReturn(Uni.createFrom().item(serviceInstance));

        when(moduleClientFactory.getModuleClient(any()))
                .thenReturn(Uni.createFrom().item(mockModuleClient));

        com.rokkon.search.sdk.ProcessResponse moduleResponse = 
                com.rokkon.search.sdk.ProcessResponse.newBuilder()
                        .setSuccess(false)
                        .addProcessorLogs("Module processing failed")
                        .build();
        when(mockModuleClient.processData(any(ProcessRequest.class)))
                .thenReturn(Uni.createFrom().item(moduleResponse));

        // When
        ProcessResponse response = pipelineExecutorService.executePipeline(
                pipelineName, document, ActionType.CREATE)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ProcessStatus.ACCEPTED);
        // Even though module failed, pipeline completes (no error handling configured)
    }
}