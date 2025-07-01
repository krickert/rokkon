package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.config.model.StepType;
import com.rokkon.pipeline.engine.grpc.ServiceDiscovery;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ServiceMetadata;
import io.smallrye.mutiny.Uni;
import io.smallrye.stork.api.ServiceInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test for ModuleRouterImpl without any CDI or Quarkus context.
 * Tests the routing logic in isolation using mocked dependencies.
 */
class ModuleRouterImplUnitTest {
    
    private ModuleRouterImpl moduleRouter;
    private ServiceDiscovery mockServiceDiscovery;
    private GrpcClientFactory mockGrpcClientFactory;
    private PipeStepProcessor mockClient;
    
    @BeforeEach
    void setup() {
        // Create router manually
        moduleRouter = new ModuleRouterImpl();
        
        // Create and inject mock dependencies
        mockServiceDiscovery = Mockito.mock(ServiceDiscovery.class);
        mockGrpcClientFactory = Mockito.mock(GrpcClientFactory.class);
        mockClient = Mockito.mock(PipeStepProcessor.class);
        
        moduleRouter.serviceDiscovery = mockServiceDiscovery;
        moduleRouter.grpcClientFactory = mockGrpcClientFactory;
    }
    
    @Test
    void testRouteToModuleWithNullProcessorInfo() {
        // PipelineStepConfig requires non-null processorInfo, so we'll test at runtime
        // by creating a mock step that returns null processorInfo
        PipelineStepConfig stepConfig = Mockito.mock(PipelineStepConfig.class);
        when(stepConfig.stepName()).thenReturn("test-step");
        when(stepConfig.processorInfo()).thenReturn(null);
        
        ProcessRequest request = createTestRequest();
        
        // When routing
        Uni<ProcessResponse> result = moduleRouter.routeToModule(request, stepConfig);
        
        // Then it should fail
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> result.await().indefinitely()
        );
        assertThat(error.getMessage()).contains("No gRPC service configured for step: test-step");
        
