package com.rokkon.proxy;

import com.rokkon.search.sdk.MutinyPipeStepProcessorGrpc;
import com.rokkon.search.sdk.PipeStepProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for PipeStepProcessorProxy using Mockito.
 * 
 * This test class demonstrates how to test the PipeStepProcessorProxy with mocked dependencies.
 * It uses Mockito to create mock objects for the ModuleClientFactory and the backend gRPC client.
 * 
 * The tests verify that:
 * 1. The proxy correctly forwards requests to the backend client
 * 2. The proxy handles successful responses from the backend
 * 3. The proxy handles error responses and exceptions from the backend
 * 4. The proxy adds its own metadata to service registration responses
 * 
 * This approach allows testing the proxy in isolation without requiring a real backend service.
 */
@ExtendWith(MockitoExtension.class)
class PipeStepProcessorProxyTest extends PipeStepProcessorProxyTestBase {

    private PipeStepProcessorProxy pipeStepProcessor;

    @Mock
    private ModuleClientFactory moduleClientFactory;

    private MeterRegistry meterRegistry;
    private MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub mockBackendClient;

    @BeforeEach
    void setupMocks() {
        // Create a simple meter registry
        meterRegistry = new SimpleMeterRegistry();

        // Create a mock backend client
        mockBackendClient = Mockito.mock(MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub.class);

        // Configure the mock factory to return our mock client
        Mockito.when(moduleClientFactory.createClient()).thenReturn(mockBackendClient);

        // Create the proxy processor with mocked dependencies
        pipeStepProcessor = new PipeStepProcessorProxy(moduleClientFactory, meterRegistry);
    }

    @Override
    protected PipeStepProcessor getProxyProcessor() {
        return pipeStepProcessor;
    }

    @Override
    protected MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub getMockedBackendClient() {
        return mockBackendClient;
    }
}
