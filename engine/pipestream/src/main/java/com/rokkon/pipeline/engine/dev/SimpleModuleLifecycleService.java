package com.rokkon.pipeline.engine.dev;

import com.rokkon.pipeline.engine.dev.config.DevModeModuleConfig;
import com.rokkon.pipeline.engine.dev.PipelineModule;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module lifecycle service for dev mode.
 * Handles health monitoring and restart of failed modules.
 * Does NOT auto-deploy at startup - modules are restored from Consul.
 */
@ApplicationScoped
public class SimpleModuleLifecycleService {
    
    private static final Logger LOG = Logger.getLogger(SimpleModuleLifecycleService.class);
    
    @Inject
    DevModeModuleConfig config;
    
    @Inject
    Instance<ModuleDeploymentService> deploymentServiceInstance;
    
    @Inject
    LaunchMode launchMode;
    
    // Track deployed modules
    private final Set<String> deployedModules = ConcurrentHashMap.newKeySet();
    private volatile boolean active = false;
    
    /**
     * Initialize service on startup in dev mode
     */
    void onStart(@Observes StartupEvent ev) {
        if (!LaunchMode.current().isDevOrTest()) {
            LOG.debug("Module lifecycle service disabled - not in dev mode");
            return;
        }
        
        if (!deploymentServiceInstance.isResolvable()) {
            LOG.debug("Module lifecycle service disabled - deployment service not available");
            return;
        }
        
        if (!config.autoDeploy().enabled()) {
            LOG.info("Module lifecycle service disabled by configuration");
            return;
        }
        
        LOG.info("Module lifecycle service started in dev mode");
        active = true;
        
        // Don't auto-deploy at startup - modules should already be running
        // and registered in Consul. The engine will restore their state
        // from Consul via ConsulInitializer.
    }
    
    /**
     * Stop all modules on shutdown
     */
    void onStop(@Observes ShutdownEvent ev) {
        if (!LaunchMode.current().isDevOrTest()) {
            return;
        }
        
        active = false;
        LOG.info("Stopping all deployed modules");
        
        // Stop all deployed modules
        if (deploymentServiceInstance.isResolvable()) {
            ModuleDeploymentService deploymentService = deploymentServiceInstance.get();
            deployedModules.forEach(moduleName -> {
                try {
                    PipelineModule module = PipelineModule.valueOf(moduleName.toUpperCase());
                    deploymentService.stopModule(module);
                    LOG.infof("Stopped module: %s", moduleName);
                } catch (Exception e) {
                    LOG.warnf("Failed to stop module %s: %s", moduleName, e.getMessage());
                }
            });
        }
        
        deployedModules.clear();
    }
    
    
    /**
     * Deploy all configured modules
     */
    private void deployConfiguredModules() {
        if (!active) return;
        
        if (!deploymentServiceInstance.isResolvable()) {
            LOG.debug("Deployment service not available");
            return;
        }
        
        ModuleDeploymentService deploymentService = deploymentServiceInstance.get();
        
        config.autoDeploy().modules().forEach(moduleConfig -> {
            if (moduleConfig.enabled()) {
                String moduleName = moduleConfig.name();
                try {
                    PipelineModule module = PipelineModule.valueOf(moduleName.toUpperCase());
                    
                    LOG.infof("Deploying module %s with %d instances", 
                        moduleName, moduleConfig.instances());
                    
                    // Deploy first instance
                    ModuleDeploymentService.ModuleDeploymentResult result = 
                        deploymentService.deployModule(module);
                    
                    if (result.success()) {
                        deployedModules.add(moduleName);
                        LOG.infof("Successfully deployed %s: %s", 
                            moduleName, result.message());
                        
                        // Deploy additional instances if needed
                        for (int i = 1; i < moduleConfig.instances(); i++) {
                            result = deploymentService.deployAdditionalInstance(module);
                            if (!result.success()) {
                                LOG.warnf("Failed to deploy instance %d of %s: %s", 
                                    i + 1, moduleName, result.message());
                                break;
                            }
                        }
                    } else {
                        LOG.errorf("Failed to deploy module %s: %s", 
                            moduleName, result.message());
                    }
                } catch (IllegalArgumentException e) {
                    LOG.errorf("Unknown module: %s", moduleName);
                } catch (Exception e) {
                    LOG.errorf(e, "Unexpected error deploying module %s", moduleName);
                }
            }
        });
    }
    
    /**
     * Periodic health check
     */
    @Scheduled(every = "{pipeline.dev.modules.health.check-interval}", 
               delay = 30, delayUnit = java.util.concurrent.TimeUnit.SECONDS)
    void checkModuleHealth() {
        if (!active || !LaunchMode.current().isDevOrTest()) {
            return;
        }
        
        if (!deploymentServiceInstance.isResolvable()) {
            return;
        }
        
        if (!config.autoDeploy().restart().enabled()) {
            return;
        }
        
        LOG.debug("Checking module health");
        
        ModuleDeploymentService deploymentService = deploymentServiceInstance.get();
        
        deployedModules.forEach(moduleName -> {
            try {
                PipelineModule module = PipelineModule.valueOf(moduleName.toUpperCase());
                ModuleDeploymentService.ModuleStatus status = 
                    deploymentService.getModuleStatus(module);
                
                if (status == ModuleDeploymentService.ModuleStatus.STOPPED) {
                    
                    LOG.warnf("Module %s is not running (status: %s), attempting restart", 
                        moduleName, status);
                    
                    // Try to redeploy
                    ModuleDeploymentService.ModuleDeploymentResult result = 
                        deploymentService.deployModule(module);
                    
                    if (result.success()) {
                        LOG.infof("Successfully restarted module %s", moduleName);
                    } else {
                        LOG.errorf("Failed to restart module %s: %s", 
                            moduleName, result.message());
                    }
                }
            } catch (Exception e) {
                LOG.errorf(e, "Error checking health of module %s", moduleName);
            }
        });
    }
    
    /**
     * Get module statuses for monitoring
     */
    public Map<String, String> getModuleStatuses() {
        Map<String, String> statuses = new HashMap<>();
        
        if (!deploymentServiceInstance.isResolvable()) {
            statuses.put("status", "Deployment service not available");
            return statuses;
        }
        
        ModuleDeploymentService deploymentService = deploymentServiceInstance.get();
        deployedModules.forEach(moduleName -> {
            try {
                PipelineModule module = PipelineModule.valueOf(moduleName.toUpperCase());
                ModuleDeploymentService.ModuleStatus status = 
                    deploymentService.getModuleStatus(module);
                statuses.put(moduleName, status.toString());
            } catch (Exception e) {
                statuses.put(moduleName, "ERROR: " + e.getMessage());
            }
        });
        
        return statuses;
    }
}