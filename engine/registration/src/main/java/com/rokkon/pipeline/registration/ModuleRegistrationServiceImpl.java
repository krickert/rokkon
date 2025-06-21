package com.rokkon.pipeline.registration;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import com.rokkon.search.grpc.*;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@GrpcService
@Singleton
public class ModuleRegistrationServiceImpl implements ModuleRegistration {
    
    private static final Logger LOG = Logger.getLogger(ModuleRegistrationServiceImpl.class);
    
    private final Map<String, ModuleInfo> registeredModules = new ConcurrentHashMap<>();
    
    @Inject
    Event<ModuleRegistrationEvent> eventPublisher;
    
    @Inject
    ConsulModuleRegistry consulRegistry;
    
    /**
     * Result of module authentication
     */
    private static class AuthenticationResult {
        private final boolean authenticated;
        private final String reason;
        
        private AuthenticationResult(boolean authenticated, String reason) {
            this.authenticated = authenticated;
            this.reason = reason;
        }
        
        static AuthenticationResult success() {
            return new AuthenticationResult(true, null);
        }
        
        static AuthenticationResult failure(String reason) {
            return new AuthenticationResult(false, reason);
        }
        
        boolean isAuthenticated() { return authenticated; }
        String getReason() { return reason; }
    }
    
    @Override
    public Uni<RegistrationStatus> registerModule(ModuleInfo request) {
        LOG.infof("📥 Module registration request: %s at %s:%d", 
                request.getServiceName(), request.getHost(), request.getPort());
        
        // First authenticate the module
        return authenticateModule(request)
            .onItem().transformToUni(authResult -> {
                if (!authResult.isAuthenticated()) {
                    LOG.warnf("❌ Module authentication failed for %s: %s", 
                            request.getServiceName(), authResult.getReason());
                    return Uni.createFrom().item(createFailureStatus(
                            "Authentication failed: " + authResult.getReason()));
                }
                
                // If authenticated, register with Consul
                return consulRegistry.registerService(request)
                    .onItem().transform(consulServiceId -> {
                try {
                    // Store the module info only after successful Consul registration
                    registeredModules.put(request.getServiceId(), request);
                    
                    // Create success response
                    Instant now = Instant.now();
                    Timestamp timestamp = Timestamp.newBuilder()
                        .setSeconds(now.getEpochSecond())
                        .setNanos(now.getNano())
                        .build();
                    
                    String consulServiceIdStr = consulServiceId.toString();
                    RegistrationStatus response = RegistrationStatus.newBuilder()
                        .setSuccess(true)
                        .setMessage(String.format("Module '%s' successfully registered with Consul", request.getServiceName()))
                        .setRegisteredAt(timestamp)
                        .setConsulServiceId(consulServiceIdStr)
                        .build();
                    
                    // Fire registration event  
                    eventPublisher.fire(new ModuleRegistrationEvent(
                        "REGISTERED", 
                        request.getServiceName(),
                        request.getServiceId(), 
                        consulServiceIdStr
                    ));
                    
                    LOG.infof("✅ Module registration successful: %s -> %s", 
                            request.getServiceName(), consulServiceIdStr);
                            
                    return response;
                    
                } catch (Exception e) {
                    LOG.errorf(e, "❌ Module registration failed after Consul success: %s", request.getServiceName());
                    throw new RuntimeException("Post-registration processing failed", e);
                }
            });
            })
            .onFailure().recoverWithItem(failure -> {
                LOG.errorf(failure, "❌ Module registration failed: %s", request.getServiceName());
                
                return RegistrationStatus.newBuilder()
                    .setSuccess(false)
                    .setMessage("Registration failed: " + failure.getMessage())
                    .build();
            });
    }
    
