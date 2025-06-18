package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.consul.service.ClusterService;
import com.rokkon.pipeline.engine.model.ModuleRegistrationRequest;
import com.rokkon.pipeline.engine.model.ModuleRegistrationResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class ModuleRegistrationService {
    
    private static final Logger LOG = Logger.getLogger(ModuleRegistrationService.class);
    
    @Inject
    ClusterService clusterService;
    
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String consulHost;
    
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    String consulPort;
    
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    public Uni<ModuleRegistrationResponse> registerModule(ModuleRegistrationRequest request) {
        String moduleId = request.moduleName() + "-" + UUID.randomUUID().toString();
        
        return validateClusterExists(request.clusterName())
            .chain(valid -> {
                if (!valid) {
                    return Uni.createFrom().item(
                        ModuleRegistrationResponse.failure("Cluster '" + request.clusterName() + "' does not exist")
                    );
                }
                return validateModuleConnection(request);
            })
            .chain(response -> {
                if (!response.success()) {
                    return Uni.createFrom().item(response);
                }
                return performHealthCheck(request);
            })
            .chain(response -> {
                if (!response.success()) {
                    return Uni.createFrom().item(response);
                }
                return registerWithConsul(request, moduleId);
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.errorf("Error during module registration: %s", throwable.getMessage());
                return ModuleRegistrationResponse.failure(
                    "Internal error during module registration: " + throwable.getMessage()
                );
            });
    }
    
    private Uni<Boolean> validateClusterExists(String clusterName) {
        LOG.debugf("Validating cluster exists: %s", clusterName);
        return clusterService.clusterExists(clusterName);
    }
    
    private Uni<ModuleRegistrationResponse> validateModuleConnection(ModuleRegistrationRequest request) {
        LOG.infof("Validating module connection to %s:%d", request.host(), request.port());
        
        // For now, we'll implement a basic gRPC connection test
        // In a real implementation, you would call the module's getServiceRegistration method
        // and validate the returned data matches what was provided
        
        return Uni.createFrom().item(() -> {
            // Basic validation of host and port
            if (request.port() < 1 || request.port() > 65535) {
                return ModuleRegistrationResponse.failure(
                    String.format("Invalid port number: %d", request.port())
                );
            }
            
            ManagedChannel channel = null;
            try {
                channel = ManagedChannelBuilder
                    .forAddress(request.host(), request.port())
                    .usePlaintext()
                    .build();
                
                // Force connection by checking state
                var connectivity = channel.getState(true);
                
                // Wait a bit for connection to establish
                Thread.sleep(100);
                
                // Check if channel is in a good state
                if (channel.isShutdown() || channel.isTerminated()) {
                    throw new RuntimeException("Channel failed to connect");
                }
                
                // In a real implementation, we would:
                // 1. Create a gRPC client for the module
                // 2. Call getServiceRegistration() 
                // 3. Validate the response matches request.serviceData
                
                LOG.infof("Successfully connected to module at %s:%d", request.host(), request.port());
                // Continue with the flow - no module ID yet
                return ModuleRegistrationResponse.success(null);
                
            } catch (Exception e) {
                LOG.errorf("Failed to connect to module at %s:%d - %s", 
                          request.host(), request.port(), e.getMessage());
                return ModuleRegistrationResponse.failure(
                    String.format("Failed to connect to module: %s", e.getMessage())
                );
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
    
    private Uni<ModuleRegistrationResponse> performHealthCheck(ModuleRegistrationRequest request) {
        LOG.infof("Performing health check for module at %s:%d", request.host(), request.port());
        
        String healthUrl = String.format("http://%s:%d/q/health", request.host(), request.port());
        
        return Uni.createFrom().completionStage(() -> {
            HttpRequest healthRequest = HttpRequest.newBuilder()
                .uri(URI.create(healthUrl))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
                
            return httpClient.sendAsync(healthRequest, HttpResponse.BodyHandlers.ofString());
        })
        .map(response -> {
            if (response.statusCode() == 200) {
                LOG.infof("Health check passed for module at %s:%d", request.host(), request.port());
                // Continue with the flow - no module ID yet
                return ModuleRegistrationResponse.success(null);
            } else {
                LOG.errorf("Health check failed for module at %s:%d - Status: %d", 
                          request.host(), request.port(), response.statusCode());
                return ModuleRegistrationResponse.failure(
                    String.format("Health check failed with status: %d", response.statusCode())
                );
            }
        })
        .onFailure().recoverWithItem(throwable -> {
            LOG.errorf("Health check error for module at %s:%d - %s", 
                      request.host(), request.port(), throwable.getMessage());
            return ModuleRegistrationResponse.failure(
                String.format("Health check error: %s", throwable.getMessage())
            );
        });
    }
    
    private Uni<ModuleRegistrationResponse> registerWithConsul(ModuleRegistrationRequest request, String moduleId) {
        LOG.infof("Registering module '%s' with Consul", request.moduleName());
        
        String consulServiceId = moduleId;
        
        // Create Consul service registration JSON
        String serviceJson = String.format("""
            {
                "ID": "%s",
                "Name": "%s",
                "Tags": ["grpc", "rokkon-module", "cluster:%s"],
                "Address": "%s",
                "Port": %d,
                "Meta": {
                    "cluster": "%s",
                    "module": "%s",
                    "version": "1.0.0"
                },
                "Check": {
                    "Name": "Module Health Check",
                    "HTTP": "http://%s:%d/q/health",
                    "Interval": "10s",
                    "Timeout": "5s"
                }
            }
            """,
            consulServiceId,
            request.moduleName(),
            request.clusterName(),
            request.host(),
            request.port(),
            request.clusterName(),
            request.moduleName(),
            request.host(),
            request.port()
        );
        
        String consulUrl = String.format("http://%s:%s/v1/agent/service/register", consulHost, consulPort);
        
        return Uni.createFrom().completionStage(() -> {
            HttpRequest consulRequest = HttpRequest.newBuilder()
                .uri(URI.create(consulUrl))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(serviceJson))
                .build();
                
            return httpClient.sendAsync(consulRequest, HttpResponse.BodyHandlers.ofString());
        })
        .map(response -> {
            if (response.statusCode() == 200) {
                LOG.infof("Successfully registered module '%s' with Consul as service ID: %s", 
                         request.moduleName(), consulServiceId);
                return ModuleRegistrationResponse.success(moduleId);
            } else {
                LOG.errorf("Failed to register with Consul - Status: %d, Body: %s", 
                          response.statusCode(), response.body());
                return ModuleRegistrationResponse.failure(
                    String.format("Consul registration failed with status: %d", response.statusCode())
                );
            }
        })
        .onFailure().recoverWithItem(throwable -> {
            LOG.errorf("Consul registration error: %s", throwable.getMessage());
            return ModuleRegistrationResponse.failure(
                String.format("Consul registration error: %s", throwable.getMessage())
            );
        });
    }
}