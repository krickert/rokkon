package com.rokkon.cli.service;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.MutinyHealthGrpc;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class ModuleHealthService {
    
    private static final Logger LOG = Logger.getLogger(ModuleHealthService.class);
    
    public Uni<Boolean> checkModuleHealth(String host, int port) {
        return Uni.createFrom().item(() -> {
            ManagedChannel channel = null;
            try {
                LOG.debugf("Performing health check on %s:%d", host, port);
                
                channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .build();
                
                var healthStub = MutinyHealthGrpc.newMutinyStub(channel);
                
                // Check overall service health
                HealthCheckRequest request = HealthCheckRequest.newBuilder()
                    .setService("") // Empty string checks overall health
                    .build();
                
                HealthCheckResponse response = healthStub.check(request)
                    .await().atMost(Duration.ofSeconds(10));
                
                boolean healthy = response.getStatus() == HealthCheckResponse.ServingStatus.SERVING;
                
                if (healthy) {
                    LOG.infof("Module at %s:%d is healthy", host, port);
                } else {
                    LOG.warnf("Module at %s:%d returned status: %s", host, port, response.getStatus());
                }
                
                return healthy;
                
            } catch (Exception e) {
                LOG.errorf(e, "Health check failed for %s:%d", host, port);
                return false;
            } finally {
                if (channel != null) {
                    try {
                        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        channel.shutdownNow();
                    }
                }
            }
        });
    }
}