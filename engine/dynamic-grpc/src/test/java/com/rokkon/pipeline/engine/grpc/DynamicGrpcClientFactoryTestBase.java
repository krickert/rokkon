package com.rokkon.pipeline.engine.grpc;

import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.search.model.PipeDoc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test class for DynamicGrpcClientFactory.
 * Contains test methods that can be run with both mocked and real implementations.
 */
public abstract class DynamicGrpcClientFactoryTestBase {
    
    protected static Server testGrpcServer;
    protected static int testGrpcPort;
    
    protected abstract DynamicGrpcClientFactory getFactory();
    
    @BeforeAll
    static void startTestServer() throws IOException {
        // Find a free port
        try (ServerSocket socket = new ServerSocket(0)) {
            testGrpcPort = socket.getLocalPort();
        }
        
        // Set the port in MockServiceDiscovery for tests that use it
        MockServiceDiscovery.setTestGrpcPort(testGrpcPort);
        
        // Start a test gRPC server
        testGrpcServer = ServerBuilder.forPort(testGrpcPort)
            .addService(new TestPipeStepProcessor())
            .build()
            .start();
    }
    
    @AfterAll
    static void stopTestServer() throws InterruptedException {
        if (testGrpcServer != null) {
            testGrpcServer.shutdown();
            testGrpcServer.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
    
    @Test
    void testDirectClientCreation() {
        // When creating a client directly
        PipeStepProcessor client = getFactory().getClient("localhost", testGrpcPort);
        
        // Then client should be created
        assertThat(client).isNotNull();
        
        // And we can make a call
        ProcessRequest request = createTestRequest();
        ProcessResponse response = client.processData(request)
            .await().atMost(java.time.Duration.ofSeconds(5));
        
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getOutputDoc().getBody()).contains("Processed:");
    }
    
    @Test
    void testClientReuse() {
        DynamicGrpcClientFactory factory = getFactory();
        
        // When creating multiple clients for the same host:port
        PipeStepProcessor client1 = factory.getClient("localhost", testGrpcPort);
        PipeStepProcessor client2 = factory.getClient("localhost", testGrpcPort);
        
        // Then they should reuse the same channel
        assertThat(factory.getActiveChannelCount()).isEqualTo(1);
        
        // And both clients should work
        ProcessRequest request = createTestRequest();
        
        ProcessResponse response1 = client1.processData(request)
            .await().atMost(java.time.Duration.ofSeconds(5));
        ProcessResponse response2 = client2.processData(request)
            .await().atMost(java.time.Duration.ofSeconds(5));
        
        assertThat(response1.getSuccess()).isTrue();
        assertThat(response2.getSuccess()).isTrue();
    }
    
    @Test
    void testMultipleChannels() {
        DynamicGrpcClientFactory factory = getFactory();
        
        // When creating clients for different hosts/ports
        PipeStepProcessor client1 = factory.getClient("localhost", testGrpcPort);
        PipeStepProcessor client2 = factory.getClient("127.0.0.1", testGrpcPort);
        
        // Then separate channels should be created
        assertThat(factory.getActiveChannelCount()).isEqualTo(2);
    }
    
    @Test
    void testMutinyClient() {
        // When creating a Mutiny client
        var mutinyClient = getFactory().getMutinyClient("localhost", testGrpcPort);
        
        // Then we can make reactive calls
        ProcessRequest request = createTestRequest();
        
        Uni<ProcessResponse> responseUni = mutinyClient.processData(request);
        ProcessResponse response = responseUni
            .await().atMost(java.time.Duration.ofSeconds(5));
        
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getOutputDoc().getBody()).contains("Processed:");
    }
    
    protected ProcessRequest createTestRequest() {
        return ProcessRequest.newBuilder()
            .setDocument(PipeDoc.newBuilder()
                .setId("test-doc-1")
                .setBody("Test content")
                .build())
            .setMetadata(ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("test-step")
                .setStreamId("test-stream")
                .build())
            .build();
    }
    
    /**
     * Simple test implementation of PipeStepProcessor
     */
    static class TestPipeStepProcessor extends com.rokkon.search.sdk.PipeStepProcessorGrpc.PipeStepProcessorImplBase {
        @Override
        public void processData(ProcessRequest request, StreamObserver<ProcessResponse> responseObserver) {
            ProcessResponse response = ProcessResponse.newBuilder()
                .setSuccess(true)
                .setOutputDoc(PipeDoc.newBuilder()
                    .setId(request.getDocument().getId())
                    .setBody("Processed: " + request.getDocument().getBody())
                    .build())
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}