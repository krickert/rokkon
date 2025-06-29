package com.rokkon.pipeline.cli.service;

import com.rokkon.search.sdk.*;
import com.rokkon.search.model.*;
import com.rokkon.search.registration.api.*;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class ModuleRegistrationService {
    
    private static final Logger LOG = Logger.getLogger(ModuleRegistrationService.class);
    
    public Uni<Boolean> registerModule(
            String moduleHost, 
            int modulePort,
            String engineHost,
            int enginePort,
            String registrationHost,
            int registrationPort,
            boolean performHealthCheck) {
        
        LOG.infof("Starting registration process for module at %s:%d", moduleHost, modulePort);
        
        // Step 1: Connect to module and get registration info with optional health check
        return getModuleInfoWithHealthCheck(moduleHost, modulePort, performHealthCheck)
            .onItem().transformToUni(registrationResponse -> {
                LOG.infof("Retrieved module info: %s", registrationResponse.getModuleName());
                
                // Check if health check passed (if it was performed)
                if (performHealthCheck && !registrationResponse.getHealthCheckPassed()) {
                    return Uni.createFrom().failure(
                        new RuntimeException("Module health check failed: " + 
                            registrationResponse.getHealthCheckMessage())
                    );
                }
                
                // Step 2: Register with engine via gRPC
                return registerWithEngine(
                    registrationResponse,
                    moduleHost,
                    modulePort,
                    engineHost,
                    enginePort,
                    registrationHost,
                    registrationPort
                );
            })
            .onFailure().recoverWithItem(error -> {
                LOG.errorf(error, "Registration failed");
                return false;
            });
    }
    
    private Uni<ServiceRegistrationResponse> getModuleInfoWithHealthCheck(String host, int port, boolean performHealthCheck) {
        return Uni.createFrom().item(() -> {
            ManagedChannel channel = null;
            try {
                LOG.debugf("Connecting to module at %s:%d", host, port);
                channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .build();
                
                var stub = com.rokkon.search.sdk.MutinyPipeStepProcessorGrpc.newMutinyStub(channel);
                
                // Build registration request with optional test data
                RegistrationRequest.Builder requestBuilder = RegistrationRequest.newBuilder();
                
                if (performHealthCheck) {
                    // Create a test document for health check
                    PipeDoc testDoc = PipeDoc.newBuilder()
                        .setId("health-check-" + UUID.randomUUID().toString())
                        .setTitle("CLI Health Check Document")
                        .setBody("This is a test document from the CLI to verify module functionality")
                        .build();
                    
                    ProcessRequest testRequest = ProcessRequest.newBuilder()
                        .setDocument(testDoc)
                        .setMetadata(ServiceMetadata.newBuilder()
                            .setPipelineName("cli-health-check")
                            .setPipeStepName("health-check")
                            .setStreamId("cli-stream-" + UUID.randomUUID().toString())
                            .setCurrentHopNumber(1)
                            .build())
                        .setConfig(ProcessConfiguration.newBuilder().build())
                        .build();
                    
                    requestBuilder.setTestRequest(testRequest);
                    LOG.debugf("Including test request for health check");
                }
                
                ServiceRegistrationResponse response = stub.getServiceRegistration(requestBuilder.build())
                    .await().atMost(Duration.ofSeconds(30)); // Increased timeout for health check
                
                LOG.debugf("Successfully retrieved module info: %s (Health: %s)", 
                    response.getModuleName(), 
                    response.getHealthCheckPassed() ? "PASSED" : "FAILED");
                
                if (!response.getHealthCheckMessage().isEmpty()) {
                    LOG.infof("Health check message: %s", response.getHealthCheckMessage());
                }
                
                return response;
                
            } catch (Exception e) {
                LOG.errorf(e, "Failed to get module info from %s:%d", host, port);
                throw new RuntimeException("Failed to connect to module", e);
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
    
    private Uni<Boolean> registerWithEngine(
            ServiceRegistrationResponse moduleData,
            String moduleHost,
            int modulePort,
            String engineHost,
            int enginePort,
            String registrationHost,
            int registrationPort) {
        
        return Uni.createFrom().item(() -> {
            ManagedChannel channel = null;
            try {
                LOG.infof("Connecting to engine at %s:%d for registration", engineHost, enginePort);
                channel = ManagedChannelBuilder.forAddress(engineHost, enginePort)
                    .usePlaintext()
                    .build();
                
                var stub = MutinyModuleRegistrationServiceGrpc.newMutinyStub(channel);
                
                // Build metadata
                Map<String, String> metadata = new HashMap<>();
                metadata.put("cli_version", "1.0.0");
                metadata.put("registered_by", "rokkon-cli");
                
                // Store module connection info
                metadata.put("module_host", moduleHost);
                metadata.put("module_port", String.valueOf(modulePort));
                
                // Store registration info (where Consul should health check)
                metadata.put("registration_host", registrationHost);
                metadata.put("registration_port", String.valueOf(registrationPort));
                
                // Add all the rich metadata from ServiceRegistrationResponse
                
                // Core fields
                String version = moduleData.getVersion().isEmpty() ? "NO_VERSION" : moduleData.getVersion();
                metadata.put("version", version);
                
                if (moduleData.hasJsonConfigSchema()) {
                    metadata.put("json_schema", moduleData.getJsonConfigSchema());
                }
                
                // UI/Developer fields
                if (moduleData.hasDisplayName()) {
                    metadata.put("display_name", moduleData.getDisplayName());
                }
                if (moduleData.hasDescription()) {
                    metadata.put("description", moduleData.getDescription());
                }
                if (moduleData.hasOwner()) {
                    metadata.put("owner", moduleData.getOwner());
                }
                if (moduleData.hasDocumentationUrl()) {
                    metadata.put("documentation_url", moduleData.getDocumentationUrl());
                }
                
                // Add tags
                if (moduleData.getTagsCount() > 0) {
                    metadata.put("tags", String.join(",", moduleData.getTagsList()));
                }
                
                // Health check status
                metadata.put("health_check_passed", String.valueOf(moduleData.getHealthCheckPassed()));
                metadata.put("health_check_message", moduleData.getHealthCheckMessage());
                
                // Runtime intelligence
                if (!moduleData.getServerInfo().isEmpty()) {
                    metadata.put("server_info", moduleData.getServerInfo());
                }
                if (!moduleData.getSdkVersion().isEmpty()) {
                    metadata.put("sdk_version", moduleData.getSdkVersion());
                }
                if (moduleData.getDependenciesCount() > 0) {
                    metadata.put("dependencies", String.join(",", moduleData.getDependenciesList()));
                }
                if (moduleData.hasRegistrationTimestamp()) {
                    // Convert protobuf Timestamp to ISO 8601 string for metadata
                    com.google.protobuf.Timestamp ts = moduleData.getRegistrationTimestamp();
                    java.time.Instant instant = java.time.Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
                    metadata.put("registration_timestamp", instant.toString());
                }
                
                // Add all custom metadata from the module
                moduleData.getMetadataMap().forEach((key, value) -> {
                    metadata.put("module_" + key, value);
                });
                
                // Create registration request for the new proto
                RegisterModuleRequest request = RegisterModuleRequest.newBuilder()
                    .setImplementationId(moduleData.getModuleName() + "-impl")
                    .setInstanceServiceName(moduleData.getModuleName())
                    .setHost(registrationHost)  // Use the registration host for Consul health checks
                    .setPort(registrationPort)  // Use the registration port for Consul health checks
                    .setHealthCheckType(HealthCheckType.GRPC)
                    .setHealthCheckEndpoint("grpc.health.v1.Health/Check")
                    .setInstanceCustomConfigJson("{}") // Empty config for now
                    .setModuleSoftwareVersion(version)  // Use the version we extracted above
                    .setInstanceIdHint(UUID.randomUUID().toString().substring(0, 8))
                    .putAllAdditionalTags(metadata)
                    .build();
                
                LOG.infof("Registering module '%s' with engine", moduleData.getModuleName());
                
                RegisterModuleResponse response = stub.registerModule(request)
                    .await().atMost(Duration.ofSeconds(30));
                
                if (response.getSuccess()) {
                    LOG.infof("Registration successful: %s (Service ID: %s)", 
                             response.getMessage(), response.getRegisteredServiceId());
                    return true;
                } else {
                    LOG.errorf("Registration failed: %s", response.getMessage());
                    return false;
                }
                
            } catch (Exception e) {
                LOG.errorf(e, "Failed to register with engine at %s:%d", engineHost, enginePort);
                throw new RuntimeException("Failed to register with engine", e);
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