package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.config.model.StepType;
import com.rokkon.pipeline.engine.grpc.DynamicGrpcClientFactory;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ServiceMetadata;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test for GrpcTransportHandler without any CDI or Quarkus context.
 * This avoids all classloading issues by testing the handler in isolation.
 */
class GrpcTransportHandlerPureUnitTest {
    
    private GrpcTransportHandler transportHandler;
    private DynamicGrpcClientFactory mockFactory;
    
    @BeforeEach
    void setup() {
        // Create handler manually
        transportHandler = new GrpcTransportHandler();
        
        // Create and inject mock factory
        mockFactory = Mockito.mock(DynamicGrpcClientFactory.class);
        transportHandler.grpcClientFactory = mockFactory;
    }
    
    @Test
    void testCanHandleWithGrpcService() {
        // Create step config with gRPC service
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service",
            null
        );
        
        PipelineStepConfig stepConfig = new PipelineStepConfig(
            "test-step",
            StepType.PIPELINE,
            "Test step",
            null, null, null, null, null, null, null, null, null,
            processorInfo
        );
        
        boolean result = transportHandler.canHandle(stepConfig);
        assertThat(result).isTrue();
    }
    
    @Test
    void testCanHandleWithInternalProcessor() {
        // Create step with internal processor
        PipelineStepConfig.ProcessorInfo internalProcessor = new PipelineStepConfig.ProcessorInfo(
            null,
            "internalBean"
        );
        
        PipelineStepConfig internalStep = new PipelineStepConfig(
            "internal-step",
            StepType.PIPELINE,
            "Internal step",
            null, null, null, null, null, null, null, null, null,
            internalProcessor
        );
        
        boolean result = transportHandler.canHandle(internalStep);
        assertThat(result).isFalse();
    }
    
    @Test
    void testProcessorInfoCannotHaveBothTypes() {
        // ProcessorInfo validates that you can't have both gRPC and internal processor
        assertThrows(
            IllegalArgumentException.class,
            () -> new PipelineStepConfig.ProcessorInfo("grpc-service", "internalBean"),
            "ProcessorInfo cannot have both grpcServiceName and internalProcessorBeanName set."
        );
    }
    
    @Test
    void testProcessorInfoValidation() {
        // ProcessorInfo constructor validates that at least one field is non-blank
        assertThrows(
            IllegalArgumentException.class,
            () -> new PipelineStepConfig.ProcessorInfo("", null),
            "ProcessorInfo must have either grpcServiceName or internalProcessorBeanName set."
        );
        
        assertThrows(
            IllegalArgumentException.class,
            () -> new PipelineStepConfig.ProcessorInfo(null, null),
            "ProcessorInfo must have either grpcServiceName or internalProcessorBeanName set."
        );
        
        assertThrows(
            IllegalArgumentException.class,
            () -> new PipelineStepConfig.ProcessorInfo("", ""),
            "ProcessorInfo must have either grpcServiceName or internalProcessorBeanName set."
        );
    }
    
    @Test
    void testRouteRequestWithInvalidStepConfig() {
        // Create step with internal processor (can't be handled by gRPC transport)
        PipelineStepConfig.ProcessorInfo internalProcessor = new PipelineStepConfig.ProcessorInfo(
            null,
            "internalBean"
        );
        
        PipelineStepConfig internalStep = new PipelineStepConfig(
            "internal-step",
            StepType.PIPELINE,
            "Internal step",
            null, null, null, null, null, null, null, null, null,
            internalProcessor
        );
        
        // Create a test request
        ProcessRequest request = ProcessRequest.newBuilder()
            .setDocument(PipeDoc.newBuilder().setId("test").build())
            .setMetadata(ServiceMetadata.newBuilder().setPipelineName("test").build())
            .setConfig(ProcessConfiguration.newBuilder().build())
            .build();
        
        // When routing with invalid config
        Uni<ProcessResponse> result = transportHandler.routeRequest(request, internalStep);
        
        // Then it should fail
        assertThrows(
            IllegalArgumentException.class,
            () -> result.await().indefinitely(),
            "Step does not have gRPC configuration"
        );
        
        // Verify no factory calls were made
        verify(mockFactory, never()).getMutinyClientForService(anyString());
    }
    
    @Test
    void testRouteRequestCallsFactory() {
        // Create step config with gRPC service
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
        
        // Create a test request
        ProcessRequest request = ProcessRequest.newBuilder()
            .setDocument(PipeDoc.newBuilder().setId("test-123").setBody("Hello").build())
            .setMetadata(ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("echo-step")
                .setStreamId("stream-123")
                .build())
            .setConfig(ProcessConfiguration.newBuilder().build())
            .build();
        
        // Setup mock to return a failure (simulating service not found)
        when(mockFactory.getMutinyClientForService("echo-service"))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Service not found: echo-service")));
        
        // When routing the request
        Uni<ProcessResponse> result = transportHandler.routeRequest(request, stepConfig);
        
        // Then it should fail with the expected error
        RuntimeException error = assertThrows(
            RuntimeException.class,
            () -> result.await().indefinitely()
        );
        assertThat(error.getMessage()).contains("Service not found: echo-service");
        
        // Verify the factory was called with correct service name
        verify(mockFactory).getMutinyClientForService("echo-service");
    }
}