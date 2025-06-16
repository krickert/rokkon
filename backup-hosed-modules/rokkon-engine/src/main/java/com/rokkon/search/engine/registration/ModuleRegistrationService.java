package com.rokkon.search.engine.registration;

import com.rokkon.search.sdk.PipeStepProcessorGrpc;
import com.rokkon.search.sdk.ServiceRegistrationData;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.rokkon.search.config.pipeline.service.SchemaValidationService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for validating and registering new modules with the engine.
 * 
 * Registration Flow:
 * 1. Validate module health via gRPC health check
 * 2. Test PipeStepProcessor interface connectivity  
 * 3. Get module registration metadata
 * 4. Register with Consul if all validations pass
 * 5. Create dynamic gRPC client for engine use
 */
@ApplicationScoped
public class ModuleRegistrationService {
    
    private static final Logger LOG = LoggerFactory.getLogger(ModuleRegistrationService.class);
    
    @Inject
    ConsulModuleRegistry consulRegistry;
    
    @Inject  
    DynamicGrpcClientManager clientManager;
    
    @Inject
    SchemaValidationService schemaValidationService;
    
    /**
     * Register a new module after full validation
     */
    public ModuleRegistrationResponse registerModule(ModuleRegistrationRequest request) {
        LOG.info("Starting registration for module: {} at {}:{}", 
                request.moduleName(), request.host(), request.port());
        
        List<String> errors = new ArrayList<>();
        
        // Step 1: Validate gRPC health
        if (!validateModuleHealth(request, errors)) {
            return ModuleRegistrationResponse.failure("Module health check failed", errors);
        }
        
        // Step 2: Test PipeStepProcessor interface
        ServiceRegistrationData moduleMetadata = validatePipeStepProcessor(request, errors);
        if (moduleMetadata == null) {
            return ModuleRegistrationResponse.failure("PipeStepProcessor interface validation failed", errors);
        }
        
        // Step 3: Validate JSON schema if provided
        if (!validateModuleJsonSchema(request, moduleMetadata, errors)) {
            return ModuleRegistrationResponse.failure("JSON schema validation failed", errors);
        }
        
        // Step 4: Register with Consul
        String consulServiceId = registerWithConsul(request, moduleMetadata, errors);
        if (consulServiceId == null) {
            return ModuleRegistrationResponse.failure("Consul registration failed", errors);
        }
        
        // Step 5: Create dynamic gRPC client for engine
        String moduleId = UUID.randomUUID().toString();
        try {
            clientManager.createClient(moduleId, request.host(), request.port());
            LOG.info("✅ Module registration completed: {} -> {}", request.moduleName(), moduleId);
            
            return ModuleRegistrationResponse.success(
                moduleId,
                consulServiceId,
                String.format("Module '%s' successfully registered and ready for pipeline use", request.moduleName())
            );
            
        } catch (Exception e) {
            LOG.error("Failed to create gRPC client for registered module", e);
            errors.add("Failed to create engine gRPC client: " + e.getMessage());
            
            // Cleanup Consul registration on client creation failure
            consulRegistry.deregisterService(consulServiceId);
            
            return ModuleRegistrationResponse.failure("gRPC client creation failed", errors);
        }
    }
    
    /**
     * Step 1: Validate module health via standard gRPC health check
     */
    private boolean validateModuleHealth(ModuleRegistrationRequest request, List<String> errors) {
        LOG.debug("Validating health for module at {}:{}", request.host(), request.port());
        
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress(request.host(), request.port())
                    .usePlaintext()
                    .build();
            
            HealthGrpc.HealthBlockingStub healthClient = HealthGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(10, TimeUnit.SECONDS);
            
            HealthCheckRequest healthRequest = HealthCheckRequest.newBuilder()
                    .setService("") // Check overall service health
                    .build();
            
            HealthCheckResponse healthResponse = healthClient.check(healthRequest);
            
            if (healthResponse.getStatus() == HealthCheckResponse.ServingStatus.SERVING) {
                LOG.debug("✅ Module health check passed");
                return true;
            } else {
                errors.add("Module health check returned: " + healthResponse.getStatus());
                return false;
            }
            
        } catch (Exception e) {
            LOG.error("Health check failed for module at {}:{}", request.host(), request.port(), e);
            errors.add("Health check failed: " + e.getMessage());
            return false;
        } finally {
            if (channel != null) {
                channel.shutdown();
            }
        }
    }
    
    /**
     * Step 2: Validate PipeStepProcessor interface and get module metadata
     */
    private ServiceRegistrationData validatePipeStepProcessor(ModuleRegistrationRequest request, List<String> errors) {
        LOG.debug("Validating PipeStepProcessor interface for module at {}:{}", request.host(), request.port());
        
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress(request.host(), request.port())
                    .usePlaintext()
                    .build();
            
            PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client = 
                    PipeStepProcessorGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(10, TimeUnit.SECONDS);
            
            // Test getServiceRegistration method
            com.google.protobuf.Empty emptyRequest = com.google.protobuf.Empty.newBuilder().build();
            ServiceRegistrationData registrationData = client.getServiceRegistration(emptyRequest);
            
            LOG.debug("✅ PipeStepProcessor interface validated. Module: {}", registrationData.getModuleName());
            return registrationData;
            
        } catch (Exception e) {
            LOG.error("PipeStepProcessor validation failed for module at {}:{}", request.host(), request.port(), e);
            errors.add("PipeStepProcessor interface validation failed: " + e.getMessage());
            return null;
        } finally {
            if (channel != null) {
                channel.shutdown();
            }
        }
    }
    
    /**
     * Step 3: Validate JSON schema if provided by the module
     */
    private boolean validateModuleJsonSchema(ModuleRegistrationRequest request, ServiceRegistrationData metadata, List<String> errors) {
        LOG.debug("Validating JSON schema for module: {}", request.moduleName());
        
        // Check if module provides a JSON schema
        if (!metadata.hasJsonConfigSchema()) {
            LOG.debug("✅ No JSON schema provided by module - validation skipped");
            return true; // No schema means no validation needed
        }
        
        String moduleSchema = metadata.getJsonConfigSchema();
        LOG.debug("Module provided JSON schema, validating...");
        
        try {
            // Validate that the provided schema is valid JSON Schema
            var validationResult = schemaValidationService.validateJsonSchema(moduleSchema);
            
            if (!validationResult.valid()) {
                errors.addAll(validationResult.errors());
                LOG.error("Invalid JSON schema provided by module {}: {}", request.moduleName(), validationResult.errors());
                return false;
            }
            
            // Schema is valid JSON Schema v7 - no additional requirements needed!
            
            LOG.debug("✅ JSON schema validation passed for module: {}", request.moduleName());
            return true;
            
        } catch (Exception e) {
            LOG.error("JSON schema validation failed for module: {}", request.moduleName(), e);
            errors.add("Schema validation error: " + e.getMessage());
            return false;
        }
    }
    
    
    /**
     * Step 4: Register the validated module with Consul
     */
    private String registerWithConsul(ModuleRegistrationRequest request, ServiceRegistrationData metadata, List<String> errors) {
        LOG.debug("Registering module with Consul: {}", request.moduleName());
        
        try {
            return consulRegistry.registerPipeStepProcessor(
                request.moduleName(),
                request.host(), 
                request.port(),
                metadata,
                request.metadata()
            );
        } catch (Exception e) {
            LOG.error("Consul registration failed for module: {}", request.moduleName(), e);
            errors.add("Consul registration failed: " + e.getMessage());
            return null;
        }
    }
}