package com.rokkon.testmodule.health;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to prove that standard gRPC clients can connect to Mutiny services.
 * This demonstrates that the gRPC protocol is the same regardless of stub type.
 */
@QuarkusTest
class MixedHealthCheckTest {
    
    // Removed Mutiny client since we're testing with standard stubs
    
    private ManagedChannel channel;
    private HealthGrpc.HealthBlockingStub blockingHealthService;
    private HealthGrpc.HealthStub asyncHealthService;
    
    @BeforeEach
    void setup() {
        // Create a channel to the same service
        channel = ManagedChannelBuilder
                .forAddress("localhost", 0) // Quarkus will use random port
                .usePlaintext()
                .build();
        
        // Create both blocking and async stubs
        blockingHealthService = HealthGrpc.newBlockingStub(channel);
        asyncHealthService = HealthGrpc.newStub(channel);
    }
    
    @AfterEach
    void cleanup() {
        if (channel != null) {
            channel.shutdown();
        }
    }
    
    
    @Test
    void testBlockingClientCanCallMutinyService() {
        // Connect to Quarkus dev server port (usually 9000 or configured)
        ManagedChannel devChannel = ManagedChannelBuilder
                .forAddress("localhost", 9000) // Default Quarkus gRPC port
                .usePlaintext()
                .build();
        
        try {
            HealthGrpc.HealthBlockingStub blockingStub = HealthGrpc.newBlockingStub(devChannel);
            HealthCheckRequest request = HealthCheckRequest.newBuilder().build();
            
            // Standard blocking client calling Mutiny service
            HealthCheckResponse response = blockingStub.check(request);
            
            assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        } finally {
            devChannel.shutdown();
        }
    }
    
    @Test
    void testAsyncClientCanCallMutinyService() {
        // Connect to Quarkus dev server port
        ManagedChannel devChannel = ManagedChannelBuilder
                .forAddress("localhost", 9000)
                .usePlaintext()
                .build();
        
        try {
            HealthGrpc.HealthStub asyncStub = HealthGrpc.newStub(devChannel);
            HealthCheckRequest request = HealthCheckRequest.newBuilder().build();
            
            // Use a simple callback to capture the response
            var responseHolder = new Object() {
                volatile HealthCheckResponse response = null;
                volatile Throwable error = null;
            };
            
            asyncStub.check(request, new io.grpc.stub.StreamObserver<HealthCheckResponse>() {
                @Override
                public void onNext(HealthCheckResponse value) {
                    responseHolder.response = value;
                }
                
                @Override
                public void onError(Throwable t) {
                    responseHolder.error = t;
                }
                
                @Override
                public void onCompleted() {
                    // Nothing to do
                }
            });
            
            // Wait a bit for the async response
            Thread.sleep(100);
            
            assertThat(responseHolder.error).isNull();
            assertThat(responseHolder.response).isNotNull();
            assertThat(responseHolder.response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            devChannel.shutdown();
        }
    }
}