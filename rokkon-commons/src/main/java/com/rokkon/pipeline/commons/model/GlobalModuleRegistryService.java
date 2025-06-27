package com.rokkon.pipeline.commons.model;

import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service interface for managing global module registrations.
 * Modules are registered globally and can be referenced by clusters.
 */
public interface GlobalModuleRegistryService {

    /**
     * Health status enum for module health checks
     */
    enum HealthStatus {
        PASSING,
        WARNING,
        CRITICAL;
    }

    /**
     * Module registration data stored globally
     */
    record ModuleRegistration(
        String moduleId,
        String moduleName,
        String implementationId,
        String host,
        int port,
        String serviceType,
        String version,
        Map<String, String> metadata,
        long registeredAt,
        String engineHost,
        int enginePort,
        String jsonSchema,  // Optional JSON schema for validation
        boolean enabled,    // Whether the module is enabled or disabled
        String containerId, // Docker container ID (if available)
        String containerName, // Docker container name (if available)
        String hostname     // Container hostname (if available)
    ) {}

    /**
     * Result of zombie cleanup operation
     */
    record ZombieCleanupResult(
        int zombiesDetected,
        int zombiesCleaned,
        List<String> errors
    ) {}

    /**
     * Service health status record
     */
    record ServiceHealthStatus(
        ModuleRegistration module,
        HealthStatus healthStatus,
        boolean exists
    ) {
        public boolean isZombie() {
            // Consider as zombie if:
            // 1. Service doesn't exist in Consul health checks
            // 2. Health status is critical (failing for extended period)
            return !exists || healthStatus == HealthStatus.CRITICAL;
        }
    }

    /**
     * Register a module globally.
     * This creates both a service entry and stores metadata in KV.
     */
    Uni<ModuleRegistration> registerModule(
            String moduleName,
            String implementationId,
            String host,
            int port,
            String serviceType,
            String version,
            Map<String, String> metadata,
            String engineHost,
            int enginePort,
            String jsonSchema);

    /**
     * List all globally registered modules as an ordered set (no duplicates)
     * This includes both enabled and disabled modules
     */
    Uni<Set<ModuleRegistration>> listRegisteredModules();

    /**
     * List only enabled modules
     */
    Uni<Set<ModuleRegistration>> listEnabledModules();

    /**
     * Get a specific module by ID
     */
    Uni<ModuleRegistration> getModule(String moduleId);

    /**
     * Disable a module (sets enabled=false)
     */
    Uni<Boolean> disableModule(String moduleId);

    /**
     * Enable a module (sets enabled=true)
     */
    Uni<Boolean> enableModule(String moduleId);

    /**
     * Deregister a module (hard delete - removes from registry completely)
     */
    Uni<Boolean> deregisterModule(String moduleId);

    /**
     * Enable a module for a specific cluster
     */
    Uni<Void> enableModuleForCluster(String moduleId, String clusterName);

    /**
     * Disable a module for a specific cluster
     */
    Uni<Void> disableModuleForCluster(String moduleId, String clusterName);

    /**
     * List modules enabled for a specific cluster as an ordered set
     */
    Uni<Set<String>> listEnabledModulesForCluster(String clusterName);

    /**
     * Archive a service by moving it from active to archive namespace
     */
    Uni<Boolean> archiveService(String serviceName, String reason);

    /**
     * Clean up zombie instances - modules that are failing health checks or no longer exist
     */
    Uni<ZombieCleanupResult> cleanupZombieInstances();

    /**
     * Public method to check module health
     * @param moduleId The module ID to check
     * @return Health status of the module
     */
    Uni<ServiceHealthStatus> getModuleHealthStatus(String moduleId);

    /**
     * Clean up stale entries in the whitelist (modules registered but not in Consul)
     */
    Uni<Integer> cleanupStaleWhitelistedModules();
}