    @Override
    public Uni<UnregistrationStatus> unregisterModule(ModuleId request) {
        LOG.infof("🗑️ Module unregistration request: %s", request.getServiceId());
        
        // Check if module exists locally
        ModuleInfo moduleInfo = registeredModules.get(request.getServiceId());
        if (moduleInfo == null) {
            Instant now = Instant.now();
            Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
                
            LOG.warnf("⚠️ Module not found for unregistration: %s", request.getServiceId());
            
            return Uni.createFrom().item(UnregistrationStatus.newBuilder()
                .setSuccess(false)
                .setMessage("Module not found: " + request.getServiceId())
                .setUnregisteredAt(timestamp)
                .build());
        }
        
        // Build consul service ID and unregister from Consul
        String consulServiceId = "grpc-" + request.getServiceId();
        return consulRegistry.unregisterService(consulServiceId)
            .onItem().transform(success -> {
                Instant now = Instant.now();
                Timestamp timestamp = Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .setNanos(now.getNano())
                    .build();
                    
                if (success) {
                    // Remove from local registry after successful Consul unregistration
                    registeredModules.remove(request.getServiceId());
                    
                    LOG.infof("✅ Module unregistered successfully from Consul: %s", request.getServiceId());
                    
                    return UnregistrationStatus.newBuilder()
                        .setSuccess(true)
                        .setMessage("Module unregistered successfully from Consul")
                        .setUnregisteredAt(timestamp)
                        .build();
                } else {
                    LOG.errorf("❌ Failed to unregister module from Consul: %s", request.getServiceId());
                    
                    return UnregistrationStatus.newBuilder()
                        .setSuccess(false)
                        .setMessage("Failed to unregister from Consul: " + request.getServiceId())
                        .setUnregisteredAt(timestamp)
                        .build();
                }
            });
    }
    
    @Override
    public Uni<HeartbeatAck> heartbeat(ModuleHeartbeat request) {
        LOG.debugf("💗 Heartbeat from module: %s", request.getServiceId());
        
        return Uni.createFrom().item(() -> {
            Instant now = Instant.now();
            Timestamp serverTime = Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
            
            // Update last seen time for the module
            ModuleInfo module = registeredModules.get(request.getServiceId());
            if (module != null) {
                // Could update last seen time in module metadata
                return HeartbeatAck.newBuilder()
                    .setAcknowledged(true)
                    .setServerTime(serverTime)
                    .setMessage("Heartbeat acknowledged")
                    .build();
            } else {
                return HeartbeatAck.newBuilder()
                    .setAcknowledged(false)
                    .setServerTime(serverTime)
                    .setMessage("Module not registered: " + request.getServiceId())
                    .build();
            }
        });
    }
    
    @Override
    public Uni<ModuleHealthStatus> getModuleHealth(ModuleId request) {
        LOG.debugf("🔍 Health check request for module: %s", request.getServiceId());
        
        ModuleInfo module = registeredModules.get(request.getServiceId());
        if (module == null) {
            Instant now = Instant.now();
            Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
                
            return Uni.createFrom().item(ModuleHealthStatus.newBuilder()
                .setServiceId(request.getServiceId())
                .setServiceName("unknown")
                .setIsHealthy(false)
                .setLastChecked(timestamp)
                .setHealthDetails("Module not found in registry")
                .build());
        }
        
        // Check actual health via Consul
        String consulServiceId = "grpc-" + request.getServiceId();
        return consulRegistry.checkServiceHealth(consulServiceId)
            .onItem().transform(isHealthy -> {
                Instant now = Instant.now();
                Timestamp timestamp = Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .setNanos(now.getNano())
                    .build();
                    
                return ModuleHealthStatus.newBuilder()
                    .setServiceId(request.getServiceId())
                    .setServiceName(module.getServiceName())
                    .setIsHealthy(isHealthy)
                    .setLastChecked(timestamp)
                    .setHealthDetails(isHealthy ? 
                        "Module is healthy according to Consul checks" : 
                        "Module is unhealthy or not responding to Consul checks")
                    .putAllMetadata(module.getMetadataMap())
                    .build();
            });
    }
    
