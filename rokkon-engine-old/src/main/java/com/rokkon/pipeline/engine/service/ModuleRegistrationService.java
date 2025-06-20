package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.consul.service.ClusterService;
import com.rokkon.pipeline.engine.model.ModuleRegistrationRequest;
import com.rokkon.pipeline.engine.model.ModuleRegistrationResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import com.rokkon.search.sdk.PipeStepProcessorGrpc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ServiceRegistrationData;
import com.rokkon.search.model.PipeDoc;
import com.google.protobuf.Empty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

@ApplicationScoped
public class ModuleRegistrationService {
    
    private static final Logger LOG = Logger.getLogger(ModuleRegistrationService.class);
    
    @Inject
    ClusterService clusterService;
    
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String consulHost;
    
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    String consulPort;
    
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
                
                // Create gRPC stub for the module
                PipeStepProcessorGrpc.PipeStepProcessorBlockingStub moduleStub = 
                    PipeStepProcessorGrpc.newBlockingStub(channel);
                
                // Call getServiceRegistration to validate module metadata
                ServiceRegistrationData registrationData = moduleStub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .getServiceRegistration(Empty.getDefaultInstance());
                
                // Validate the module name matches
                if (!request.moduleName().equals(registrationData.getModuleName())) {
                    LOG.warnf("Module name mismatch: request='%s', module reports='%s'", 
                              request.moduleName(), registrationData.getModuleName());
                    throw new RuntimeException(String.format(
                        "Module name mismatch: request='%s', module reports='%s'", 
                        request.moduleName(), registrationData.getModuleName()));
                }
                
                LOG.infof("Successfully connected to module '%s' at %s:%d", 
                          registrationData.getModuleName(), request.host(), request.port());
                
                // Store the registration data in metadata for later use
                // This is a temporary solution - ideally we'd pass it through the flow
                if (request.metadata() != null) {
                    request.metadata().put("jsonConfigSchema", registrationData.getJsonConfigSchema());
                }
                
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
        LOG.infof("Performing gRPC health check for module at %s:%d", request.host(), request.port());
        
