package com.rokkon.engine.grpc;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import com.rokkon.search.grpc.*;
import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService;
import io.vertx.ext.consul.CheckStatus;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.annotation.PostConstruct;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

/**
 * gRPC service implementation for module registration.
 * This service directly uses GlobalModuleRegistryService instead of events.
 */
@GrpcService
@Singleton
public class ModuleRegistrationServiceImpl extends MutinyModuleRegistrationGrpc.ModuleRegistrationImplBase {
    
    private static final Logger LOG = Logger.getLogger(ModuleRegistrationServiceImpl.class);
    
    @Inject
    GlobalModuleRegistryService registryService;
    
    @PostConstruct
    void init() {
        LOG.info("ModuleRegistrationServiceImpl gRPC service initialized");
    }
    
    @Override
    public Uni<RegistrationStatus> registerModule(ModuleInfo request) {
        LOG.infof("📥 Module registration request via gRPC: %s at %s:%d", 
                request.getServiceName(), request.getHost(), request.getPort());
        
        // Extract metadata from the request
        Map<String, String> metadata = new HashMap<>(request.getMetadataMap());
        
        // Extract version from metadata or use NO_VERSION
        String version = metadata.getOrDefault("version", "NO_VERSION");
        
        // Generate implementation ID if not provided
        String implementationId = request.getServiceId();
        if (implementationId == null || implementationId.isEmpty()) {
            implementationId = request.getServiceName() + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
        
        // Extract engine connection info from metadata or use module's host/port
        String engineHost = metadata.getOrDefault("engineHost", request.getHost());
        int enginePort = request.getPort(); // Default to module port if enginePort not in metadata
        if (metadata.containsKey("enginePort")) {
            try {
                enginePort = Integer.parseInt(metadata.get("enginePort"));
            } catch (NumberFormatException e) {
                LOG.warnf("Invalid enginePort in metadata: %s, using module port", metadata.get("enginePort"));
            }
        }
        
        // Extract JSON schema from metadata if present
        String jsonSchema = metadata.get("jsonSchema");
        
        // Call GlobalModuleRegistryService directly with all required parameters
        return registryService.registerModule(
            request.getServiceName(),
            implementationId,
            request.getHost(),
            request.getPort(),
            "GRPC", // serviceType
            version,
            metadata,
            engineHost,
            enginePort,
            jsonSchema
        ).map(registration -> {
            Instant now = Instant.now();
            Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
                
            LOG.infof("✅ Module registered successfully: %s -> %s", 
                     registration.moduleName(), registration.moduleId());
                
            return RegistrationStatus.newBuilder()
                .setSuccess(true)
                .setMessage("Module registered successfully")
                .setRegisteredAt(timestamp)
                .setConsulServiceId(registration.moduleId())
                .build();
        })
        .onFailure().recoverWithItem(error -> {
            LOG.errorf(error, "Failed to register module: %s", request.getServiceName());
            Instant now = Instant.now();
            Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
                
            return RegistrationStatus.newBuilder()
                .setSuccess(false)
                .setMessage("Registration failed: " + error.getMessage())
                .setRegisteredAt(timestamp)
                .setConsulServiceId("")
                .build();
        });
    }
    
    @Override
    public Uni<UnregistrationStatus> unregisterModule(ModuleId request) {
        LOG.infof("🗑️ Module unregistration request: %s", request.getServiceId());
        
        return registryService.deregisterModule(request.getServiceId())
            .map(success -> {
                Instant now = Instant.now();
                Timestamp timestamp = Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .setNanos(now.getNano())
                    .build();
                    
                if (success) {
                    return UnregistrationStatus.newBuilder()
                        .setSuccess(true)
                        .setMessage("Module unregistered successfully")
                        .setUnregisteredAt(timestamp)
                        .build();
                } else {
                    return UnregistrationStatus.newBuilder()
                        .setSuccess(false)
                        .setMessage("Failed to unregister module")
                        .setUnregisteredAt(timestamp)
                        .build();
                }
            })
            .onFailure().recoverWithItem(error -> {
                LOG.errorf(error, "Failed to unregister module: %s", request.getServiceId());
                Instant now = Instant.now();
                Timestamp timestamp = Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .setNanos(now.getNano())
                    .build();
                    
                return UnregistrationStatus.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error: " + error.getMessage())
                    .setUnregisteredAt(timestamp)
                    .build();
            });
    }
    
    @Override
    public Uni<HeartbeatAck> heartbeat(ModuleHeartbeat request) {
        LOG.debugf("💗 Heartbeat from module: %s", request.getServiceId());
        
        // For now, just acknowledge the heartbeat
        // In the future, this could update last-seen timestamps
        Instant now = Instant.now();
        Timestamp serverTime = Timestamp.newBuilder()
            .setSeconds(now.getEpochSecond())
            .setNanos(now.getNano())
            .build();
            
        return Uni.createFrom().item(HeartbeatAck.newBuilder()
            .setAcknowledged(true)
            .setServerTime(serverTime)
            .setMessage("Heartbeat acknowledged")
            .build());
    }
    
    @Override
    public Uni<ModuleHealthStatus> getModuleHealth(ModuleId request) {
        LOG.debugf("🔍 Health check request for module: %s", request.getServiceId());
        
        // Use actual Consul health checks
        return registryService.getModuleHealthStatus(request.getServiceId())
            .map(healthStatus -> {
                GlobalModuleRegistryService.ModuleRegistration module = healthStatus.module();
                boolean isHealthy = healthStatus.exists() && healthStatus.healthStatus() == GlobalModuleRegistryService.HealthStatus.PASSING;
                
                String healthDetails;
                if (!healthStatus.exists()) {
                    healthDetails = "Module not found in Consul health checks";
                } else {
                    switch (healthStatus.healthStatus()) {
                        case PASSING:
                            healthDetails = "All health checks passing";
                            break;
                        case WARNING:
                            healthDetails = "Health check warnings detected";
                            break;
                        case CRITICAL:
                            healthDetails = "Health check critical - module may be down";
                            break;
                        default:
                            healthDetails = "Unknown health status";
                    }
                }
                
                Instant now = Instant.now();
                Timestamp timestamp = Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .setNanos(now.getNano())
                    .build();
                    
                return ModuleHealthStatus.newBuilder()
                    .setServiceId(module.moduleId())
                    .setServiceName(module.moduleName())
                    .setIsHealthy(isHealthy)
                    .setLastChecked(timestamp)
                    .setHealthDetails(healthDetails)
                    .putAllMetadata(module.metadata() != null ? module.metadata() : Map.of())
                    .build();
            })
            .onFailure().recoverWithItem(error -> {
                LOG.debugf(error, "Module not found: %s", request.getServiceId());
                Instant now = Instant.now();
                Timestamp timestamp = Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .setNanos(now.getNano())
                    .build();
                    
                return ModuleHealthStatus.newBuilder()
                    .setServiceId(request.getServiceId())
                    .setServiceName("unknown")
                    .setIsHealthy(false)
                    .setLastChecked(timestamp)
                    .setHealthDetails("Module not found: " + error.getMessage())
                    .build();
            });
    }
    
    @Override
    public Uni<ModuleList> listModules(Empty request) {
        LOG.debug("📋 Listing all registered modules");
        
        return registryService.listRegisteredModules()
            .map(modules -> {
                List<ModuleInfo> moduleInfos = modules.stream()
                    .map(this::toModuleInfo)
                    .collect(Collectors.toList());
                    
                Instant now = Instant.now();
                Timestamp timestamp = Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .setNanos(now.getNano())
                    .build();
                
                return ModuleList.newBuilder()
                    .addAllModules(moduleInfos)
                    .setAsOf(timestamp)
                    .build();
            });
    }
    
    private ModuleInfo toModuleInfo(GlobalModuleRegistryService.ModuleRegistration registration) {
        ModuleInfo.Builder builder = ModuleInfo.newBuilder()
            .setServiceName(registration.moduleName())
            .setServiceId(registration.moduleId())
            .setHost(registration.host())
            .setPort(registration.port());
            
        if (registration.metadata() != null) {
            builder.putAllMetadata(registration.metadata());
        }
        
        return builder.build();
    }
}