    @Override
    public Uni<ModuleList> listModules(Empty request) {
        LOG.debug("📋 Listing all registered modules");
        
        return Uni.createFrom().item(() -> {
            Instant now = Instant.now();
            Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
            
            return ModuleList.newBuilder()
                .addAllModules(registeredModules.values())
                .setAsOf(timestamp)
                .build();
        });
    }
    
    /**
     * Authenticate a module before registration.
     * Validates: 1) Module is in whitelist, 2) Schema consistency if already registered
     */
    private Uni<AuthenticationResult> authenticateModule(ModuleInfo request) {
        LOG.debugf("🔐 Authenticating module: %s", request.getServiceName());
        
        // First check whitelist (registeredModules acts as our whitelist)
        // TODO: This should probably come from configuration, not runtime state
        boolean isWhitelisted = registeredModules.containsKey(request.getServiceId()) ||
                               isModuleTypeAllowed(request.getServiceName());
        
        if (!isWhitelisted) {
            LOG.warnf("❌ Module %s is not in the whitelist", request.getServiceName());
            return Uni.createFrom().item(AuthenticationResult.failure(
                String.format("Module %s is not allowed. Please contact administrators to whitelist this module type.",
                             request.getServiceName())
            ));
        }
        
        // Module is whitelisted, now check if it's already registered in Consul
        return consulRegistry.getExistingModule(request.getServiceName())
            .map(existingModuleOpt -> {
                if (existingModuleOpt.isEmpty()) {
                    // First time registration - no schema to compare
                    LOG.debugf("✅ Module %s is whitelisted and new, authentication passed", request.getServiceName());
                    return AuthenticationResult.success();
                }
                
                ModuleInfo existingModule = existingModuleOpt.get();
                
                // Module already exists in Consul - compare schemas
                String existingSchema = existingModule.getMetadataOrDefault("schema", "");
                String newSchema = request.getMetadataOrDefault("schema", "");
                
                // If both are empty or both are the same, it's ok
                if (existingSchema.isEmpty() && newSchema.isEmpty()) {
                    LOG.debugf("✅ Module %s re-registering with no schema changes (both empty)", request.getServiceName());
                    return AuthenticationResult.success();
                }
                
                if (existingSchema.equals(newSchema)) {
                    LOG.debugf("✅ Module %s re-registering with matching schema", request.getServiceName());
                    return AuthenticationResult.success();
                }
                
                // Schema mismatch - this is the incompatible version case
                LOG.warnf("❌ Module %s schema mismatch. Existing: %s, New: %s", 
                        request.getServiceName(), existingSchema, newSchema);
                
                return AuthenticationResult.failure(
                    String.format("Schema mismatch for module %s. The existing registration has a different schema. " +
                                 "This could break existing pipelines. To update the schema, please coordinate " +
                                 "with pipeline owners and update pipeline configurations.",
                                 request.getServiceName())
                );
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.errorf(throwable, "Failed to check existing module registration");
                // On error checking existing module, fail safe by rejecting
                return AuthenticationResult.failure("Failed to verify module: " + throwable.getMessage());
            });
    }
    
    /**
     * Check if a module type is in our allowed list
     * TODO: Move this to configuration
     */
    private boolean isModuleTypeAllowed(String moduleName) {
        // These are the known module types we allow
        return moduleName != null && (
            moduleName.equals("echo") ||
            moduleName.equals("chunker") ||
            moduleName.equals("parser") ||
            moduleName.equals("embedder") ||
            moduleName.equals("opensearch-sink") ||
            moduleName.equals("test-module")
        );
    }
    
    /**
     * Create a failure status with proper timestamp
     */
    private RegistrationStatus createFailureStatus(String message) {
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.newBuilder()
            .setSeconds(now.getEpochSecond())
            .setNanos(now.getNano())
            .build();
            
        return RegistrationStatus.newBuilder()
            .setSuccess(false)
            .setMessage(message)
            .setRegisteredAt(timestamp)
            .build();
    }
}