package com.rokkon.cli.service;

import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ServiceRegistrationData;
import com.rokkon.search.grpc.ModuleRegistration;
import com.rokkon.search.grpc.ModuleInfo;
import com.rokkon.search.grpc.RegistrationStatus;
import com.rokkon.search.grpc.MutinyModuleRegistrationGrpc;
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
    
    @Inject
    ModuleHealthService healthService;
    
    public Uni<Boolean> registerModule(
            String moduleHost, 
            int modulePort,
            String engineHost,
            int enginePort,
            String consulHost,
            int consulPort,
            boolean performHealthCheck) {
        
        LOG.infof("Starting registration process for module at %s:%d", moduleHost, modulePort);
        
        // Step 1: Connect to module and get registration info
        return getModuleInfo(moduleHost, modulePort)
            .onItem().transformToUni(moduleInfo -> {
                LOG.infof("Retrieved module info: %s", moduleInfo.getModuleName());
                
                // Step 2: Optionally perform health check
                if (performHealthCheck) {
                    return healthService.checkModuleHealth(moduleHost, modulePort)
                        .onItem().transformToUni(healthy -> {
                            if (!healthy) {
                                return Uni.createFrom().failure(
                                    new RuntimeException("Module health check failed")
                                );
                            }
                            return Uni.createFrom().item(moduleInfo);
                        });
                } else {
                    return Uni.createFrom().item(moduleInfo);
                }
            })
            .onItem().transformToUni(moduleInfo -> {
                // Step 3: Register with engine via gRPC
                return registerWithEngine(
                    moduleInfo,
                    moduleHost,
                    modulePort,
                    engineHost,
                    enginePort,
                    consulHost,
                    consulPort
                );
            })
            .onFailure().recoverWithItem(error -> {
                LOG.errorf(error, "Registration failed");
                return false;
            });
    }
    
    private Uni<ServiceRegistrationData> getModuleInfo(String host, int port) {
        return Uni.createFrom().item(() -> {
            ManagedChannel channel = null;
            try {
                LOG.debugf("Connecting to module at %s:%d", host, port);
                channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .build();
                
                var stub = com.rokkon.search.sdk.MutinyPipeStepProcessorGrpc.newMutinyStub(channel);
                ServiceRegistrationData data = stub.getServiceRegistration(Empty.getDefaultInstance())
                    .await().atMost(Duration.ofSeconds(10));
                
                LOG.debugf("Successfully retrieved module info: %s", data.getModuleName());
                return data;
                
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
            ServiceRegistrationData moduleData,
            String moduleHost,
            int modulePort,
            String engineHost,
            int enginePort,
            String consulHost,
            int consulPort) {
        
        return Uni.createFrom().item(() -> {
            ManagedChannel channel = null;
            try {
                LOG.infof("Connecting to engine at %s:%d for registration", engineHost, enginePort);
                channel = ManagedChannelBuilder.forAddress(engineHost, enginePort)
                    .usePlaintext()
                    .build();
                
                var stub = MutinyModuleRegistrationGrpc.newMutinyStub(channel);
                
                // Build metadata
                Map<String, String> metadata = new HashMap<>();
                metadata.put("cli_version", "1.0.0");
                metadata.put("registered_by", "rokkon-cli");
                
                // Store engine connection info if different from Consul registration
                if (!moduleHost.equals(consulHost) || modulePort != consulPort) {
                    metadata.put("engine_host", moduleHost);
                    metadata.put("engine_port", String.valueOf(modulePort));
                }
                
                // Add schema if present
                if (moduleData.hasJsonConfigSchema()) {
                    metadata.put("json_schema", moduleData.getJsonConfigSchema());
                }
                
                // Create module info for registration
                ModuleInfo moduleInfo = ModuleInfo.newBuilder()
                    .setServiceName(moduleData.getModuleName())
                    .setServiceId(moduleData.getModuleName() + "-" + UUID.randomUUID().toString().substring(0, 8))
                    .setHost(consulHost)
                    .setPort(consulPort)
                    .setHealthEndpoint("/grpc.health.v1.Health/Check")
                    .putAllMetadata(metadata)
                    .addTags("module")
                    .addTags("grpc")
                    .build();
                
                LOG.infof("Registering module '%s' with engine", moduleData.getModuleName());
                
                RegistrationStatus status = stub.registerModule(moduleInfo)
                    .await().atMost(Duration.ofSeconds(30));
                
                if (status.getSuccess()) {
                    LOG.infof("Registration successful: %s (Consul ID: %s)", 
                             status.getMessage(), status.getConsulServiceId());
                    return true;
                } else {
                    LOG.errorf("Registration failed: %s", status.getMessage());
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