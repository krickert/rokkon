package com.rokkon.pipeline.cli.service;

import com.rokkon.search.sdk.*;
import com.rokkon.search.model.*;
import com.rokkon.search.registration.api.*;
import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class ModuleRegistrationService {
    private static final int TIMEOUT_SECONDS = 30;
    
    public boolean registerModule(
            String moduleHost, 
            int modulePort,
            String engineHost,
            int enginePort,
            String registrationHost,
            int registrationPort,
            boolean performHealthCheck) {
        
        Log.infof("Starting registration process for module at %s:%d", moduleHost, modulePort);
        
        ManagedChannel moduleChannel = null;
        ManagedChannel engineChannel = null;
        
        try {
            // Step 1: Connect to module and get registration info
            moduleChannel = ManagedChannelBuilder
                .forAddress(moduleHost, modulePort)
                .usePlaintext()
                .build();
            
            PipeStepProcessorGrpc.PipeStepProcessorBlockingStub moduleStub = 
                PipeStepProcessorGrpc.newBlockingStub(moduleChannel)
                    .withDeadlineAfter(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            // Get module registration info
            RegistrationRequest registrationRequest = RegistrationRequest.newBuilder().build();
            ServiceRegistrationResponse registrationResponse = moduleStub.getServiceRegistration(registrationRequest);
            Log.infof("Retrieved module info: %s", registrationResponse.getModuleName());
            
            // Health check result is included in the registration response
            if (performHealthCheck) {
                if (!registrationResponse.getHealthCheckPassed()) {
                    Log.errorf("Module health check failed: %s", registrationResponse.getHealthCheckMessage());
                    return false;
                }
                Log.info("Module health check passed");
            }
            
            // Step 2: Register with engine via gRPC
            engineChannel = ManagedChannelBuilder
                .forAddress(engineHost, enginePort)
                .usePlaintext()
                .build();
            
            ModuleRegistrationServiceGrpc.ModuleRegistrationServiceBlockingStub engineStub = 
                ModuleRegistrationServiceGrpc.newBlockingStub(engineChannel)
                    .withDeadlineAfter(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            // Build registration request
            // Service name is simple (e.g., "echo"), instance ID is unique
            String instanceId = registrationResponse.getModuleName() + "-" + UUID.randomUUID().toString().substring(0, 8);
            RegisterModuleRequest.Builder requestBuilder = RegisterModuleRequest.newBuilder()
                .setImplementationId(registrationResponse.getModuleName())
                .setInstanceServiceName(registrationResponse.getModuleName())  // Simple service name for Consul load balancing
                .setHost(registrationHost)
                .setPort(registrationPort)
                .setHealthCheckType(HealthCheckType.GRPC);  // Use standard gRPC health check
            
            // Add tags and metadata as additional tags
            Map<String, String> additionalTags = new HashMap<>();
            
            // Add module tags
            for (String tag : registrationResponse.getTagsList()) {
                additionalTags.put("tag:" + tag, tag);
            }
            
            // Add metadata
            additionalTags.putAll(registrationResponse.getMetadataMap());
            
            // Add instance ID for tracking specific instances
            additionalTags.put("instance_id", instanceId);
            
            // Add version info
            additionalTags.put("module_version", registrationResponse.getVersion());
            additionalTags.put("sdk_version", registrationResponse.getSdkVersion());
            
            requestBuilder.putAllAdditionalTags(additionalTags);
            
            RegisterModuleRequest request = requestBuilder.build();
            Log.infof("Sending registration request to engine at %s:%d", engineHost, enginePort);
            
            RegisterModuleResponse response = engineStub.registerModule(request);
            
            if (response.getSuccess()) {
                Log.infof("Module registered successfully: %s", response.getMessage());
                return true;
            } else {
                Log.errorf("Registration failed: %s", response.getMessage());
                return false;
            }
            
        } catch (Exception e) {
            Log.error("Error during module registration", e);
            return false;
        } finally {
            // Cleanup channels
            if (moduleChannel != null) {
                try {
                    moduleChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    moduleChannel.shutdownNow();
                }
            }
            if (engineChannel != null) {
                try {
                    engineChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    engineChannel.shutdownNow();
                }
            }
        }
    }
}