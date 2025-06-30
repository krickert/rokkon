package com.rokkon.protobuf.stubs;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify that both blocking and Mutiny stubs are generated and available.
 */
public class StubsGenerationTest {
    
    @Test
    public void testBlockingStubsExist() {
        // Test that blocking stubs are generated
        assertThat(com.rokkon.search.sdk.PipeStepProcessorGrpc.class).isNotNull();
        assertThat(com.rokkon.search.sdk.PipeStepProcessorGrpc.PipeStepProcessorBlockingStub.class).isNotNull();
        
        // Test module registration blocking stub
        assertThat(com.rokkon.search.registration.api.ModuleRegistrationServiceGrpc.class).isNotNull();
        assertThat(com.rokkon.search.registration.api.ModuleRegistrationServiceGrpc.ModuleRegistrationServiceBlockingStub.class).isNotNull();
        
        // Test engine service blocking stubs
        assertThat(com.rokkon.search.engine.PipeStreamEngineGrpc.class).isNotNull();
        assertThat(com.rokkon.search.engine.ConnectorEngineGrpc.class).isNotNull();
    }
    
    @Test
    public void testMutinyStubsExist() {
        // Test that Mutiny stubs are generated
        assertThat(com.rokkon.search.sdk.MutinyPipeStepProcessorGrpc.class).isNotNull();
        assertThat(com.rokkon.search.sdk.MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub.class).isNotNull();
        
        // Test module registration Mutiny stub
        assertThat(com.rokkon.search.registration.api.MutinyModuleRegistrationServiceGrpc.class).isNotNull();
        assertThat(com.rokkon.search.registration.api.MutinyModuleRegistrationServiceGrpc.MutinyModuleRegistrationServiceStub.class).isNotNull();
        
        // Test engine service Mutiny stubs
        assertThat(com.rokkon.search.engine.MutinyPipeStreamEngineGrpc.class).isNotNull();
        assertThat(com.rokkon.search.engine.MutinyConnectorEngineGrpc.class).isNotNull();
    }
    
    @Test
    public void testProtobufMessagesExist() {
        // Test that protobuf messages are available
        assertThat(com.rokkon.search.sdk.ProcessRequest.class).isNotNull();
        assertThat(com.rokkon.search.sdk.ProcessResponse.class).isNotNull();
        assertThat(com.rokkon.search.sdk.ServiceRegistrationResponse.class).isNotNull();
        
        // Test registration messages
        assertThat(com.rokkon.search.registration.api.RegisterModuleRequest.class).isNotNull();
        assertThat(com.rokkon.search.registration.api.RegisterModuleResponse.class).isNotNull();
        
        // Test core types
        assertThat(com.rokkon.search.model.PipeDoc.class).isNotNull();
        assertThat(com.rokkon.search.model.PipeStream.class).isNotNull();
        assertThat(com.rokkon.search.model.Blob.class).isNotNull();
    }
    
    @Test
    public void testStubsCanBeInstantiated() {
        // Create a dummy channel
        io.grpc.Channel channel = io.grpc.ManagedChannelBuilder
            .forAddress("localhost", 9090)
            .usePlaintext()
            .build();
        
        // Test that we can create blocking stubs
        var blockingStub = com.rokkon.search.sdk.PipeStepProcessorGrpc.newBlockingStub(channel);
        assertThat(blockingStub).isNotNull();
        
        var registrationStub = com.rokkon.search.registration.api.ModuleRegistrationServiceGrpc.newBlockingStub(channel);
        assertThat(registrationStub).isNotNull();
        
        // Test that we can create Mutiny stubs
        var mutinyStub = com.rokkon.search.sdk.MutinyPipeStepProcessorGrpc.newMutinyStub(channel);
        assertThat(mutinyStub).isNotNull();
        
        var mutinyRegistrationStub = com.rokkon.search.registration.api.MutinyModuleRegistrationServiceGrpc.newMutinyStub(channel);
        assertThat(mutinyRegistrationStub).isNotNull();
        
        // Close the channel
        ((io.grpc.ManagedChannel) channel).shutdownNow();
    }
    
    @Test
    public void testGoogleCommonProtosAvailable() {
        // Test that Google common protos are available
        assertThat(com.google.rpc.Status.class).isNotNull();
        assertThat(com.google.rpc.ErrorInfo.class).isNotNull();
        assertThat(com.google.protobuf.Empty.class).isNotNull();
        assertThat(com.google.protobuf.Timestamp.class).isNotNull();
    }
}