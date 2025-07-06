package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService;
import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import com.rokkon.pipeline.consul.config.ConsulConfigSource;
import com.rokkon.pipeline.events.ModuleRegistrationRequestEvent; // @deprecated - for event-based registration
import com.rokkon.pipeline.events.ModuleRegistrationResponseEvent; // @deprecated - for event-based registration
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheKey;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.Service;
import io.vertx.ext.consul.ServiceOptions;
import io.vertx.ext.consul.CheckOptions;
import io.vertx.ext.consul.CheckStatus;
import io.vertx.ext.consul.ServiceEntry;
import io.vertx.ext.consul.ServiceEntryList;
import io.vertx.ext.consul.ServiceList;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.net.Socket;
import java.net.InetSocketAddress;

import io.quarkus.scheduler.Scheduled;

/**
 * Implementation of GlobalModuleRegistryService that uses Consul for storage.
 * Modules are registered globally and can be referenced by clusters.
 */
@ApplicationScoped
public class GlobalModuleRegistryServiceImpl implements GlobalModuleRegistryService {
    
    private static final Logger LOG = Logger.getLogger(GlobalModuleRegistryServiceImpl.class);
    
    @ConfigProperty(name = "pipeline.consul.kv-prefix", defaultValue = "pipeline")
    String kvPrefix;
    
    @Inject
    ConsulConnectionManager connectionManager;
    
    @Inject
    ObjectMapper objectMapper;
    
    /**
     * @deprecated Part of the event-based registration approach. Use direct calls instead.
     */
    @Deprecated(forRemoval = true, since = "2024-01")
    @Inject
    Event<ModuleRegistrationResponseEvent> moduleRegistrationResponseEvent;
    
    @Inject
    ConsulConfigSource config;
    
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    
    /**
     * Register a module globally in Consul.
     * This creates both a service entry and stores metadata in KV.
     */
    @Override
    @CacheInvalidate(cacheName = "global-modules-list")
    @CacheInvalidate(cacheName = "global-modules-enabled")
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
        
        // Extract container metadata if provided
        final String containerId = metadata != null ? metadata.get("containerId") : null;
        final String containerName = metadata != null ? metadata.get("containerName") : null;
        final String hostname = metadata != null ? metadata.get("hostname") : null;
        
        // Validate JSON Schema v7 if provided
        if (jsonSchema != null && !jsonSchema.trim().isEmpty()) {
            if (!isValidJsonSchemaV7(jsonSchema)) {
                throw new IllegalArgumentException(
                    String.format("Invalid JSON Schema v7 provided for module '%s'", moduleName)
                );
            }
        }
        
