package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.Service;
import io.vertx.ext.consul.ServiceOptions;
import io.vertx.ext.consul.CheckOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.time.Duration;

/**
 * Service for managing global module registrations in Consul.
 * Modules are registered globally and can be referenced by clusters.
 */
@ApplicationScoped
public class GlobalModuleRegistryService {
    
    private static final Logger LOG = Logger.getLogger(GlobalModuleRegistryService.class);
    private static final String MODULE_KV_PREFIX = "rokkon/modules/registered/";
    private static final String ARCHIVE_PREFIX = "rokkon/archive";
    
    @Inject
    ConsulConnectionManager connectionManager;
    
    @Inject
    ObjectMapper objectMapper;
    
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    
    /**
     * Module registration data stored globally
     */
    public record ModuleRegistration(
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
        String jsonSchema  // Optional JSON schema for validation
    ) implements Comparable<ModuleRegistration> {
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ModuleRegistration other)) return false;
            // Two registrations are equal if they have the same module name
            // This prevents duplicate module names in sets
            return Objects.equals(moduleName, other.moduleName);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(moduleName);
        }
        
        @Override
        public int compareTo(ModuleRegistration other) {
            // Sort by module name first, then by registration time
            int nameCompare = this.moduleName.compareTo(other.moduleName);
            if (nameCompare != 0) return nameCompare;
            return Long.compare(this.registeredAt, other.registeredAt);
        }
    }
    
    /**
     * Register a module globally in Consul.
     * This creates both a service entry and stores metadata in KV.
     */
    public Uni<ModuleRegistration> registerModule(
            String moduleName,
            String implementationId,
            String host,
            int port,
            String serviceType,
            String version,
            Map<String, String> metadata,
            String engineHost,
            int enginePort,
            String jsonSchema) {
        
        ConsulClient client = getConsulClient();
        String moduleId = generateModuleId(moduleName);
        
        LOG.infof("Registering module globally: %s (%s) at %s:%d (engine connection: %s:%d)", 
                  moduleName, implementationId, host, port, engineHost, enginePort);
        
        // Validate JSON Schema v7 if provided
        if (jsonSchema != null && !jsonSchema.trim().isEmpty()) {
            if (!isValidJsonSchemaV7(jsonSchema)) {
                throw new IllegalArgumentException(
                    String.format("Invalid JSON Schema v7 provided for module '%s'", moduleName)
                );
            }
        }
        
        // First check if this module is already registered
        return listRegisteredModules()
            .onItem().transformToUni(existingModules -> {
                // Create a test registration to check against the set
                ModuleRegistration testRegistration = new ModuleRegistration(
                    "", moduleName, "", "", 0, "", "", Map.of(), 0, "", 0, null
                );
                
                // Since equals() is based on moduleName, this will check if any module with this name exists
                if (existingModules.contains(testRegistration)) {
                    // Find the existing registration for logging
                    ModuleRegistration existing = existingModules.stream()
                        .filter(m -> m.moduleName().equals(moduleName))
                        .findFirst()
                        .orElse(null);
                    
                    LOG.warnf("Module %s is already registered. Existing registration: %s", 
                             moduleName, existing);
                    
                    // Check if schema has changed - use proper JSON comparison, not string comparison
                    if (existing != null && jsonSchema != null && existing.jsonSchema() != null) {
                        if (!areJsonSchemasEquivalent(existing.jsonSchema(), jsonSchema)) {
                            LOG.errorf("Module %s schema mismatch detected", moduleName);
                            throw new IllegalArgumentException(
                                String.format("Schema mismatch for module '%s'. The provided schema does not match the registered schema.", 
                                            moduleName)
                            );
                        }
                    }
                    
                    return Uni.createFrom().failure(new WebApplicationException(
                        String.format("Module '%s' is already registered. Please deregister it first before re-registering.", moduleName),
                        Response.Status.CONFLICT
                    ));
                }
                
                // If not registered, validate the module is accessible using engine connection
                return validateModuleConnection(engineHost, enginePort, moduleName);
            })
            .onItem().transformToUni(valid -> {
                if (!valid) {
                    return Uni.createFrom().failure(new WebApplicationException(
                        String.format("Cannot connect to module %s at %s:%d", moduleName, host, port),
                        Response.Status.BAD_REQUEST
                    ));
                }
                
                // Create the module registration record
                ModuleRegistration registration = new ModuleRegistration(
                    moduleId,
                    moduleName,
                    implementationId,
                    host,
                    port,
                    serviceType,
                    version,
                    metadata != null ? metadata : Map.of(),
                    System.currentTimeMillis(),
                    engineHost,
                    enginePort,
                    jsonSchema
                );
                
                // Create service options for Consul
                Map<String, String> serviceMeta = new java.util.HashMap<>();
                serviceMeta.put("moduleName", moduleName);
                serviceMeta.put("implementationId", implementationId);
                serviceMeta.put("serviceType", serviceType);
                serviceMeta.put("version", version);
                serviceMeta.put("registeredAt", String.valueOf(registration.registeredAt()));
                // serviceMeta.put("visibility", "PUBLIC"); // Removed - all modules are public now
                
                // Store JSON schema if provided
                if (jsonSchema != null && !jsonSchema.trim().isEmpty()) {
                    serviceMeta.put("jsonSchema", jsonSchema);
                }
                
                // Store engine connection info if different from Consul registration
                if (!engineHost.equals(host) || enginePort != port) {
                    serviceMeta.put("engineHost", engineHost);
                    serviceMeta.put("enginePort", String.valueOf(enginePort));
                }
                
                ServiceOptions serviceOptions = new ServiceOptions()
                    .setId(moduleId)
                    .setName("module-" + moduleName)
                    .setTags(List.of("module", "global", serviceType, "version:" + version))
                    .setAddress(host)
                    .setPort(port)
                    .setMeta(serviceMeta);
                
                // Add gRPC health check
                CheckOptions checkOptions = new CheckOptions()
                    .setName("Module gRPC Health Check")
                    .setGrpc(host + ":" + port)
                    .setGrpcTls(false)
                    .setInterval("10s");
                
                serviceOptions.setCheckOptions(checkOptions);
                
                // Register the service
                return Uni.createFrom().completionStage(
                    client.registerService(serviceOptions).toCompletionStage()
                )
                .onItem().transformToUni(v -> {
                    // Store additional metadata in KV
                    return storeModuleMetadata(registration);
                })
                .onItem().transform(v -> registration);
            })
            .onFailure().transform(t -> {
                LOG.errorf(t, "Failed to register module %s", moduleName);
                return new WebApplicationException(
                    "Failed to register module: " + t.getMessage(),
                    Response.Status.INTERNAL_SERVER_ERROR
                );
            });
    }
    
    /**
     * List all globally registered modules as an ordered set (no duplicates)
     */
    public Uni<Set<ModuleRegistration>> listRegisteredModules() {
        ConsulClient client = getConsulClient();
        
        return Uni.createFrom().completionStage(
            client.localServices().toCompletionStage()
        )
        .onItem().transformToUni(services -> {
            // Filter for module services  
            List<Service> moduleServices = services.stream()
                .filter(service -> service.getName().startsWith("module-"))
                .toList();
            
            // Convert to ModuleRegistration objects
            List<Uni<ModuleRegistration>> registrationUnis = moduleServices.stream()
                .map(this::serviceToModuleRegistration)
                .toList();
            
            if (registrationUnis.isEmpty()) {
                return Uni.createFrom().item(new LinkedHashSet<>());
            }
            
            return Uni.combine().all().unis(registrationUnis)
                .with(list -> {
                    // Use LinkedHashSet to maintain order and prevent duplicates
                    Set<ModuleRegistration> registrations = new LinkedHashSet<>();
                    list.forEach(obj -> registrations.add((ModuleRegistration) obj));
                    return registrations;
                });
        });
    }
    
    /**
     * Get a specific module by ID
     */
    public Uni<ModuleRegistration> getModule(String moduleId) {
        ConsulClient client = getConsulClient();
        
        return Uni.createFrom().completionStage(
            client.localServices().toCompletionStage()
        )
        .onItem().transform(services -> {
            return services.stream()
                .filter(service -> service.getId().equals(moduleId))
                .findFirst()
                .orElse(null);
        })
        .onItem().transformToUni(service -> {
            if (service == null) {
                return Uni.createFrom().nullItem();
            }
            return serviceToModuleRegistration(service);
        });
    }
    
    /**
     * Deregister a module
     */
    public Uni<Boolean> deregisterModule(String moduleId) {
        ConsulClient client = getConsulClient();
        
        LOG.infof("Deregistering module: %s", moduleId);
        
        return Uni.createFrom().completionStage(
            client.deregisterService(moduleId).toCompletionStage()
        )
        .onItem().transformToUni(v -> {
            // Remove from KV store
            String kvKey = MODULE_KV_PREFIX + moduleId;
            return Uni.createFrom().completionStage(
                client.deleteValue(kvKey).toCompletionStage()
            );
        })
        .onItem().transform(v -> true)
        .onFailure().recoverWithItem(false);
    }
    
    /**
     * Enable a module for a specific cluster
     */
    public Uni<Void> enableModuleForCluster(String moduleId, String clusterName) {
        ConsulClient client = getConsulClient();
        String kvKey = String.format("rokkon/clusters/%s/enabled-modules/%s", 
                                    clusterName, moduleId);
        
        return Uni.createFrom().completionStage(
            client.putValue(kvKey, "true").toCompletionStage()
        )
        .onItem().transformToUni(success -> {
            if (success) {
                LOG.infof("Enabled module %s for cluster %s", moduleId, clusterName);
                return Uni.createFrom().voidItem();
            } else {
                return Uni.createFrom().failure(
                    new WebApplicationException("Failed to enable module", 
                                              Response.Status.INTERNAL_SERVER_ERROR)
                );
            }
        });
    }
    
    /**
     * Disable a module for a specific cluster
     */
    public Uni<Void> disableModuleForCluster(String moduleId, String clusterName) {
        ConsulClient client = getConsulClient();
        String kvKey = String.format("rokkon/clusters/%s/enabled-modules/%s", 
                                    clusterName, moduleId);
        
        return Uni.createFrom().completionStage(
            client.deleteValue(kvKey).toCompletionStage()
        )
        .onItem().transform(v -> {
            LOG.infof("Disabled module %s for cluster %s", moduleId, clusterName);
            return null;
        });
    }
    
    /**
     * List modules enabled for a specific cluster as an ordered set
     */
    public Uni<Set<String>> listEnabledModulesForCluster(String clusterName) {
        ConsulClient client = getConsulClient();
        String prefix = String.format("rokkon/clusters/%s/enabled-modules/", clusterName);
        
        return Uni.createFrom().completionStage(
            client.getKeys(prefix).toCompletionStage()
        )
        .onItem().transform(keyList -> {
            if (keyList == null || keyList.isEmpty()) {
                return new LinkedHashSet<>();
            }
            
            // Extract module IDs from keys into ordered set
            Set<String> enabledModules = new LinkedHashSet<>();
            keyList.stream()
                .map(key -> key.substring(prefix.length()))
                .forEach(enabledModules::add);
            return enabledModules;
        });
    }
    
    private Uni<ModuleRegistration> serviceToModuleRegistration(Service service) {
        Map<String, String> meta = service.getMeta();
        
        // Extract engine connection info if present, otherwise default to service host/port
        String engineHost = meta.getOrDefault("engineHost", service.getAddress());
        int enginePort = Integer.parseInt(meta.getOrDefault("enginePort", String.valueOf(service.getPort())));
        
        // Get schema if stored in metadata
        String jsonSchema = meta.get("jsonSchema");
        
        return Uni.createFrom().item(new ModuleRegistration(
            service.getId(),
            meta.getOrDefault("moduleName", service.getName()),
            meta.getOrDefault("implementationId", ""),
            service.getAddress(),
            service.getPort(),
            meta.getOrDefault("serviceType", "PIPELINE"),
            meta.getOrDefault("version", "1.0.0"),
            meta,
            Long.parseLong(meta.getOrDefault("registeredAt", "0")),
            engineHost,
            enginePort,
            jsonSchema
        ));
    }
    
    private Uni<Void> storeModuleMetadata(ModuleRegistration registration) {
        ConsulClient client = getConsulClient();
        String kvKey = MODULE_KV_PREFIX + registration.moduleId();
        
        // Convert registration to JSON (simplified for now)
        String jsonValue = String.format("""
            {
                "moduleId": "%s",
                "moduleName": "%s",
                "implementationId": "%s",
                "host": "%s",
                "port": %d,
                "serviceType": "%s",
                "version": "%s",
                "registeredAt": %d,
                "jsonSchema": %s
            }
            """,
            registration.moduleId(),
            registration.moduleName(),
            registration.implementationId(),
            registration.host(),
            registration.port(),
            registration.serviceType(),
            registration.version(),
            registration.registeredAt(),
            registration.jsonSchema() != null ? "\"" + registration.jsonSchema().replace("\"", "\\\"") + "\"" : "null"
        );
        
        return Uni.createFrom().completionStage(
            client.putValue(kvKey, jsonValue).toCompletionStage()
        )
        .onItem().transformToUni(success -> {
            if (success) {
                LOG.debugf("Stored module metadata in KV: %s", kvKey);
                return Uni.createFrom().voidItem();
            } else {
                return Uni.createFrom().failure(
                    new RuntimeException("Failed to store module metadata")
                );
            }
        });
    }
    
    private String generateModuleId(String moduleName) {
        return moduleName + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private ConsulClient getConsulClient() {
        return connectionManager.getClient().orElseThrow(() -> 
            new WebApplicationException("Consul not connected", 
                                      Response.Status.SERVICE_UNAVAILABLE)
        );
    }
    
    /**
     * Validate that we can connect to the module before registering it
     */
    private Uni<Boolean> validateModuleConnection(String host, int port, String moduleName) {
        return Uni.createFrom().item(() -> {
            LOG.infof("Validating connection to module %s at %s:%d", moduleName, host, port);
            
            // Simple TCP connection test - Consul will handle the actual gRPC health checks
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 5000); // 5 second timeout
                LOG.infof("Successfully connected to module %s at %s:%d", moduleName, host, port);
                return true;
            } catch (Exception e) {
                LOG.errorf("Failed to connect to module %s at %s:%d - %s", 
                          moduleName, host, port, e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Archive a service by moving it from active to archive namespace in Consul
     */
    public Uni<Boolean> archiveService(String serviceName, String reason) {
        ConsulClient client = getConsulClient();
        String timestamp = java.time.Instant.now().toString();
        
        // First, try to get the service from Consul's service registry
        return Uni.createFrom().completionStage(
            client.catalogServiceNodes(serviceName).toCompletionStage()
        )
        .onItem().transformToUni(serviceNodes -> {
            if (serviceNodes == null || serviceNodes.getList() == null || serviceNodes.getList().isEmpty()) {
                LOG.warnf("Service %s not found in Consul registry", serviceName);
                return Uni.createFrom().item(false);
            }
            
            // Get the first service node
            var serviceNode = serviceNodes.getList().get(0);
            
            // Create archive metadata
            String archiveJson;
            try {
                // Use simple JSON format to avoid Jackson dependency issues
                archiveJson = String.format("""
                    {
                        "serviceName": "%s",
                        "serviceId": "%s",
                        "address": "%s",
                        "port": %d,
                        "archivedAt": "%s",
                        "reason": "%s"
                    }
                    """,
                    serviceNode.getName() != null ? serviceNode.getName() : serviceName,
                    serviceNode.getId() != null ? serviceNode.getId() : "",
                    serviceNode.getAddress() != null ? serviceNode.getAddress() : "",
                    serviceNode.getPort(),
                    timestamp,
                    reason
                );
            } catch (Exception e) {
                LOG.warnf("Failed to create archive JSON: %s", e.getMessage());
                return archiveServiceSimple(serviceName, reason, timestamp);
            }
            
            // Store in archive namespace
            String archiveKey = String.format("%s/services/%s-%s", 
                ARCHIVE_PREFIX, serviceName, timestamp.replace(":", "-").replace(".", "-"));
            
            return Uni.createFrom().completionStage(
                client.putValue(archiveKey, archiveJson).toCompletionStage()
            )
            .onItem().transformToUni(success -> {
                if (!success) {
                    return Uni.createFrom().failure(
                        new RuntimeException("Failed to archive service metadata")
                    );
                }
                
                // Now deregister the service using the service ID
                String serviceId = serviceNode.getId() != null ? 
                    serviceNode.getId() : serviceName;
                    
                return Uni.createFrom().completionStage(
                    client.deregisterService(serviceId).toCompletionStage()
                )
                .onItem().transform(v -> {
                    LOG.infof("Successfully archived and deregistered service %s", serviceName);
                    return true;
                });
            });
        })
        .onFailure().recoverWithUni(t -> {
            if (t.getMessage() != null && t.getMessage().contains("com.fasterxml.jackson")) {
                // JSON serialization error - try simpler format
                LOG.warnf("JSON serialization failed, using simple archive format: %s", t.getMessage());
                return archiveServiceSimple(serviceName, reason, timestamp);
            }
            LOG.errorf(t, "Failed to archive service %s", serviceName);
            return Uni.createFrom().item(false);
        });
    }
    
    private Uni<Boolean> archiveServiceSimple(String serviceName, String reason, String timestamp) {
        ConsulClient client = getConsulClient();
        
        // Simple archive format without complex JSON serialization
        String archiveJson = String.format("""
            {
                "serviceName": "%s",
                "archivedAt": "%s",
                "reason": "%s"
            }
            """,
            serviceName,
            timestamp,
            reason
        );
        
        String archiveKey = String.format("%s/services/%s-%s", 
            ARCHIVE_PREFIX, serviceName, timestamp.replace(":", "-").replace(".", "-"));
        
        return Uni.createFrom().completionStage(
            client.putValue(archiveKey, archiveJson).toCompletionStage()
        )
        .onItem().transform(success -> {
            if (success) {
                LOG.infof("Service %s archived (simple format)", serviceName);
            }
            return success;
        });
    }
    
    /**
     * Compare two JSON schemas for semantic equivalence.
     * This handles differences in formatting, property ordering, etc.
     */
    private boolean areJsonSchemasEquivalent(String schema1, String schema2) {
        try {
            // Parse both schemas as JSON
            JsonNode schemaNode1 = objectMapper.readTree(schema1);
            JsonNode schemaNode2 = objectMapper.readTree(schema2);
            
            // Use Jackson's equals which does deep comparison ignoring order
            return schemaNode1.equals(schemaNode2);
        } catch (Exception e) {
            LOG.errorf("Failed to compare schemas: %s", e.getMessage());
            return false;
        }
    }
    
    /**
     * Validate that a schema is a valid JSON Schema v7
     */
    private boolean isValidJsonSchemaV7(String schemaContent) {
        if (schemaContent == null || schemaContent.trim().isEmpty()) {
            return false;
        }
        
        try {
            JsonNode schemaNode = objectMapper.readTree(schemaContent);
            // Attempt to create a JsonSchema - this validates it's proper JSON Schema v7
            JsonSchema schema = schemaFactory.getSchema(schemaNode);
            return true;
        } catch (Exception e) {
            LOG.errorf("Invalid JSON Schema v7: %s", e.getMessage());
            return false;
        }
    }
}