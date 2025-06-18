package com.rokkon.pipeline.registration;

import com.google.protobuf.Empty;
import com.rokkon.search.grpc.*;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.PipeStepProcessorClient;
import com.rokkon.search.sdk.ServiceRegistrationData;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;

/**
 * CLI Registration Client Stub - Ready for CLI implementation
 * 
 * This class provides the foundation for the CLI that will:
 * 1. Discover deployed services (Docker containers, K8s pods, etc.)
 * 2. Query services for their registration data
 * 3. Register services with the engine
 * 4. Handle service lifecycle events
 * 
 * For future CLI implementation in any language (Java, Go, Python, etc.)
 */
public class RegistrationClientStub {
    
    private static final Logger LOG = Logger.getLogger(RegistrationClientStub.class);
    
    private final String engineHost;
    private final int enginePort;
    private ManagedChannel engineChannel;
    private ModuleRegistration engineRegistrationClient;
    
    public RegistrationClientStub(String engineHost, int enginePort) {
        this.engineHost = engineHost;
        this.enginePort = enginePort;
        this.initializeEngineConnection();
    }
    
    private void initializeEngineConnection() {
        this.engineChannel = ManagedChannelBuilder
                .forAddress(engineHost, enginePort)
                .usePlaintext()
                .build();
        
        this.engineRegistrationClient = new ModuleRegistrationClient(
                "engineRegistration", engineChannel, (name, stub) -> stub);
        
        LOG.infof("🔗 CLI connected to engine at %s:%d", engineHost, enginePort);
    }
    
    /**
     * CLI Discovery Phase: Query a deployed service for its registration data
     * This will be called for each discovered service (Docker container, etc.)
     */
    public Uni<ServiceRegistrationData> discoverService(String serviceHost, int servicePort) {
        LOG.infof("🔍 CLI discovering service at %s:%d", serviceHost, servicePort);
        
        ManagedChannel serviceChannel = ManagedChannelBuilder
                .forAddress(serviceHost, servicePort)
                .usePlaintext()
                .build();
        
        PipeStepProcessor serviceClient = new PipeStepProcessorClient(
                "discoveredService", serviceChannel, (name, stub) -> stub);
        
        return serviceClient.getServiceRegistration(Empty.newBuilder().build())
                .onItem().invoke(serviceData -> {
                    LOG.infof("✅ CLI discovered service: %s", 
                            serviceData.getModuleName());
                })
                .onFailure().invoke(failure -> {
                    LOG.errorf(failure, "❌ CLI failed to discover service at %s:%d", serviceHost, servicePort);
                    serviceChannel.shutdown();
                })
                .onTermination().invoke(() -> serviceChannel.shutdown());
    }
    
    /**
     * CLI Registration Phase: Register a discovered service with the engine
     * This simulates what the CLI will do in production deployments
     */
    public Uni<RegistrationStatus> registerDiscoveredService(
            ServiceRegistrationData serviceData, 
            String serviceHost, 
            int servicePort,
            String deploymentId) {
        
        LOG.infof("📝 CLI registering service: %s from deployment %s", 
                serviceData.getModuleName(), deploymentId);
        
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
                .setServiceName(serviceData.getModuleName())
                .setServiceId(deploymentId + "-" + System.currentTimeMillis())
                .setHost(serviceHost)
                .setPort(servicePort)
                .setHealthEndpoint("/health")
                .putMetadata("module_name", serviceData.getModuleName())
                .putMetadata("json_schema", serviceData.getJsonConfigSchema())
                .putMetadata("discovered_by", "cli")
                .putMetadata("deployment_id", deploymentId)
                .addAllTags(java.util.List.of("cli-discovered", "auto-registered"))
                .build();
        
        return engineRegistrationClient.registerModule(moduleInfo)
                .onItem().invoke(status -> {
                    if (status.getSuccess()) {
                        LOG.infof("✅ CLI registered service successfully: %s -> %s", 
                                serviceData.getModuleName(), status.getConsulServiceId());
                    } else {
                        LOG.errorf("❌ CLI registration failed: %s", status.getMessage());
                    }
                });
    }
    
    /**
     * CLI Management: List all registered modules
     */
    public Uni<ModuleList> listRegisteredModules() {
        LOG.debug("📋 CLI requesting list of registered modules");
        
        return engineRegistrationClient.listModules(Empty.newBuilder().build())
                .onItem().invoke(moduleList -> {
                    LOG.infof("📋 CLI found %d registered modules", moduleList.getModulesCount());
                });
    }
    
    /**
     * CLI Management: Check health of a specific module
     */
    public Uni<ModuleHealthStatus> checkModuleHealth(String serviceId) {
        LOG.infof("🔍 CLI checking health of module: %s", serviceId);
        
        ModuleId moduleId = ModuleId.newBuilder()
                .setServiceId(serviceId)
                .build();
        
        return engineRegistrationClient.getModuleHealth(moduleId)
                .onItem().invoke(health -> {
                    LOG.infof("🔍 CLI health check result for %s: %s", 
                            serviceId, health.getIsHealthy() ? "HEALTHY" : "UNHEALTHY");
                });
    }
    
    /**
     * CLI Cleanup: Unregister a service (when container/pod is terminated)
     */
    public Uni<UnregistrationStatus> unregisterService(String serviceId) {
        LOG.infof("🗑️ CLI unregistering service: %s", serviceId);
        
        ModuleId moduleId = ModuleId.newBuilder()
                .setServiceId(serviceId)
                .build();
        
        return engineRegistrationClient.unregisterModule(moduleId)
                .onItem().invoke(status -> {
                    if (status.getSuccess()) {
                        LOG.infof("✅ CLI unregistered service successfully: %s", serviceId);
                    } else {
                        LOG.errorf("❌ CLI unregistration failed: %s", status.getMessage());
                    }
                });
    }
    
    /**
     * CLI Full Discovery and Registration Flow
     * This simulates the complete CLI workflow for a new deployment
     */
    public Uni<RegistrationStatus> discoverAndRegister(String serviceHost, int servicePort, String deploymentId) {
        LOG.infof("🚀 CLI starting full discovery and registration for deployment: %s", deploymentId);
        
        return discoverService(serviceHost, servicePort)
                .flatMap(serviceData -> registerDiscoveredService(serviceData, serviceHost, servicePort, deploymentId))
                .onItem().invoke(status -> {
                    LOG.infof("🎯 CLI completed discovery and registration for %s: %s", 
                            deploymentId, status.getSuccess() ? "SUCCESS" : "FAILED");
                });
    }
    
    /**
     * CLI Shutdown
     */
    public void shutdown() {
        if (engineChannel != null) {
            engineChannel.shutdown();
            LOG.info("🔌 CLI disconnected from engine");
        }
    }
}