        // Allow multiple instances of the same module type to register
        // Each instance gets a unique moduleId but shares the same moduleName
        return listRegisteredModules()
            .onItem().transformToUni(existingModules -> {
                // Check for duplicate container registration
                if (containerId != null) {
                    Optional<ModuleRegistration> duplicateContainer = existingModules.stream()
                        .filter(m -> containerId.equals(m.containerId()))
                        .findFirst();
                    
                    if (duplicateContainer.isPresent()) {
                        ModuleRegistration dup = duplicateContainer.get();
                        LOG.errorf("Container %s is already registered as module '%s'", containerId, dup.moduleName());
                        return Uni.createFrom().failure(new IllegalArgumentException(
                            String.format("Container %s is already registered as module '%s'. " +
                                        "Cannot register the same container under multiple module names.", 
                                        containerId, dup.moduleName())
                        ));
                    }
                }
                
                // Check if there are existing modules with the same name
                // Check for duplicate host/port combination
                boolean duplicateEndpoint = existingModules.stream()
                    .anyMatch(m -> m.host().equals(host) && m.port() == port);
                
                if (duplicateEndpoint) {
                    LOG.warnf("Module already registered at %s:%d", host, port);
                    return Uni.createFrom().failure(new WebApplicationException(
                        String.format("A module is already registered at %s:%d", host, port),
                        Response.Status.CONFLICT
                    ));
                }
                
                List<ModuleRegistration> sameNameModules = existingModules.stream()
                    .filter(m -> m.moduleName().equals(moduleName))
                    .toList();
                
                if (!sameNameModules.isEmpty()) {
                    LOG.infof("Module type '%s' already has %d instance(s) registered. Adding new instance.", 
                             moduleName, sameNameModules.size());
                    
                    // Validate schema consistency across instances
                    for (ModuleRegistration existing : sameNameModules) {
                        if (existing.jsonSchema() != null && jsonSchema != null) {
                            if (!areJsonSchemasEquivalent(existing.jsonSchema(), jsonSchema)) {
                                LOG.errorf("Module %s schema mismatch detected for new instance", moduleName);
                                return Uni.createFrom().failure(new IllegalArgumentException(
                                    String.format("Schema mismatch for module '%s'. All instances must have the same schema.", 
                                                moduleName)
                                ));
                            }
                        }
                    }
                }
                
                // Validate the module is accessible
                return validateModuleConnection(host, port, moduleName);
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
                    jsonSchema,
                    true,  // New modules are enabled by default
                    containerId,
                    containerName,
                    hostname
                );
                
                // Create service options for Consul
                Map<String, String> serviceMeta = new java.util.HashMap<>();
                serviceMeta.put("moduleName", moduleName);
                serviceMeta.put("implementationId", implementationId);
                serviceMeta.put("serviceType", serviceType);
                serviceMeta.put("service-type", "MODULE");  // Add standard service-type for dashboard
                serviceMeta.put("version", version);
                serviceMeta.put("registeredAt", String.valueOf(registration.registeredAt()));
                
                // Add container metadata if available
                if (containerId != null) serviceMeta.put("containerId", containerId);
                if (containerName != null) serviceMeta.put("containerName", containerName);
                if (hostname != null) serviceMeta.put("hostname", hostname);
                
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
                    .setName(moduleName)
                    .setTags(List.of("module", "global", serviceType, "version:" + version))
                    .setAddress(host)
                    .setPort(port)
                    .setMeta(serviceMeta);
                
                // Add gRPC health check with configurable intervals
                CheckOptions checkOptions = new CheckOptions()
                    .setName("Module gRPC Health Check")
                    .setGrpc(host + ":" + port)
                    .setGrpcTls(false)
                    .setInterval(config.consul().health().checkInterval().getSeconds() + "s")
                    .setDeregisterAfter(config.consul().health().deregisterAfter().getSeconds() + "s");
                
                serviceOptions.setCheckOptions(checkOptions);
                
                // Register the service
                LOG.infof("Registering service with Consul: %s (ID: %s)", moduleName, moduleId);
                return Uni.createFrom().completionStage(
                    client.registerService(serviceOptions).toCompletionStage()
                )
                .onItem().transformToUni(v -> {
                    LOG.infof("Service registered with Consul, now storing metadata in KV");
                    // Store additional metadata in KV
                    return storeModuleMetadata(registration);
                })
                .onItem().transform(v -> {
                    LOG.infof("Module registration complete: %s", moduleId);
                    return registration;
                });
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
     * This includes both enabled and disabled modules
     */
    @Override
    @CacheResult(cacheName = "global-modules-list")
    @SuppressWarnings("unchecked")
    public Uni<Set<ModuleRegistration>> listRegisteredModules() {
        ConsulClient client = getConsulClient();
        
        // Try to get all services with module tag directly using health endpoint
        return ((Uni<Set<ModuleRegistration>>) (Uni<?>) Uni.createFrom().<List<Service>>emitter(emitter -> {
            // First, we need to get the list of all services from catalog
            client.catalogServices()
                .onComplete(catalogResult -> {
                    if (catalogResult.failed()) {
                        LOG.warnf("Failed to get catalog services: %s", catalogResult.cause());
                        emitter.complete(new ArrayList<>());
                        return;
                    }
                    
                    ServiceList serviceList = catalogResult.result();
                    if (serviceList == null || serviceList.getList() == null) {
                        LOG.debugf("No services found in catalog");
                        emitter.complete(new ArrayList<>());
                        return;
                    }
                    
                    // Filter for services with "module" tag
                    List<String> moduleServiceNames = serviceList.getList().stream()
                        .filter(service -> service != null && 
                                service.getTags() != null && 
                                service.getTags().contains("module"))
                        .map(service -> service.getName())
                        .distinct()
                        .toList();
                    
                    if (moduleServiceNames.isEmpty()) {
                        LOG.debugf("No services with 'module' tag found");
                        emitter.complete(new ArrayList<>());
                        return;
                    }
                    
                    // Collect all services from health checks
                    List<Service> allServices = new ArrayList<>();
                    java.util.concurrent.atomic.AtomicInteger remaining = 
                        new java.util.concurrent.atomic.AtomicInteger(moduleServiceNames.size());
                    
                    for (String serviceName : moduleServiceNames) {
                        client.healthServiceNodes(serviceName, false)
                            .onComplete(healthResult -> {
                                if (healthResult.succeeded()) {
                                    ServiceEntryList entryList = healthResult.result();
                                    if (entryList != null && entryList.getList() != null) {
                                        entryList.getList().stream()
                                            .map(ServiceEntry::getService)
                                            .filter(service -> service != null)
                                            .forEach(allServices::add);
                                    }
                                } else {
                                    LOG.debugf("Failed to get health for service %s: %s", 
                                        serviceName, healthResult.cause());
                                }
                                
                                if (remaining.decrementAndGet() == 0) {
                                    // All requests completed
                                    emitter.complete(allServices);
                                }
                            });
                    }
                });
        })
        .onItem().transformToUni(services -> {
            if (services.isEmpty()) {
                return Uni.createFrom().item(new LinkedHashSet<>());
            }
            
            // Convert to ModuleRegistration objects
            List<Uni<ModuleRegistration>> registrationUnis = services.stream()
                .filter(service -> service.getTags() != null && service.getTags().contains("module"))
                .map(this::serviceToModuleRegistration)
                .toList();
            
            if (registrationUnis.isEmpty()) {
                return Uni.createFrom().item(new LinkedHashSet<>());
            }
            
            return Uni.combine().all().unis(registrationUnis)
                .with(list -> {
                    // Use LinkedHashSet to maintain order and prevent duplicates
                    Set<ModuleRegistration> registrations = new LinkedHashSet<>();
                    list.forEach(obj -> {
                        if (obj instanceof ModuleRegistration) {
                            registrations.add((ModuleRegistration) obj);
                        }
                    });
                    return registrations;
                });
        })
        .onFailure().recoverWithUni(throwable -> {
            LOG.errorf(throwable, "Failed to list registered modules");
            Set<ModuleRegistration> empty = new LinkedHashSet<>();
            return (Uni<Set<ModuleRegistration>>) (Uni<?>) Uni.createFrom().item(empty);
        }));
    }
    
    /**
     * List only enabled modules
     */
    @Override
    @CacheResult(cacheName = "global-modules-enabled")
    public Uni<Set<ModuleRegistration>> listEnabledModules() {
        return listRegisteredModules()
            .onItem().transform(modules -> {
                Set<ModuleRegistration> enabledModules = new LinkedHashSet<>();
                for (ModuleRegistration module : modules) {
                    if (module.enabled()) {
                        enabledModules.add(module);
                    }
                }
                return enabledModules;
            });
    }
    
    /**
     * Get a specific module by ID
     */
    @Override
    @CacheResult(cacheName = "global-modules")
    public Uni<ModuleRegistration> getModule(@CacheKey String moduleId) {
        ConsulClient client = getConsulClient();
        
        // First try to find the service by listing all modules
        return listRegisteredModules()
            .onItem().transform(modules -> {
                return modules.stream()
                    .filter(module -> module.moduleId().equals(moduleId))
                    .findFirst()
                    .orElse(null);
            });
    }
    
    /**
     * Disable a module (sets enabled=false)
     */
    @Override
    @CacheInvalidate(cacheName = "global-modules-list")
    @CacheInvalidate(cacheName = "global-modules-enabled")
    @CacheInvalidate(cacheName = "global-modules")
    @CacheInvalidate(cacheName = "cluster-modules-enabled")
    public Uni<Boolean> disableModule(@CacheKey String moduleId) {
        LOG.infof("Disabling module: %s", moduleId);
        
        // First get the current module registration
        return getModule(moduleId)
            .onItem().transformToUni(module -> {
                if (module == null) {
                    LOG.warnf("Module %s not found for disabling", moduleId);
                    return Uni.createFrom().item(false);
                }
                
                // Create a new registration with enabled=false
                ModuleRegistration disabledModule = new ModuleRegistration(
                    module.moduleId(),
                    module.moduleName(),
                    module.implementationId(),
                    module.host(),
                    module.port(),
                    module.serviceType(),
                    module.version(),
                    module.metadata(),
                    module.registeredAt(),
                    module.engineHost(),
                    module.enginePort(),
                    module.jsonSchema(),
                    false,  // Set to disabled
                    module.containerId(),
                    module.containerName(),
                    module.hostname()
                );
                
                // Update the KV store with the disabled state
                return storeModuleMetadata(disabledModule)
                    .onItem().transform(v -> {
                        LOG.infof("Module %s has been disabled", moduleId);
                        return true;
                    });
            })
            .onFailure().recoverWithItem(t -> {
                LOG.errorf(t, "Failed to disable module %s", moduleId);
                return false;
            });
    }
    
    /**
     * Enable a module (sets enabled=true)
     */
    @Override
    @CacheInvalidate(cacheName = "global-modules-list")
    @CacheInvalidate(cacheName = "global-modules-enabled")
    @CacheInvalidate(cacheName = "global-modules")
    @CacheInvalidate(cacheName = "cluster-modules-enabled")
    public Uni<Boolean> enableModule(@CacheKey String moduleId) {
        LOG.infof("Enabling module: %s", moduleId);
        
        // First get the current module registration
        return getModule(moduleId)
            .onItem().transformToUni(module -> {
                if (module == null) {
                    LOG.warnf("Module %s not found for enabling", moduleId);
                    return Uni.createFrom().item(false);
                }
                
                // Create a new registration with enabled=true
                ModuleRegistration enabledModule = new ModuleRegistration(
                    module.moduleId(),
                    module.moduleName(),
                    module.implementationId(),
                    module.host(),
                    module.port(),
                    module.serviceType(),
                    module.version(),
                    module.metadata(),
                    module.registeredAt(),
                    module.engineHost(),
                    module.enginePort(),
                    module.jsonSchema(),
                    true,  // Set to enabled
                    module.containerId(),
                    module.containerName(),
                    module.hostname()
                );
                
                // Update the KV store with the enabled state
                return storeModuleMetadata(enabledModule)
                    .onItem().transform(v -> {
                        LOG.infof("Module %s has been enabled", moduleId);
                        return true;
                    });
            })
            .onFailure().recoverWithItem(t -> {
                LOG.errorf(t, "Failed to enable module %s", moduleId);
                return false;
            });
    }
    
    /**
     * Deregister a module (hard delete - removes from registry completely)
     */
    @Override
    @CacheInvalidate(cacheName = "global-modules-list")
    @CacheInvalidate(cacheName = "global-modules-enabled")
    @CacheInvalidate(cacheName = "global-modules")
    @CacheInvalidate(cacheName = "module-health-status")
    @CacheInvalidate(cacheName = "cluster-modules-enabled")
    public Uni<Boolean> deregisterModule(@CacheKey String moduleId) {
        ConsulClient client = getConsulClient();
        
        LOG.infof("Deregistering module (hard delete): %s", moduleId);
        
        // First deregister from Consul service registry
        return Uni.createFrom().completionStage(
            client.deregisterService(moduleId).toCompletionStage()
        )
        .onItem().transformToUni(v -> {
            // Then remove from KV store
            String kvKey = kvPrefix + "/modules/registered/" + moduleId;
            return Uni.createFrom().completionStage(
                client.deleteValue(kvKey).toCompletionStage()
            );
        })
        .onItem().transform(v -> {
            LOG.infof("Module %s has been completely deregistered", moduleId);
            return true;
        })
        .onFailure().recoverWithItem(t -> {
            LOG.errorf(t, "Failed to deregister module %s", moduleId);
            return false;
        });
    }
    
    /**
     * Enable a module for a specific cluster
     */
    @Override
    @CacheInvalidate(cacheName = "cluster-modules-enabled")
    public Uni<Void> enableModuleForCluster(@CacheKey String moduleId, @CacheKey String clusterName) {
        ConsulClient client = getConsulClient();
        String kvKey = String.format("%s/clusters/%s/enabled-modules/%s", 
                                    kvPrefix, clusterName, moduleId);
        
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
    @Override
    @CacheInvalidate(cacheName = "cluster-modules-enabled")
    public Uni<Void> disableModuleForCluster(@CacheKey String moduleId, @CacheKey String clusterName) {
        ConsulClient client = getConsulClient();
        String kvKey = String.format("%s/clusters/%s/enabled-modules/%s", 
                                    kvPrefix, clusterName, moduleId);
        
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
    @Override
    @CacheResult(cacheName = "cluster-modules-enabled")
    public Uni<Set<String>> listEnabledModulesForCluster(@CacheKey String clusterName) {
        ConsulClient client = getConsulClient();
        String prefix = String.format("%s/clusters/%s/enabled-modules/", kvPrefix, clusterName);
        
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
        ConsulClient client = getConsulClient();
        String moduleId = service.getId();
        
        // First, try to get the enabled state from KV store
        String kvKey = kvPrefix + "/modules/registered/" + moduleId;
        
        return Uni.createFrom().completionStage(
            client.getValue(kvKey).toCompletionStage()
        )
        .onItem().transform(kvValue -> {
            boolean enabled = true; // Default to enabled if not found
            
            if (kvValue != null && kvValue.getValue() != null) {
                try {
                    // Parse the JSON to get the enabled field
                    String json = kvValue.getValue();
                    if (json.contains("\"enabled\"")) {
                        enabled = json.contains("\"enabled\": true") || json.contains("\"enabled\":true");
                    }
                } catch (Exception e) {
                    LOG.warnf("Failed to parse enabled state for module %s, defaulting to true", moduleId);
                }
            }
            
            // Extract engine connection info if present, otherwise default to service host/port
            String engineHost = meta.getOrDefault("engineHost", service.getAddress());
            int enginePort = Integer.parseInt(meta.getOrDefault("enginePort", String.valueOf(service.getPort())));
            
            // Get schema if stored in metadata
            String jsonSchema = meta.get("jsonSchema");
            
            // Get container metadata if available
            String containerId = meta.get("containerId");
            String containerName = meta.get("containerName");
            String hostname = meta.get("hostname");
            
            return new ModuleRegistration(
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
                jsonSchema,
                enabled,
                containerId,
                containerName,
                hostname
            );
        });
    }
    
    private Uni<Void> storeModuleMetadata(ModuleRegistration registration) {
        ConsulClient client = getConsulClient();
        String kvKey = kvPrefix + "/modules/registered/" + registration.moduleId();
        
        LOG.infof("Storing module metadata to KV - prefix: %s, key: %s", kvPrefix, kvKey);
        
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
                "jsonSchema": %s,
                "enabled": %b,
                "containerId": %s,
                "containerName": %s,
                "hostname": %s
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
            registration.jsonSchema() != null ? "\"" + registration.jsonSchema().replace("\"", "\\\"") + "\"" : "null",
            registration.enabled(),
            registration.containerId() != null ? "\"" + registration.containerId() + "\"" : "null",
            registration.containerName() != null ? "\"" + registration.containerName() + "\"" : "null",
            registration.hostname() != null ? "\"" + registration.hostname() + "\"" : "null"
        );
        
        return Uni.createFrom().completionStage(
            client.putValue(kvKey, jsonValue).toCompletionStage()
        )
        .onItem().transformToUni(success -> {
            if (success) {
                LOG.infof("Successfully stored module metadata in KV: %s (success=%s)", kvKey, success);
                return Uni.createFrom().voidItem();
            } else {
                LOG.errorf("Failed to store module metadata in KV: %s (success=%s)", kvKey, success);
                return Uni.createFrom().failure(
                    new RuntimeException("Failed to store module metadata")
                );
            }
        })
        .onFailure().transform(t -> {
            LOG.errorf(t, "Exception storing module metadata in KV: %s", kvKey);
            return new RuntimeException("Failed to store module metadata", t);
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
                // Use configured timeout for module connections
                int timeoutMillis = (int) config.modules().connectionTimeout().toMillis();
                socket.connect(new InetSocketAddress(host, port), timeoutMillis);
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
    @Override
    @CacheInvalidate(cacheName = "global-modules-list")
    @CacheInvalidate(cacheName = "global-modules-enabled")
    @CacheInvalidate(cacheName = "global-modules")
    @CacheInvalidate(cacheName = "module-health-status")
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
                kvPrefix + "/archive", serviceName, timestamp.replace(":", "-").replace(".", "-"));
            
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
            kvPrefix + "/archive", serviceName, timestamp.replace(":", "-").replace(".", "-"));
        
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
    
    /**
     * Clean up zombie instances - modules that are failing health checks or no longer exist
     * This method handles ALL types of zombies and dirty Consul state:
     * 1. Type 1: Classic zombies (in KV + catalog but unhealthy)
     * 2. Type 2: Stale service entries (in catalog but not KV)
     * 3. Type 3: Partial registrations (in KV but not catalog)
     * 4. Type 4: Name mismatches (registered with wrong name like "echo-module" instead of "echo")
     * 5. Type 5: Any registration that doesn't match expected patterns or has no real backing
     */
    @Override
    public Uni<ZombieCleanupResult> cleanupZombieInstances() {
        LOG.info("Starting comprehensive zombie instance cleanup");
        ConsulClient client = getConsulClient();
        
        // First, get all registered modules from KV store
        return listRegisteredModules()
            .onItem().transformToUni(registeredModules -> {
                // Also get all services from catalog that look like modules
                return Uni.createFrom().<List<Service>>emitter(emitter -> {
                    client.catalogServices()
                        .onComplete(catalogResult -> {
                            if (catalogResult.failed()) {
                                LOG.warnf("Failed to get catalog services: %s", catalogResult.cause());
                                emitter.complete(new ArrayList<>());
                                return;
                            }
                            
                            ServiceList serviceList = catalogResult.result();
                            if (serviceList == null || serviceList.getList() == null) {
                                emitter.complete(new ArrayList<>());
                                return;
                            }
                            
                            // Filter for services with "module" tag
                            List<Service> moduleServices = serviceList.getList().stream()
                                .filter(service -> service != null && 
                                        service.getTags() != null && 
                                        service.getTags().contains("module"))
                                .toList();
                                
                            emitter.complete(moduleServices);
                        });
                })
                .onItem().transformToUni(catalogModuleServices -> {
                    // Now check for all three types of zombies
                    List<Uni<ServiceHealthStatus>> healthCheckUnis = new ArrayList<>();
                    Set<String> registeredModuleIds = registeredModules.stream()
                        .map(ModuleRegistration::moduleId)
                        .collect(Collectors.toSet());
                    
                    // Type 1 & 3: Check health of all registered modules
                    for (ModuleRegistration module : registeredModules) {
                        healthCheckUnis.add(checkModuleHealth(client, module));
                    }
                    
                    // Type 2: Check for stale service entries (in catalog but not in KV)
                    List<Uni<ServiceHealthStatus>> staleServiceUnis = new ArrayList<>();
                    for (Service catalogService : catalogModuleServices) {
                        String serviceName = catalogService.getName();
                        
                        // Get all instances of this service
                        Uni<ServiceHealthStatus> staleCheckUni = Uni.createFrom().completionStage(
                            client.healthServiceNodes(serviceName, false).toCompletionStage()
                        )
                        .onItem().transformToUni(serviceEntryList -> {
                            if (serviceEntryList == null || serviceEntryList.getList() == null) {
                                return Uni.createFrom().item((ServiceHealthStatus) null);
                            }
                            
                            List<Uni<ServiceHealthStatus>> instanceUnis = new ArrayList<>();
                            for (ServiceEntry entry : serviceEntryList.getList()) {
                                Service svc = entry.getService();
                                if (svc != null && !registeredModuleIds.contains(svc.getId())) {
                                    // This service instance is NOT in our KV store - it's a stale entry!
                                    LOG.infof("Found stale service entry: %s (ID: %s) - not in KV store", 
                                             serviceName, svc.getId());
                                    
                                    // Create a dummy ModuleRegistration for cleanup
                                    ModuleRegistration staleModule = new ModuleRegistration(
                                        svc.getId(),
                                        serviceName,
                                        "",
                                        svc.getAddress(),
                                        svc.getPort(),
                                        "UNKNOWN",
                                        "unknown",
                                        svc.getMeta() != null ? svc.getMeta() : Map.of(),
                                        0,
                                        svc.getAddress(),
                                        svc.getPort(),
                                        null,
                                        false,
                                        null,
                                        null,
                                        null
                                    );
                                    
                                    // Mark as zombie (stale type 2)
                                    instanceUnis.add(Uni.createFrom().item(
                                        new ServiceHealthStatus(staleModule, HealthStatus.CRITICAL, false)
                                    ));
                                }
                            }
                            
                            if (instanceUnis.isEmpty()) {
                                return Uni.createFrom().item((ServiceHealthStatus) null);
                            }
                            
                            return Uni.combine().all().unis(instanceUnis)
                                .with(list -> {
                                    @SuppressWarnings("unchecked")
                                    List<ServiceHealthStatus> statuses = (List<ServiceHealthStatus>) list;
                                    return statuses.isEmpty() ? null : statuses.get(0);
                                });
                        });
                        
                        staleServiceUnis.add(staleCheckUni);
                    }
                    
                    // Combine all health checks
                    List<Uni<ServiceHealthStatus>> allChecks = new ArrayList<>();
                    allChecks.addAll(healthCheckUnis);
                    allChecks.addAll(staleServiceUnis);
                    
                    if (allChecks.isEmpty()) {
                        return Uni.createFrom().item(new ZombieCleanupResult(0, 0, List.of()));
                    }
                    
                    return Uni.combine().all().unis(allChecks)
                        .with(results -> {
                            @SuppressWarnings("unchecked")
                            List<ServiceHealthStatus> healthStatuses = ((List<ServiceHealthStatus>) results).stream()
                                .filter(Objects::nonNull)
                                .toList();
                            
                            // Identify all types of zombies
                            List<ServiceHealthStatus> zombies = new ArrayList<>();
                            
                            // Add Type 1, 2, 3 zombies (unhealthy, stale, partial)
                            zombies.addAll(healthStatuses.stream()
                                .filter(status -> status.isZombie())
                                .toList());
                            
                            // Type 4: Check for modules in KV with no corresponding service instances
                            // This catches registrations that exist in KV but have no service catalog entries
                            Set<String> servicesWithInstances = new HashSet<>();
                            for (ServiceHealthStatus status : healthStatuses) {
                                if (status.exists()) {
                                    servicesWithInstances.add(status.module().moduleName());
                                }
                            }
                            
                            // Find modules that are registered but have NO service instances at all
                            for (ModuleRegistration regModule : registeredModules) {
                                if (!servicesWithInstances.contains(regModule.moduleName())) {
                                    // This module is in KV but has no service instances - it's a zombie
                                    boolean alreadyMarked = zombies.stream()
                                        .anyMatch(z -> z.module().moduleId().equals(regModule.moduleId()));
                                    
                                    if (!alreadyMarked) {
                                        LOG.infof("Found Type 4 zombie (KV entry with no service instances): %s (%s)", 
                                                 regModule.moduleId(), regModule.moduleName());
                                        zombies.add(new ServiceHealthStatus(regModule, HealthStatus.CRITICAL, false));
                                    }
                                }
                            }
                            
                            LOG.infof("Zombie detection complete: %d total zombies found", zombies.size());
                            
                            // Log zombie types for debugging
                            for (ServiceHealthStatus zombie : zombies) {
                                ModuleRegistration module = zombie.module();
                                if (!zombie.exists()) {
                                    // Could be Type 2 (stale catalog) or Type 4 (KV only)
                                    boolean hasKvEntry = registeredModules.stream()
                                        .anyMatch(r -> r.moduleId().equals(module.moduleId()));
                                    if (hasKvEntry) {
                                        LOG.infof("  Type 4 zombie (KV entry with no service instances): %s", module.moduleId());
                                    } else {
                                        LOG.infof("  Type 2 zombie (stale catalog entry): %s", module.moduleId());
                                    }
                                } else if (zombie.healthStatus() == HealthStatus.CRITICAL) {
                                    LOG.infof("  Type 1 zombie (unhealthy): %s", module.moduleId());
                                }
                            }
                            
                            return zombies;
                        })
                        .onItem().transformToUni(zombies -> {
                            int zombiesDetected = zombies.size();
                            
                            if (zombiesDetected == 0) {
                                LOG.info("No zombies detected");
                                return Uni.createFrom().item(new ZombieCleanupResult(0, 0, List.of()));
                            }
                            
                            // Clean up all zombies
                            List<Uni<Boolean>> cleanupUnis = zombies.stream()
                                .map(zombie -> {
                                    String moduleId = zombie.module().moduleId();
                                    LOG.infof("Cleaning up zombie: %s", moduleId);
                                    
                                    // Use the full deregisterModule method which handles both service catalog and KV
                                    // This ensures we clean up everything regardless of zombie type
                                    return deregisterModule(moduleId)
                                        .onItem().transform(success -> {
                                            if (success) {
                                                LOG.infof("Successfully cleaned up zombie: %s", moduleId);
                                            } else {
                                                LOG.warnf("Partial cleanup of zombie: %s", moduleId);
                                            }
                                            return success;
                                        });
                                })
                                .toList();
                            
                            return Uni.combine().all().unis(cleanupUnis)
                                .with(cleanupResults -> {
                                    @SuppressWarnings("unchecked")
                                    List<Boolean> cleanResults = (List<Boolean>) cleanupResults;
                                    
                                    int zombiesCleaned = (int) cleanResults.stream()
                                        .filter(success -> success)
                                        .count();
                                    
                                    List<String> errors = new ArrayList<>();
                                    for (int i = 0; i < cleanResults.size(); i++) {
                                        if (!cleanResults.get(i)) {
                                            ModuleRegistration zombie = zombies.get(i).module();
                                            errors.add(String.format("Failed to cleanup %s (%s)", 
                                                                    zombie.moduleId(), zombie.moduleName()));
                                        }
                                    }
                                    
                                    LOG.infof("Zombie cleanup completed: %d detected, %d cleaned, %d errors", 
                                             zombiesDetected, zombiesCleaned, errors.size());
                                    
                                    return new ZombieCleanupResult(zombiesDetected, zombiesCleaned, errors);
                                });
                        });
                });
            })
            .onFailure().recoverWithItem(t -> {
                LOG.errorf(t, "Failed to cleanup zombie instances");
                return new ZombieCleanupResult(0, 0, List.of("Cleanup failed: " + t.getMessage()));
            });
    }
    
    /**
     * Public method to check module health using Consul health checks
     * @param moduleId The module ID to check
     * @return Health status of the module
     */
    @Override
    @CacheResult(cacheName = "module-health-status")
    public Uni<ServiceHealthStatus> getModuleHealthStatus(@CacheKey String moduleId) {
        return getModule(moduleId)
            .onItem().transformToUni(module -> {
                if (module == null) {
                    return Uni.createFrom().failure(new RuntimeException("Module not found: " + moduleId));
                }
                ConsulClient client = getConsulClient();
                return checkModuleHealth(client, module);
            });
    }
    
    /**
     * Check module health using Consul health checks
     */
    private Uni<ServiceHealthStatus> checkModuleHealth(ConsulClient client, ModuleRegistration module) {
        String serviceName = module.moduleName();
        
        return Uni.createFrom().completionStage(
            client.healthServiceNodes(serviceName, false).toCompletionStage()
        )
        .onItem().transform(serviceEntryList -> {
            if (serviceEntryList == null || serviceEntryList.getList() == null || serviceEntryList.getList().isEmpty()) {
                // Service not found in health checks
                LOG.debugf("Module %s not found in Consul health checks", module.moduleId());
                return new ServiceHealthStatus(module, HealthStatus.CRITICAL, false);
            }
            
            // Find the specific instance by ID
            var instanceHealth = serviceEntryList.getList().stream()
                .filter(entry -> module.moduleId().equals(entry.getService().getId()))
                .findFirst();
            
            if (instanceHealth.isEmpty()) {
                // Specific instance not found
                LOG.debugf("Module instance %s not found in health checks", module.moduleId());
                return new ServiceHealthStatus(module, HealthStatus.CRITICAL, false);
            }
            
            // Get the worst health check status for this instance
            HealthStatus worstStatus = HealthStatus.PASSING;
            var checks = instanceHealth.get().getChecks();
            if (checks != null) {
                for (var check : checks) {
                    CheckStatus status = check.getStatus();
                    if (status == CheckStatus.CRITICAL) {
                        worstStatus = HealthStatus.CRITICAL;
                        break;
                    } else if (status == CheckStatus.WARNING && worstStatus == HealthStatus.PASSING) {
                        worstStatus = HealthStatus.WARNING;
                    }
                }
            }
            
            if (worstStatus == HealthStatus.CRITICAL) {
                LOG.debugf("Module %s has critical health status", module.moduleId());
            }
            
            return new ServiceHealthStatus(module, worstStatus, true);
        })
        .onFailure().recoverWithItem(t -> {
            LOG.warnf("Failed to check health for module %s: %s", module.moduleId(), t.getMessage());
            // On failure, assume module exists but status unknown
            return new ServiceHealthStatus(module, HealthStatus.WARNING, true);
        });
    }
    
    /**
     * Clean up stale entries in the whitelist (modules registered but not in Consul)
     */
    @Override
    public Uni<Integer> cleanupStaleWhitelistedModules() {
        LOG.info("Starting stale whitelist cleanup");
        ConsulClient client = getConsulClient();
        
        return listRegisteredModules()
            .onItem().transformToUni(registeredModules -> {
                if (registeredModules.isEmpty()) {
                    LOG.info("No registered modules to check for staleness");
                    return Uni.createFrom().item(0);
                }
                
                // Get all services from Consul
                return Uni.createFrom().completionStage(
                    client.localServices().toCompletionStage()
                )
                .onItem().transformToUni(consulServices -> {
                    // Create a set of service IDs that exist in Consul
                    Set<String> consulServiceIds = consulServices.stream()
                        .map(Service::getId)
                        .collect(java.util.stream.Collectors.toSet());
                    
                    // Find registered modules that don't exist in Consul
                    List<ModuleRegistration> staleModules = registeredModules.stream()
                        .filter(module -> !consulServiceIds.contains(module.moduleId()))
                        .toList();
                    
                    if (staleModules.isEmpty()) {
                        LOG.info("No stale whitelist entries found");
                        return Uni.createFrom().item(0);
                    }
                    
                    LOG.infof("Found %d stale whitelist entries", staleModules.size());
                    
                    // Clean up stale entries from KV store
                    List<Uni<Boolean>> cleanupUnis = staleModules.stream()
                        .map(module -> {
                            LOG.infof("Removing stale whitelist entry: %s (%s)", 
                                     module.moduleId(), module.moduleName());
                            String kvKey = kvPrefix + "/modules/registered/" + module.moduleId();
                            return Uni.createFrom().completionStage(
                                client.deleteValue(kvKey).toCompletionStage()
                            )
                            .onItem().transform(v -> {
                                LOG.debugf("Removed stale KV entry: %s", kvKey);
                                return true;
                            })
                            .onFailure().recoverWithItem(t -> {
                                LOG.errorf(t, "Failed to remove stale KV entry: %s", kvKey);
                                return false;
                            });
                        })
                        .toList();
                    
                    return Uni.combine().all().unis(cleanupUnis)
                        .with(results -> {
                            @SuppressWarnings("unchecked")
                            List<Boolean> cleanResults = (List<Boolean>) results;
                            int cleaned = (int) cleanResults.stream()
                                .filter(success -> success)
                                .count();
                            
                            LOG.infof("Stale whitelist cleanup completed: %d entries cleaned", cleaned);
                            return cleaned;
                        });
                });
            })
            .onFailure().recoverWithItem(t -> {
                LOG.errorf(t, "Failed to cleanup stale whitelist entries");
                return 0;
            });
    }
    
    /**
     * Scheduled cleanup task that runs periodically.
     * Timing is controlled by configuration properties that can be updated at runtime.
     */
    @Scheduled(every = "{pipeline.consul.cleanup.interval:30m}", 
               delay = 1, 
               delayUnit = java.util.concurrent.TimeUnit.MINUTES)
    void scheduledCleanup() {
        if (!config.consul().cleanup().enabled()) {
            LOG.debug("Scheduled cleanup is disabled via configuration");
            return;
        }
        
        if (connectionManager.getClient().isEmpty()) {
            LOG.debug("Skipping scheduled cleanup - Consul not connected");
            return;
        }
        
        LOG.debug("Running scheduled cleanup tasks");
        
        // Run zombie cleanup
        cleanupZombieInstances()
            .subscribe().with(
                result -> {
                    if (result.zombiesDetected() > 0) {
                        LOG.infof("Scheduled zombie cleanup: detected=%d, cleaned=%d", 
                                 result.zombiesDetected(), result.zombiesCleaned());
                    }
                },
                failure -> LOG.errorf(failure, "Scheduled zombie cleanup failed")
            );
        
        // Run stale whitelist cleanup
        cleanupStaleWhitelistedModules()
            .subscribe().with(
                cleaned -> {
                    if (cleaned > 0) {
                        LOG.infof("Scheduled whitelist cleanup: cleaned %d stale entries", cleaned);
                    }
                },
                failure -> LOG.errorf(failure, "Scheduled whitelist cleanup failed")
            );
    }
    
    /**
     * Observe module registration request events from gRPC service and process them
     * @deprecated Use direct calls to registerModule() instead of events. 
     *             The newer ModuleRegistrationServiceImpl in engine/consul directly injects 
     *             and calls GlobalModuleRegistryService methods.
     */
    @Deprecated(forRemoval = true, since = "2024-01")
    public void onModuleRegistrationRequest(@Observes ModuleRegistrationRequestEvent event) {
        LOG.warnf("DEPRECATED: Received module registration request via event: %s. Please use direct calls to GlobalModuleRegistryService instead.", event.moduleName());
        
        // Process the registration request
        registerModule(
            event.moduleName(),
            event.implementationId(),
            event.host(),
            event.port(),
            event.serviceType(),
            event.version(),
            event.metadata(),
            event.engineHost(),
            event.enginePort(),
            event.jsonSchema()
        )
        .subscribe().with(
            registration -> {
                LOG.infof("Module registration successful: %s -> %s", 
                         registration.moduleName(), registration.moduleId());
                // Fire success event
                moduleRegistrationResponseEvent.fire(
                    ModuleRegistrationResponseEvent.success(
                        event.requestId(),
                        registration.moduleId(),
                        registration.moduleName(),
                        "Module registered successfully"
                    )
                );
            },
            failure -> {
                LOG.errorf(failure, "Module registration failed for: %s", event.moduleName());
                // Fire failure event
                moduleRegistrationResponseEvent.fire(
                    ModuleRegistrationResponseEvent.failure(
                        event.requestId(),
                        failure.getMessage()
                    )
                );
            }
        );
    }
}