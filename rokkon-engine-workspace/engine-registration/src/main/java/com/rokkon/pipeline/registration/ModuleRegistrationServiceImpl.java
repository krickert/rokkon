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
    
    @Override
    public Uni<RegistrationStatus> registerModule(ModuleInfo request) {
        LOG.infof("📥 Module registration request: %s at %s:%d", 
                request.getServiceName(), request.getHost(), request.getPort());
        
        // Register with Consul first, then handle success/failure
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
                    
                    RegistrationStatus response = RegistrationStatus.newBuilder()
                        .setSuccess(true)
                        .setMessage(String.format("Module '%s' successfully registered with Consul", request.getServiceName()))
                        .setRegisteredAt(timestamp)
                        .setConsulServiceId(consulServiceId)
                        .build();
                    
                    // Fire registration event  
                    eventPublisher.fire(new ModuleRegistrationEvent(
                        "REGISTERED", 
                        request.getServiceName(),
                        request.getServiceId(), 
                        consulServiceId
                    ));
                    
                    LOG.infof("✅ Module registration successful: %s -> %s", 
                            request.getServiceName(), consulServiceId);
                            
                    return response;
                    
                } catch (Exception e) {
                    LOG.errorf(e, "❌ Module registration failed after Consul success: %s", request.getServiceName());
                    throw new RuntimeException("Post-registration processing failed", e);
                }
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
}