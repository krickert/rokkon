package com.rokkon.pipeline.engine.grpc;

import com.rokkon.search.registration.api.*;
import com.rokkon.pipeline.events.ModuleRegistrationRequestEvent;
import com.rokkon.pipeline.events.ModuleRegistrationResponseEvent;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.HashMap;

/**
 * gRPC service for module registration.
 * Delegates to GlobalModuleRegistryService via CDI events.
 */
@GrpcService
@Singleton
public class ModuleRegistrationServiceImpl extends MutinyModuleRegistrationServiceGrpc.ModuleRegistrationServiceImplBase {
    
    private static final Logger LOG = Logger.getLogger(ModuleRegistrationServiceImpl.class);
    
    @ConfigProperty(name = "engine.host", defaultValue = "localhost")
    String engineHost;
    
    @ConfigProperty(name = "quarkus.http.port", defaultValue = "38082")
    int enginePort;
    
    @Inject
    Event<ModuleRegistrationRequestEvent> registrationRequestEvent;
    
    // Store pending requests so we can correlate responses
    private final Map<String, io.smallrye.mutiny.subscription.UniEmitter<? super RegisterModuleResponse>> emitterMap = new ConcurrentHashMap<>();
    
    @Override
    public Uni<RegisterModuleResponse> registerModule(RegisterModuleRequest request) {
        LOG.infof("Received gRPC module registration request: %s", request.getImplementationId());
        
        return Uni.createFrom().emitter(emitter -> {
            String requestId = UUID.randomUUID().toString();
            
            // Store the emitter so we can complete it when we get the response event
            emitterMap.put(requestId, emitter);
            
            // Convert additional_tags to metadata
            Map<String, String> metadata = new HashMap<>(request.getAdditionalTagsMap());
            
            // Add version if provided
            if (request.hasModuleSoftwareVersion()) {
                metadata.put("version", request.getModuleSoftwareVersion());
            }
            
            // Fire the event to be handled by GlobalModuleRegistryService
            ModuleRegistrationRequestEvent event = ModuleRegistrationRequestEvent.create(
                request.getInstanceServiceName(),
                request.getImplementationId(),
                request.getHost(),
                request.getPort(),
                request.getHealthCheckType().name(),
                metadata.getOrDefault("version", "1.0.0"),
                metadata,
                engineHost,
                enginePort,
                request.getInstanceCustomConfigJson(),
                requestId
            );
            
            registrationRequestEvent.fire(event);
            
            // Set a timeout in case we don't get a response
            emitter.onTermination(() -> emitterMap.remove(requestId));
        });
    }
    
    /**
     * Handles registration response events from GlobalModuleRegistryService.
     */
    public void handleRegistrationResponse(@Observes ModuleRegistrationResponseEvent event) {
        LOG.debugf("Received registration response event for request: %s", event.requestId());
        
        var emitter = emitterMap.remove(event.requestId());
        if (emitter != null) {
            RegisterModuleResponse response = RegisterModuleResponse.newBuilder()
                .setSuccess(event.success())
                .setMessage(event.message())
                .setRegisteredServiceId(event.moduleId() != null ? event.moduleId() : "")
                .build();
            emitter.complete(response);
        }
    }
}