        // Verify no service discovery or client calls
        verify(mockServiceDiscovery, never()).discoverService(anyString());
        verify(mockGrpcClientFactory, never()).getClient(anyString(), anyInt());
    }
    
    @Test
    void testRouteToModuleWithInternalProcessor() {
        // Create step with internal processor (no gRPC service)
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            null,
            "internalBean"
        );
        
        PipelineStepConfig stepConfig = new PipelineStepConfig(
            "internal-step",
            StepType.PIPELINE,
            "Internal step",
            null, null, null, null, null, null, null, null, null,
            processorInfo
        );
        
        ProcessRequest request = createTestRequest();
        
        // When routing
        Uni<ProcessResponse> result = moduleRouter.routeToModule(request, stepConfig);
        
        // Then it should fail
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> result.await().indefinitely()
        );
        assertThat(error.getMessage()).contains("No gRPC service configured for step: internal-step");
        
        // Verify no service discovery or client calls
        verify(mockServiceDiscovery, never()).discoverService(anyString());
        verify(mockGrpcClientFactory, never()).getClient(anyString(), anyInt());
    }
    
    @Test
    void testSuccessfulRouting() {
        // Create step with gRPC service
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "echo-service",
            null
        );
        
        PipelineStepConfig stepConfig = new PipelineStepConfig(
            "echo-step",
            StepType.PIPELINE,
            "Echo step",
            null, null, null, null, null, null, null, null, null,
            processorInfo
        );
        
        // Setup mock service instance
        ServiceInstance mockInstance = Mockito.mock(ServiceInstance.class);
        when(mockInstance.getHost()).thenReturn("localhost");
        when(mockInstance.getPort()).thenReturn(49092);
        
        // Setup service discovery to return the instance
        when(mockServiceDiscovery.discoverService("echo-service"))
            .thenReturn(Uni.createFrom().item(mockInstance));
        
        // Setup client factory to return mock client
        when(mockGrpcClientFactory.getClient("localhost", 49092))
            .thenReturn(mockClient);
        
        // Setup mock client response
        ProcessResponse mockResponse = ProcessResponse.newBuilder()
            .setSuccess(true)
            .setOutputDoc(PipeDoc.newBuilder().setId("test").setBody("Echo: Hello").build())
            .build();
        when(mockClient.processData(any(ProcessRequest.class)))
            .thenReturn(Uni.createFrom().item(mockResponse));
        
        ProcessRequest request = createTestRequest();
        
        // When routing
        ProcessResponse response = moduleRouter.routeToModule(request, stepConfig)
            .await().indefinitely();
        
        // Then verify the response
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getOutputDoc().getBody()).isEqualTo("Echo: Hello");
        
        // Verify all interactions
        verify(mockServiceDiscovery).discoverService("echo-service");
        verify(mockGrpcClientFactory).getClient("localhost", 49092);
        verify(mockClient).processData(request);
    }
    
    @Test
    void testServiceDiscoveryFailure() {
        // Create step with gRPC service
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "missing-service",
            null
        );
        
        PipelineStepConfig stepConfig = new PipelineStepConfig(
            "missing-step",
            StepType.PIPELINE,
            "Missing step",
            null, null, null, null, null, null, null, null, null,
            processorInfo
        );
        
        // Setup service discovery to fail
        when(mockServiceDiscovery.discoverService("missing-service"))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Service not found: missing-service")));
        
        ProcessRequest request = createTestRequest();
        
        // When routing
        Uni<ProcessResponse> result = moduleRouter.routeToModule(request, stepConfig);
        
        // Then it should fail
        RuntimeException error = assertThrows(
            RuntimeException.class,
            () -> result.await().indefinitely()
        );
        assertThat(error.getMessage()).contains("Service not found: missing-service");
        
        // Verify service discovery was called but no client factory
        verify(mockServiceDiscovery).discoverService("missing-service");
        verify(mockGrpcClientFactory, never()).getClient(anyString(), anyInt());
    }
    
    @Test
    void testGrpcCallFailure() {
        // Create step with gRPC service
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "failing-service",
            null
        );
        
        PipelineStepConfig stepConfig = new PipelineStepConfig(
            "failing-step",
            StepType.PIPELINE,
            "Failing step",
            null, null, null, null, null, null, null, null, null,
            processorInfo
        );
        
        // Setup mock service instance
        ServiceInstance mockInstance = Mockito.mock(ServiceInstance.class);
        when(mockInstance.getHost()).thenReturn("localhost");
        when(mockInstance.getPort()).thenReturn(49093);
        
        // Setup service discovery to return the instance
        when(mockServiceDiscovery.discoverService("failing-service"))
            .thenReturn(Uni.createFrom().item(mockInstance));
        
        // Setup client factory to return mock client
        when(mockGrpcClientFactory.getClient("localhost", 49093))
            .thenReturn(mockClient);
        
        // Setup mock client to fail
        when(mockClient.processData(any(ProcessRequest.class)))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("gRPC call failed")));
        
        ProcessRequest request = createTestRequest();
        
        // When routing
        Uni<ProcessResponse> result = moduleRouter.routeToModule(request, stepConfig);
        
        // Then it should fail
        RuntimeException error = assertThrows(
            RuntimeException.class,
            () -> result.await().indefinitely()
        );
        assertThat(error.getMessage()).contains("gRPC call failed");
        
        // Verify all interactions occurred
        verify(mockServiceDiscovery).discoverService("failing-service");
        verify(mockGrpcClientFactory).getClient("localhost", 49093);
        verify(mockClient).processData(request);
    }
    
    @Test
    void testMultipleRoutingCalls() {
        // Create step with gRPC service
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "echo-service",
            null
        );
        
        PipelineStepConfig stepConfig = new PipelineStepConfig(
            "echo-step",
            StepType.PIPELINE,
            "Echo step",
            null, null, null, null, null, null, null, null, null,
            processorInfo
        );
        
        // Setup mock service instances (simulating load balancing)
        ServiceInstance mockInstance1 = Mockito.mock(ServiceInstance.class);
        when(mockInstance1.getHost()).thenReturn("host1");
        when(mockInstance1.getPort()).thenReturn(49092);
        
        ServiceInstance mockInstance2 = Mockito.mock(ServiceInstance.class);
        when(mockInstance2.getHost()).thenReturn("host2");
        when(mockInstance2.getPort()).thenReturn(49092);
        
        // Setup service discovery to return different instances
        when(mockServiceDiscovery.discoverService("echo-service"))
            .thenReturn(Uni.createFrom().item(mockInstance1))
            .thenReturn(Uni.createFrom().item(mockInstance2));
        
        // Setup client factory for both hosts
        PipeStepProcessor mockClient1 = Mockito.mock(PipeStepProcessor.class);
        PipeStepProcessor mockClient2 = Mockito.mock(PipeStepProcessor.class);
        
        when(mockGrpcClientFactory.getClient("host1", 49092)).thenReturn(mockClient1);
        when(mockGrpcClientFactory.getClient("host2", 49092)).thenReturn(mockClient2);
        
        // Setup mock responses
        ProcessResponse response1 = ProcessResponse.newBuilder()
            .setSuccess(true)
            .setOutputDoc(PipeDoc.newBuilder().setId("test").setBody("Response from host1").build())
            .build();
        ProcessResponse response2 = ProcessResponse.newBuilder()
            .setSuccess(true)
            .setOutputDoc(PipeDoc.newBuilder().setId("test").setBody("Response from host2").build())
            .build();
        
        when(mockClient1.processData(any())).thenReturn(Uni.createFrom().item(response1));
        when(mockClient2.processData(any())).thenReturn(Uni.createFrom().item(response2));
        
        ProcessRequest request = createTestRequest();
        
        // First routing call
        ProcessResponse firstResponse = moduleRouter.routeToModule(request, stepConfig)
            .await().indefinitely();
        assertThat(firstResponse.getOutputDoc().getBody()).isEqualTo("Response from host1");
        
        // Second routing call
        ProcessResponse secondResponse = moduleRouter.routeToModule(request, stepConfig)
            .await().indefinitely();
        assertThat(secondResponse.getOutputDoc().getBody()).isEqualTo("Response from host2");
        
        // Verify service discovery was called twice
        verify(mockServiceDiscovery, Mockito.times(2)).discoverService("echo-service");
        
        // Verify both clients were used
        verify(mockGrpcClientFactory).getClient("host1", 49092);
        verify(mockGrpcClientFactory).getClient("host2", 49092);
        verify(mockClient1).processData(request);
        verify(mockClient2).processData(request);
    }
    
    private ProcessRequest createTestRequest() {
        return ProcessRequest.newBuilder()
            .setDocument(PipeDoc.newBuilder().setId("test-123").setBody("Hello").build())
            .setMetadata(ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("test-step")
                .setStreamId("stream-123")
                .build())
            .setConfig(ProcessConfiguration.newBuilder().build())
            .build();
    }
}