        return Uni.createFrom().item(() -> {
            ManagedChannel channel = null;
            try {
                // Create gRPC channel to the module
                channel = ManagedChannelBuilder
                    .forAddress(request.host(), request.port())
                    .usePlaintext()
                    .build();
                
                // Create health check stub
                HealthGrpc.HealthBlockingStub healthStub = HealthGrpc.newBlockingStub(channel);
                
                // Perform gRPC health check
                HealthCheckRequest healthRequest = HealthCheckRequest.newBuilder()
                    .setService("") // Empty string checks overall health
                    .build();
                    
                HealthCheckResponse response = healthStub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .check(healthRequest);
                
                if (response.getStatus() == HealthCheckResponse.ServingStatus.SERVING) {
                    LOG.infof("gRPC health check passed for module at %s:%d", request.host(), request.port());
                    // Continue with the flow - no module ID yet
                    return ModuleRegistrationResponse.success(null);
                } else {
                    LOG.errorf("gRPC health check failed for module at %s:%d - Status: %s", 
                              request.host(), request.port(), response.getStatus());
                    return ModuleRegistrationResponse.failure(
                        String.format("gRPC health check failed with status: %s", response.getStatus())
                    );
                }
            } catch (StatusRuntimeException e) {
                LOG.errorf("gRPC health check error for module at %s:%d - %s", 
                          request.host(), request.port(), e.getMessage());
                return ModuleRegistrationResponse.failure(
                    String.format("gRPC health check error: %s", e.getMessage())
                );
            } catch (Exception e) {
                LOG.errorf("Health check error for module at %s:%d - %s", 
                          request.host(), request.port(), e.getMessage());
                return ModuleRegistrationResponse.failure(
                    String.format("Health check error: %s", 
                        e.getMessage() != null ? e.getMessage() : "Connection failed")
                );
            } finally {
                if (channel != null) {
                    try {
                        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        channel.shutdownNow();
                    }
                }
            }
        });
    }
    
    private Uni<ModuleRegistrationResponse> registerWithConsul(ModuleRegistrationRequest request, String moduleId) {
        LOG.infof("Registering module '%s' with Consul", request.moduleName());
        
        String consulServiceId = moduleId;
        
        // Determine the host and port for Consul registration
        // If metadata contains consulHost/consulPort, use those for Consul's internal access
        String consulHost = request.host();
        int consulPort = request.port();
        
        if (request.metadata() != null) {
            String metaConsulHost = request.metadata().get("consulHost");
            String metaConsulPort = request.metadata().get("consulPort");
            
            if (metaConsulHost != null && !metaConsulHost.isEmpty()) {
                consulHost = metaConsulHost;
                LOG.infof("Using Consul-specific host from metadata: %s", consulHost);
            }
            
            if (metaConsulPort != null && !metaConsulPort.isEmpty()) {
                try {
                    consulPort = Integer.parseInt(metaConsulPort);
                    LOG.infof("Using Consul-specific port from metadata: %d", consulPort);
                } catch (NumberFormatException e) {
                    LOG.warnf("Invalid consulPort in metadata: %s, using default: %d", metaConsulPort, consulPort);
                }
            }
        }
        
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
                    "version": "1.0.0",
                    "externalHost": "%s",
                    "externalPort": "%d"
                },
                "Check": {
                    "Name": "Module Health Check", 
                    "GRPC": "%s:%d",
                    "GRPCUseTLS": false,
                    "Interval": "10s",
                    "Timeout": "5s"
                }
            }
            """,
            consulServiceId,
            request.moduleName(),
            request.clusterName(),
            consulHost,  // Use Consul-accessible host
            consulPort,   // Use Consul-accessible port
            request.clusterName(),
            request.moduleName(),
            request.host(),  // Store original host in metadata
            request.port(),  // Store original port in metadata
            consulHost,      // Health check uses Consul-accessible host/port
            consulPort
        );
        
        String consulUrl = String.format("http://%s:%s/v1/agent/service/register", this.consulHost, this.consulPort);
        
        return Uni.createFrom().completionStage(() -> {
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest consulRequest = HttpRequest.newBuilder()
                .uri(URI.create(consulUrl))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(serviceJson))
                .build();
                
            return httpClient.sendAsync(consulRequest, HttpResponse.BodyHandlers.ofString());
        })
        .chain(response -> {
            if (response.statusCode() == 200) {
                LOG.infof("Successfully registered module '%s' with Consul as service ID: %s", 
                         request.moduleName(), consulServiceId);
                
                // Store module metadata in Consul KV if available
                if (request.metadata() != null && request.metadata().containsKey("jsonConfigSchema")) {
                    storeModuleMetadataInConsul(moduleId, request.metadata().get("jsonConfigSchema"));
                }
                
                // Verify the service is actually registered in Consul
                return verifyServiceInConsul(consulServiceId, moduleId);
            } else {
                LOG.errorf("Failed to register with Consul - Status: %d, Body: %s", 
                          response.statusCode(), response.body());
                return Uni.createFrom().item(
                    ModuleRegistrationResponse.failure(
                        String.format("Consul registration failed with status: %d", response.statusCode())
                    )
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
    
    private Uni<ModuleRegistrationResponse> verifyServiceInConsul(String consulServiceId, String moduleId) {
        LOG.infof("Verifying service '%s' is registered in Consul", consulServiceId);
        
        String verifyUrl = String.format("http://%s:%s/v1/agent/service/%s", consulHost, consulPort, consulServiceId);
        
        // Poll up to 5 seconds with 100ms intervals
        return Uni.createFrom().completionStage(() -> {
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest verifyRequest = HttpRequest.newBuilder()
                .uri(URI.create(verifyUrl))
                .GET()
                .build();
            return httpClient.sendAsync(verifyRequest, HttpResponse.BodyHandlers.ofString());
        })
        .onFailure().retry()
            .withBackOff(Duration.ofMillis(100))
            .atMost(50) // 50 retries * 100ms = 5 seconds max
        .chain(response -> {
            if (response.statusCode() == 200) {
                LOG.infof("Verified service '%s' is registered in Consul", consulServiceId);
                // Now wait for the service to be healthy
                return waitForHealthyService(consulServiceId, moduleId);
            } else if (response.statusCode() == 404) {
                LOG.errorf("Service '%s' not found in Consul after registration", consulServiceId);
                return Uni.createFrom().item(
                    ModuleRegistrationResponse.failure(
                        String.format("Service verification failed - service not found in Consul")
                    )
                );
            } else {
                LOG.errorf("Unexpected response verifying service in Consul - Status: %d", response.statusCode());
                return Uni.createFrom().item(
                    ModuleRegistrationResponse.failure(
                        String.format("Service verification failed with status: %d", response.statusCode())
                    )
                );
            }
        })
        .onFailure().recoverWithItem(throwable -> {
            LOG.errorf("Failed to verify service in Consul: %s", throwable.getMessage());
            return ModuleRegistrationResponse.failure(
                String.format("Service verification error: %s", throwable.getMessage())
            );
        });
    }
    
    private Uni<ModuleRegistrationResponse> waitForHealthyService(String consulServiceId, String moduleId) {
        LOG.infof("Waiting for service '%s' to be healthy in Consul", consulServiceId);
        
        String healthUrl = String.format("http://%s:%s/v1/health/service/%s?passing=true", 
                                       consulHost, consulPort, consulServiceId.split("-")[0]); // Use service name, not full ID
        
        // Poll up to 30 seconds for the service to become healthy
        return Uni.createFrom().completionStage(() -> {
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest healthRequest = HttpRequest.newBuilder()
                .uri(URI.create(healthUrl))
                .GET()
                .build();
            return httpClient.sendAsync(healthRequest, HttpResponse.BodyHandlers.ofString());
        })
        .onFailure().retry()
            .withBackOff(Duration.ofMillis(500))
            .atMost(60) // 60 retries * 500ms = 30 seconds max
        .chain(response -> {
            if (response.statusCode() == 200) {
                String body = response.body();
                // Check if we have any healthy services in the response
                if (body != null && body.contains(consulServiceId) && !body.equals("[]")) {
                    LOG.infof("Service '%s' is healthy in Consul", consulServiceId);
                    // TEMPORARY TEST: Try to actually call the service
                    return testServiceCall(consulServiceId, moduleId);
                } else {
                    LOG.warnf("Service '%s' not yet healthy, response: %s", consulServiceId, body);
                    // Continue retrying via the retry mechanism
                    return Uni.createFrom().failure(new RuntimeException("Service not yet healthy"));
                }
            } else {
                LOG.errorf("Health check query failed with status: %d", response.statusCode());
                return Uni.createFrom().failure(new RuntimeException("Health check query failed"));
            }
        })
        .onFailure().recoverWithItem(throwable -> {
            LOG.errorf("Service '%s' did not become healthy within timeout: %s", 
                      consulServiceId, throwable.getMessage());
            // Still return success as the service is registered, just not healthy yet
            LOG.warnf("Proceeding despite health check timeout - service is registered");
            return ModuleRegistrationResponse.success(moduleId);
        });
    }
    
    // TEMPORARY TEST METHOD
    private Uni<ModuleRegistrationResponse> testServiceCall(String consulServiceId, String moduleId) {
        LOG.infof("TEMPORARY TEST: Attempting to call service '%s' via gRPC", consulServiceId);
        
        // For now, we'll parse the service ID to get the host/port from Consul
        // In production, we'd use Stork service discovery
        String serviceUrl = String.format("http://%s:%s/v1/agent/service/%s", consulHost, consulPort, consulServiceId);
        
        return Uni.createFrom().completionStage(() -> {
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serviceUrl))
                .GET()
                .build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        })
        .chain(response -> {
            if (response.statusCode() != 200) {
                return Uni.createFrom().item(ModuleRegistrationResponse.success(moduleId));
            }
            
            // Parse the service info to get host/port
            // This is a simplified version - in reality we'd parse the JSON properly
            String body = response.body();
            LOG.debugf("Service info from Consul: %s", body);
            
            // Extract host and port from the response (this is temporary)
            // In real implementation, we'd use Stork
            String host = null;
            int port = -1;
            
            // Try to find Address field
            int addressStart = body.indexOf("\"Address\": \"");
            if (addressStart != -1) {
                addressStart += 12; // Length of "\"Address\": \""
                int addressEnd = body.indexOf("\"", addressStart);
                if (addressEnd != -1) {
                    host = body.substring(addressStart, addressEnd);
                }
            } else {
                // Try without space
                addressStart = body.indexOf("\"Address\":\"");
                if (addressStart != -1) {
                    addressStart += 11; // Length of "\"Address\":\""
                    int addressEnd = body.indexOf("\"", addressStart);
                    if (addressEnd != -1) {
                        host = body.substring(addressStart, addressEnd);
                    }
                }
            }
            
            // Try to find Port field
            int portStart = body.indexOf("\"Port\": ");
            if (portStart != -1) {
                portStart += 8; // Length of "\"Port\": "
                int portEnd = body.indexOf(",", portStart);
                if (portEnd == -1) {
                    portEnd = body.indexOf("}", portStart);
                }
                if (portEnd != -1) {
                    try {
                        port = Integer.parseInt(body.substring(portStart, portEnd).trim());
                    } catch (NumberFormatException e) {
                        LOG.errorf("Failed to parse port number: %s", body.substring(portStart, portEnd));
                    }
                }
            } else {
                // Try without space
                portStart = body.indexOf("\"Port\":");
                if (portStart != -1) {
                    portStart += 7; // Length of "\"Port\":"
                    int portEnd = body.indexOf(",", portStart);
                    if (portEnd == -1) {
                        portEnd = body.indexOf("}", portStart);
                    }
                    if (portEnd != -1) {
                        try {
                            port = Integer.parseInt(body.substring(portStart, portEnd).trim());
                        } catch (NumberFormatException e) {
                            LOG.errorf("Failed to parse port number: %s", body.substring(portStart, portEnd));
                        }
                    }
                }
            }
            
            if (host == null || port == -1) {
                LOG.errorf("Failed to extract host/port from Consul response: %s", body);
                return Uni.createFrom().item(ModuleRegistrationResponse.success(moduleId));
            }
            
            LOG.infof("TEMPORARY TEST: Calling service at %s:%d", host, port);
            
            // Create a test document
            return callServiceWithTestDoc(host, port, moduleId);
        });
    }
    
    private Uni<ModuleRegistrationResponse> callServiceWithTestDoc(String host, int port, String moduleId) {
        return Uni.createFrom().item(() -> {
            ManagedChannel channel = null;
            try {
                channel = ManagedChannelBuilder
                    .forAddress(host, port)
                    .usePlaintext()
                    .build();
                
                PipeStepProcessorGrpc.PipeStepProcessorBlockingStub stub = 
                    PipeStepProcessorGrpc.newBlockingStub(channel);
                
                // Create a simple test document
                PipeDoc testDoc = PipeDoc.newBuilder()
                    .setId("test-doc-" + System.currentTimeMillis())
                    .setTitle("Test Document for Service Verification")
                    .build();
                
                ProcessRequest request = ProcessRequest.newBuilder()
                    .setDocument(testDoc)
                    .build();
                
                // Try to call the service
                ProcessResponse response = stub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .processData(request);
                
                if (response.getSuccess()) {
                    LOG.infof("TEMPORARY TEST: Successfully called service! Response: %s", 
                             response.getProcessorLogsList());
                    return ModuleRegistrationResponse.success(moduleId);
                } else {
                    LOG.errorf("TEMPORARY TEST: Service call failed - response not successful");
                    return ModuleRegistrationResponse.failure(
                        "Service verification failed - service returned unsuccessful response"
                    );
                }
            } catch (Exception e) {
                LOG.errorf("TEMPORARY TEST: Failed to call service - %s", e.getMessage());
                // For now, we'll still return success since the service IS registered
                // This is just a test to see if we can reach it
                LOG.warnf("TEMPORARY TEST: Continuing despite call failure (service is registered)");
                return ModuleRegistrationResponse.success(moduleId);
            } finally {
                if (channel != null) {
                    try {
                        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        channel.shutdownNow();
                    }
                }
            }
        });
    }
    
    /**
     * Store module metadata in Consul KV store.
     * This includes the JSON schema configuration for the module.
     */
    private void storeModuleMetadataInConsul(String moduleId, String jsonConfigSchema) {
        String kvKey = String.format("rokkon/modules/%s/config-schema", moduleId);
        String consulKvUrl = String.format("http://%s:%s/v1/kv/%s", consulHost, consulPort, kvKey);
        
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(consulKvUrl))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(jsonConfigSchema))
            .build();
        
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(response -> {
                if (response.statusCode() == 200) {
                    LOG.infof("Successfully stored module configuration schema in Consul KV at %s", kvKey);
                } else {
                    LOG.warnf("Failed to store module configuration schema in Consul KV - Status: %d", 
                             response.statusCode());
                }
            })
            .exceptionally(throwable -> {
                LOG.errorf("Error storing module configuration schema in Consul KV: %s", 
                          throwable.getMessage());
                return null;
            });
    }
}